package org.athar.app.review

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReviewRecognitionChunk(
    val featureFrames: Int,
    val modelFrames: Int,
    val tokens: List<ReviewCtcToken>,
    val processedLens: Long,
    val bufferedFeatureFrames: Int,
) {
    fun validated(): ReviewRecognitionChunk {
        require(featureFrames >= 0)
        require(modelFrames >= 0)
        require(processedLens >= 0)
        require(bufferedFeatureFrames in 0..60)
        require(tokens.size <= modelFrames + 1)
        return this
    }

    companion object {
        fun empty(
            processedLens: Long = 0,
            bufferedFeatureFrames: Int = 0,
        ) = ReviewRecognitionChunk(
            featureFrames = 0,
            modelFrames = 0,
            tokens = emptyList(),
            processedLens = processedLens,
            bufferedFeatureFrames = bufferedFeatureFrames,
        )
    }
}

class ReviewFeatureWindowBuffer(
    private val timeFrames: Int,
    private val hopFrames: Int,
    private val featureDim: Int,
) {
    private val frames = ArrayDeque<FloatArray>()

    init {
        require(timeFrames > 0)
        require(hopFrames in 1..timeFrames)
        require(featureDim > 0)
    }

    val bufferedFrames: Int get() = frames.size
    val hasWindow: Boolean get() = frames.size >= timeFrames

    fun add(frame: FloatArray) {
        require(frame.size == featureDim)
        require(frames.size < timeFrames + hopFrames) {
            "feature_window_buffer_overflow"
        }
        frames.addLast(frame)
    }

    fun takeWindow(): FloatArray {
        require(hasWindow)
        val output = FloatArray(timeFrames * featureDim)
        var index = 0
        val iterator = frames.iterator()
        repeat(timeFrames) {
            iterator.next().copyInto(
                output,
                destinationOffset = index,
            )
            index += featureDim
        }
        repeat(hopFrames) { frames.removeFirst() }
        return output
    }

    fun reset() = frames.clear()
}

/**
 * Local streaming recognizer frontend for Task 09.3:
 * 16 kHz PCM -> Kaldi fbank -> stateful Zipformer -> greedy CTC token IDs.
 *
 * It deliberately stops before Quran corpus search/alignment. Task 09.4 owns that layer.
 */
