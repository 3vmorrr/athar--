package org.athar.app.review

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReviewRuntimeProbeResult(
    val runtimeVersion: String,
    val inputCount: Int,
    val outputCount: Int,
    val boundedInputBytes: Long,
    val logProbabilityShape: LongArray,
) {
    fun validated(): ReviewRuntimeProbeResult {
        require(runtimeVersion.isNotBlank() && runtimeVersion.length <= 80)
        require(inputCount in 2..128)
        require(outputCount in 1..128)
        require(boundedInputBytes in 1..16L * 1024L * 1024L)
        require(logProbabilityShape.contentEquals(longArrayOf(1, 12, 251)))
        return this
    }

    override fun equals(other: Any?): Boolean =
        other is ReviewRuntimeProbeResult &&
            runtimeVersion == other.runtimeVersion &&
            inputCount == other.inputCount &&
            outputCount == other.outputCount &&
            boundedInputBytes == other.boundedInputBytes &&
            logProbabilityShape.contentEquals(other.logProbabilityShape)

    override fun hashCode(): Int =
        31 * (
            31 * (
                31 * (
                    31 * runtimeVersion.hashCode() + inputCount
                ) + outputCount
            ) + boundedInputBytes.hashCode()
        ) + logProbabilityShape.contentHashCode()
}

class ReviewNativeRuntime(
    private val manager: ReviewModelManager,
) {
    suspend fun probe(): ReviewRuntimeProbeResult = withContext(Dispatchers.Default) {
        require(manager.installed())
        val model = manager.verifiedArtifactFile("model")
        val ioFile = manager.verifiedArtifactFile("io")
        require(ioFile.length() in 100..ReviewModelIoCodec.MAX_IO_JSON_BYTES.toLong())
        val io = ReviewModelIoCodec.decode(ioFile.readText(Charsets.UTF_8))

        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            options.setExecutionMode(
                OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL,
            )
            options.setOptimizationLevel(
                OrtSession.SessionOptions.OptLevel.BASIC_OPT,
            )
            environment.createSession(model.absolutePath, options).use { session ->
                require(session.inputNames == io.inputs.map { it.name }.toSet())
                require(session.outputNames == io.outputs.map { it.name }.toSet())

                val tensors = LinkedHashMap<String, OnnxTensor>()
                try {
                    io.inputs.forEach { spec ->
                        tensors[spec.name] = zeroTensor(environment, spec)
                    }
                    session.run(tensors, setOf("log_probs")).use { results ->
                        val output = results.get("log_probs")
                            .orElseThrow { IllegalStateException("missing_log_probs") }
                        val info = output.info as? TensorInfo
                            ?: error("log_probs_not_tensor")
                        val shape = info.shape
                        val expected = io.outputs
                            .single { it.name == "log_probs" }
                            .dims
                        require(shape.contentEquals(expected))
                        ReviewRuntimeProbeResult(
                            runtimeVersion = environment.version,
                            inputCount = session.inputNames.size,
                            outputCount = session.outputNames.size,
                            boundedInputBytes = io.inputBytes,
                            logProbabilityShape = shape,
                        ).validated()
                    }
                } finally {
                    tensors.values.forEach { tensor ->
                        runCatching { tensor.close() }
                    }
                }
            }
        }
    }

    private fun zeroTensor(
        environment: OrtEnvironment,
        spec: ReviewTensorSpec,
    ): OnnxTensor {
        val elements = spec.elementCount.toInt()
        return when (spec.dtype) {
            "float32" -> {
                val buffer = ByteBuffer
                    .allocateDirect(elements * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                repeat(elements) { buffer.put(0f) }
                buffer.flip()
                OnnxTensor.createTensor(environment, buffer, spec.dims)
            }
            "int64" -> {
                val buffer = ByteBuffer
                    .allocateDirect(elements * Long.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asLongBuffer()
                repeat(elements) { buffer.put(0L) }
                buffer.flip()
                OnnxTensor.createTensor(environment, buffer, spec.dims)
            }
            else -> error("unsupported_tensor_dtype")
        }
    }
}
