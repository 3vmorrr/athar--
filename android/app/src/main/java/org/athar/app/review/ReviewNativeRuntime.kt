package org.athar.app.review

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
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
        require(outputCount in 2..128)
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
    suspend fun probe(): ReviewRuntimeProbeResult =
        withContext(Dispatchers.Default) {
            val loaded = verifiedModel()
            createSession(
                loaded.model.absolutePath,
                loaded.io,
            ).use { session ->
                val tensors = LinkedHashMap<String, OnnxTensor>()
                try {
                    loaded.io.inputs.forEach { spec ->
                        tensors[spec.name] = ReviewNativeTensorFactory.zero(
                            OrtEnvironment.getEnvironment(),
                            spec,
                        )
                    }
                    session.run(
                        tensors,
                        setOf("log_probs"),
                    ).use { results ->
                        val output = results.get("log_probs")
                            .orElseThrow {
                                IllegalStateException("missing_log_probs")
                            }
                        val info = output.info as? TensorInfo
                            ?: error("log_probs_not_tensor")
                        val shape = info.shape
                        val expected = loaded.io.outputs
                            .single { it.name == "log_probs" }
                            .dims
                        require(shape.contentEquals(expected))
                        ReviewRuntimeProbeResult(
                            runtimeVersion =
                                OrtEnvironment.getEnvironment().version,
                            inputCount = session.inputNames.size,
                            outputCount = session.outputNames.size,
                            boundedInputBytes = loaded.io.inputBytes,
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

    suspend fun openRecognizer(): ReviewStreamingRecognizer =
        withContext(Dispatchers.Default) {
            val loaded = verifiedModel()
            val environment = OrtEnvironment.getEnvironment()
            val session = createSession(
                loaded.model.absolutePath,
                loaded.io,
            )
            try {
                ReviewStreamingRecognizer(
                    environment = environment,
                    session = session,
                    io = loaded.io,
                )
            } catch (failure: Exception) {
                runCatching { session.close() }
                throw failure
            }
        }

    private fun verifiedModel(): LoadedReviewModel {
        require(manager.installed())
        val model = manager.verifiedArtifactFile("model")
        val ioFile = manager.verifiedArtifactFile("io")
        require(
            ioFile.length() in
                100..ReviewModelIoCodec.MAX_IO_JSON_BYTES.toLong(),
        )
        val io = ReviewModelIoCodec.decode(
            ioFile.readText(Charsets.UTF_8),
        )
        return LoadedReviewModel(model, io)
    }

    private fun createSession(
        modelPath: String,
        io: ReviewModelIoSpec,
    ): OrtSession {
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
            val session = environment.createSession(
                modelPath,
                options,
            )
            try {
                require(
                    session.inputNames ==
                        io.inputs.map { it.name }.toSet(),
                )
                require(
                    session.outputNames ==
                        io.outputs.map { it.name }.toSet(),
                )
                return session
            } catch (failure: Exception) {
                runCatching { session.close() }
                throw failure
            }
        }
    }

    private data class LoadedReviewModel(
        val model: java.io.File,
        val io: ReviewModelIoSpec,
    )
}