class ReviewStreamingRecognizer internal constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    val io: ReviewModelIoSpec,
) : Closeable {
    private val mutex = Mutex()
    private val fbank = ReviewKaldiFbank()
    private val decoder = ReviewGreedyCtcDecoder(io.vocabSize, BLANK_ID)
    private val windows = ReviewFeatureWindowBuffer(
        io.timeFrames,
        io.hopFrames,
        io.featureDim,
    )
    private val stateSpecs = io.inputs.filterNot { it.name == "x" }
    private val states = LinkedHashMap<String, OnnxTensor>()
    private var processedLens = 0L
    private var closed = false

    init {
        require(session.inputNames == io.inputs.map { it.name }.toSet())
        require(session.outputNames == io.outputs.map { it.name }.toSet())
        stateSpecs.forEach { spec ->
            states[spec.name] = ReviewNativeTensorFactory.zero(
                environment,
                spec,
            )
        }
    }

    suspend fun accept(samples: FloatArray): ReviewRecognitionChunk =
        mutex.withLock {
            ensureOpen()
            withContext(Dispatchers.Default) {
                runFeatureFrames(fbank.acceptWaveform(samples))
            }
        }

    suspend fun finish(): ReviewRecognitionChunk = mutex.withLock {
        ensureOpen()
        withContext(Dispatchers.Default) {
            val totals = MutableRecognitionTotals(processedLens)

            val tail = FloatArray(TAIL_SAMPLES)
            totals.add(runFeatureFrames(fbank.acceptWaveform(tail)))
            totals.add(runFeatureFrames(fbank.inputFinished()))

            val flushed = decoder.flush()
            if (flushed.isNotEmpty()) {
                totals.tokens += flushed
            }
            totals.toChunk(
                processedLens = processedLens,
                buffered = windows.bufferedFrames,
            )
        }
    }

    suspend fun reset() = mutex.withLock {
        ensureOpen()
        withContext(Dispatchers.Default) {
            fbank.reset()
            decoder.reset()
            windows.reset()
            states.values.forEach { runCatching { it.close() } }
            states.clear()
            stateSpecs.forEach { spec ->
                states[spec.name] = ReviewNativeTensorFactory.zero(
                    environment,
                    spec,
                )
            }
            processedLens = 0L
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        states.values.forEach { runCatching { it.close() } }
        states.clear()
        runCatching { session.close() }
    }

    private fun runFeatureFrames(
        features: List<FloatArray>,
    ): ReviewRecognitionChunk {
        if (features.isEmpty()) {
            return ReviewRecognitionChunk.empty(
                processedLens = processedLens,
                bufferedFeatureFrames = windows.bufferedFrames,
            )
        }

        val totals = MutableRecognitionTotals(processedLens)
        for (feature in features) {
            windows.add(feature)
            if (windows.hasWindow) {
                totals.add(runWindow(windows.takeWindow()))
            }
        }
        totals.featureFrames += features.size
        return totals.toChunk(
            processedLens = processedLens,
            buffered = windows.bufferedFrames,
        )
    }

    private fun runWindow(window: FloatArray): ReviewRecognitionChunk {
        require(window.size == io.timeFrames * io.featureDim)
        val xSpec = io.inputs.single { it.name == "x" }
        val x = ReviewNativeTensorFactory.float(
            environment,
            xSpec.dims,
            window,
        )
        val feeds = LinkedHashMap<String, OnnxTensor>()
        feeds["x"] = x
        stateSpecs.forEach { spec ->
            feeds[spec.name] = requireNotNull(states[spec.name])
        }

        val replacements = LinkedHashMap<String, OnnxTensor>()
        try {
            session.run(feeds).use { result ->
                val logValue = result.get("log_probs")
                    .orElseThrow { IllegalStateException("missing_log_probs") }
                val logTensor = logValue as? OnnxTensor
                    ?: error("log_probs_not_tensor")
                val logInfo = logTensor.info as? TensorInfo
                    ?: error("log_probs_missing_tensor_info")
                require(
                    logInfo.shape.contentEquals(
                        longArrayOf(1, 12, io.vocabSize.toLong()),
                    ),
                )
                val modelFrames = logInfo.shape[1].toInt()
                val logProbabilities = FloatArray(
                    modelFrames * io.vocabSize,
                )
                val logBuffer = requireNotNull(logTensor.floatBuffer)
                require(logBuffer.remaining() >= logProbabilities.size)
                logBuffer.get(logProbabilities)

                stateSpecs.forEach { stateSpec ->
                    val name = "new_" + stateSpec.name
                    val value = result.get(name)
                        .orElseThrow {
                            IllegalStateException("missing_" + name)
                        }
                    val tensor = value as? OnnxTensor
                        ?: error(name + "_not_tensor")
                    replacements[stateSpec.name] =
                        ReviewNativeTensorFactory.copy(
                            environment,
                            tensor,
                            stateSpec,
                        )
                }

                val nextProcessed = requireNotNull(
                    replacements["processed_lens"],
                )
                val longBuffer = requireNotNull(nextProcessed.longBuffer)
                require(longBuffer.remaining() >= 1)
                val nextProcessedLens = longBuffer.get(0)
                require(nextProcessedLens >= processedLens)

                states.values.forEach { it.close() }
                states.clear()
                states.putAll(replacements)
                replacements.clear()
                processedLens = nextProcessedLens

                val tokens = decoder.consume(
                    logProbabilities,
                    modelFrames,
                )
                return ReviewRecognitionChunk(
                    featureFrames = 0,
                    modelFrames = modelFrames,
                    tokens = tokens,
                    processedLens = processedLens,
                    bufferedFeatureFrames = windows.bufferedFrames,
                ).validated()
            }
        } finally {
            runCatching { x.close() }
            replacements.values.forEach { runCatching { it.close() } }
        }
    }

    private fun ensureOpen() {
        check(!closed) { "recognizer_closed" }
    }

    private class MutableRecognitionTotals(
        private var lastProcessedLens: Long,
    ) {
        var featureFrames: Int = 0
        var modelFrames: Int = 0
        val tokens = ArrayList<ReviewCtcToken>()

        fun add(chunk: ReviewRecognitionChunk) {
            featureFrames += chunk.featureFrames
            modelFrames += chunk.modelFrames
            tokens += chunk.tokens
            lastProcessedLens = chunk.processedLens
        }

        fun toChunk(
            processedLens: Long = lastProcessedLens,
            buffered: Int,
        ): ReviewRecognitionChunk =
            ReviewRecognitionChunk(
                featureFrames = featureFrames,
                modelFrames = modelFrames,
                tokens = tokens.toList(),
                processedLens = processedLens,
                bufferedFeatureFrames = buffered,
            ).validated()
    }

    companion object {
        const val BLANK_ID = 250
        private const val TAIL_SECONDS = 1.5
        private const val TAIL_SAMPLES =
            (ReviewKaldiFbank.SAMPLE_RATE * TAIL_SECONDS).toInt()
    }
}

internal object ReviewNativeTensorFactory {
    fun zero(
        environment: OrtEnvironment,
        spec: ReviewTensorSpec,
    ): OnnxTensor {
        val elements = spec.elementCount.toInt()
        return when (spec.dtype) {
            "float32" -> {
                val data = FloatArray(elements)
                float(environment, spec.dims, data)
            }
            "int64" -> {
                val buffer = ByteBuffer
                    .allocateDirect(elements * Long.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asLongBuffer()
                repeat(elements) { buffer.put(0L) }
                buffer.flip()
                OnnxTensor.createTensor(
                    environment,
                    buffer,
                    spec.dims,
                )
            }
            else -> error("unsupported_tensor_dtype")
        }
    }

    fun float(
        environment: OrtEnvironment,
        shape: LongArray,
        data: FloatArray,
    ): OnnxTensor {
        val buffer = ByteBuffer
            .allocateDirect(data.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.flip()
        return OnnxTensor.createTensor(
            environment,
            buffer,
            shape,
        )
    }

    fun copy(
        environment: OrtEnvironment,
        source: OnnxTensor,
        spec: ReviewTensorSpec,
    ): OnnxTensor {
        val info = source.info as? TensorInfo
            ?: error("state_not_tensor")
        require(info.shape.contentEquals(spec.dims))
        val elements = spec.elementCount.toInt()
        return when (spec.dtype) {
            "float32" -> {
                val raw = requireNotNull(source.floatBuffer)
                require(raw.remaining() >= elements)
                val data = FloatArray(elements)
                raw.get(data)
                float(environment, spec.dims, data)
            }
            "int64" -> {
                val raw = requireNotNull(source.longBuffer)
                require(raw.remaining() >= elements)
                val buffer = ByteBuffer
                    .allocateDirect(elements * Long.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asLongBuffer()
                repeat(elements) { buffer.put(raw.get()) }
                buffer.flip()
                OnnxTensor.createTensor(
                    environment,
                    buffer,
                    spec.dims,
                )
            }
            else -> error("unsupported_tensor_dtype")
        }
    }
}
