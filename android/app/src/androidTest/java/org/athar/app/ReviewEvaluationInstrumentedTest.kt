package org.athar.app

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

class ReviewEvaluationInstrumentedTest {
    @Test
    fun evaluateFrozenReviewCorpus() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("reviewEvaluation") == "true")
        val runId = requireNotNull(args.getString("reviewEvalRun"))
        require(runId.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        val gitSha = requireNotNull(args.getString("reviewEvalGitSha"))
        require(gitSha.matches(Regex("[a-f0-9]{40}")))
        val apkSha = requireNotNull(args.getString("reviewEvalApkSha"))
        require(apkSha.matches(Regex("[a-f0-9]{64}")))

        val app = ApplicationProvider
            .getApplicationContext<AtharApplication>()
        val graph = app.graph
        assertTrue(
            "Verified review model package must already be installed",
            graph.reviewModels.installed(),
        )
        val root = requireNotNull(
            app.getExternalFilesDir("review-eval"),
        ).resolve(runId)
        val manifestFile = root.resolve("manifest.json")
        assertTrue(manifestFile.isFile)

        val manifest = EvaluationManifest.decode(
            manifestFile.readText(Charsets.UTF_8),
        )
        assertEquals(
            graph.reviewModels.manifest.version,
            manifest.modelVersion,
        )

        val corpus = graph.reviewModels
            .verifiedArtifactFile("corpus")
        val runtime = ReviewNativeRuntime(graph.reviewModels)
        val aligner = ReviewPassageAligner()
        val results = ArrayList<EvaluationResult>()

        manifest.cases.forEach { case ->
            val wav = root.resolve(case.wavFile)
            assertTrue(wav.isFile)
            assertEquals(case.sha256, sha256(wav))
            val samples = EvaluationWav.read(wav)
            val passage = ReviewCorpusPassageReader.read(
                corpus,
                case.surah,
                case.ayah,
                case.ayahEnd,
            )

            val recognizer = runtime.openRecognizer()
            val heard = ArrayList<ReviewHeardChar>()
            var inferenceNs = 0L
            var finalizeNs = 0L
            var modelFrames = 0L
            var rawTokens = 0L
            var audioSamplesProcessed = 0L
            var firstTokenComputeMs: Double? = null
            var firstTokenAudioMs: Long? = null
            var peakPssKb = Debug.getPss()

            try {
                var offset = 0
                var chunkNumber = 0
                while (offset < samples.size) {
                    val end = minOf(
                        samples.size,
                        offset + ReviewAudioCaptureContract.CHUNK_SAMPLES,
                    )
                    val chunk = samples.copyOfRange(offset, end)
                    val started = SystemClock.elapsedRealtimeNanos()
                    val output = recognizer.accept(chunk)
                    inferenceNs +=
                        SystemClock.elapsedRealtimeNanos() - started
                    audioSamplesProcessed += chunk.size
                    modelFrames += output.modelFrames
                    rawTokens += output.tokens.size
                    heard += ReviewCtcExpansion.expand(output.tokens)

                    if (
                        firstTokenComputeMs == null &&
                        output.tokens.isNotEmpty()
                    ) {
                        firstTokenComputeMs =
                            inferenceNs / 1_000_000.0
                        firstTokenAudioMs =
                            audioSamplesProcessed * 1000L /
                                ReviewAudioCaptureContract.SAMPLE_RATE_HZ
                    }

                    chunkNumber++
                    if (chunkNumber % 10 == 0) {
                        peakPssKb = maxOf(
                            peakPssKb,
                            Debug.getPss(),
                        )
                    }
                    offset = end
                }

                val finalizeStarted =
                    SystemClock.elapsedRealtimeNanos()
                val tail = recognizer.finish()
                finalizeNs =
                    SystemClock.elapsedRealtimeNanos() -
                        finalizeStarted
                inferenceNs += finalizeNs
                modelFrames += tail.modelFrames
                rawTokens += tail.tokens.size
                heard += ReviewCtcExpansion.expand(tail.tokens)
                if (
                    firstTokenComputeMs == null &&
                    tail.tokens.isNotEmpty()
                ) {
                    firstTokenComputeMs =
                        inferenceNs / 1_000_000.0
                    firstTokenAudioMs =
                        samples.size * 1000L /
                            ReviewAudioCaptureContract.SAMPLE_RATE_HZ
                }
                peakPssKb = maxOf(peakPssKb, Debug.getPss())
            } finally {
                recognizer.close()
            }

            assertTrue(
                "Evaluation clip exceeded bounded aligner input",
                heard.size <= ReviewPassageAligner.MAX_HEARD_CHARS,
            )
            val verdict = aligner.align(
                passage,
                heard,
                final = true,
            )
            results += EvaluationResult(
                id = case.id,
                observation = ReviewEvaluationPolicy.observe(verdict),
                inferenceMillis = inferenceNs / 1_000_000.0,
                audioMillis =
                    samples.size * 1000L /
                        ReviewAudioCaptureContract.SAMPLE_RATE_HZ,
                finalizeMillis = finalizeNs / 1_000_000.0,
                firstTokenComputeMillis = firstTokenComputeMs,
                firstTokenAudioMillis = firstTokenAudioMs,
                peakPssKb = peakPssKb,
                modelFrames = modelFrames,
                rawTokens = rawTokens,
            )
        }

        val rootResult = JsonObject().apply {
            addProperty("schema_version", 1)
            addProperty("run_id", runId)
            addProperty("git_sha", gitSha)
            addProperty("apk_sha256", apkSha)
            addProperty(
                "model_version",
                graph.reviewModels.manifest.version,
            )
            addProperty("device_manufacturer", Build.MANUFACTURER)
            addProperty("device_model", Build.MODEL)
            addProperty("device_api", Build.VERSION.SDK_INT)
            addProperty("device_fingerprint", Build.FINGERPRINT)
            add(
                "device_abis",
                JsonArray().apply {
                    Build.SUPPORTED_ABIS.forEach { add(it) }
                },
            )
            add(
                "cases",
                JsonArray().apply {
                    results.forEach { add(it.toJson()) }
                },
            )
        }
        root.resolve("results.json").writeText(
            rootResult.toString(),
            Charsets.UTF_8,
        )
        assertEquals(manifest.cases.size, results.size)
    }

    private data class EvaluationCase(
        val id: String,
        val speakerId: String,
        val speakerClass: String,
        val errorKind: String,
        val expectation: ReviewEvaluationExpectation,
        val surah: Int,
        val ayah: Int,
        val ayahEnd: Int,
        val wavFile: String,
        val sha256: String,
    )

    private data class EvaluationManifest(
        val purpose: String,
        val modelVersion: String,
        val cases: List<EvaluationCase>,
    ) {
        companion object {
            fun decode(raw: String): EvaluationManifest {
                require(raw.length in 100..200_000)
                val root = JsonParser.parseString(raw).asJsonObject
                require(root["schema_version"].asInt == 1)
                val purpose = root["purpose"].asString
                require(purpose in setOf("development", "acceptance"))
                val modelVersion = root["model_version"].asString
                require(modelVersion.matches(Regex("[a-z0-9][a-z0-9._-]{2,80}")))
                val rows = root.getAsJsonArray("cases")
                require(rows.size() in 1..500)
                val cases = rows.map { element ->
                    val value = element.asJsonObject
                    val errorKind = value["error_kind"].asString
                    val expectation =
                        ReviewEvaluationExpectation.valueOf(
                            value["expectation"].asString,
                        )
                    EvaluationCase(
                        id = value["id"].asString,
                        speakerId = value["speaker_id"].asString,
                        speakerClass = value["speaker_class"].asString,
                        errorKind = errorKind,
                        expectation = expectation,
                        surah = value["surah"].asInt,
                        ayah = value["ayah"].asInt,
                        ayahEnd = value["ayah_end"]?.asInt
                            ?: value["ayah"].asInt,
                        wavFile = value["wav_file"].asString,
                        sha256 = value["sha256"].asString,
                    ).validated()
                }
                require(cases.map { it.id }.toSet().size == cases.size)
                return EvaluationManifest(
                    purpose,
                    modelVersion,
                    cases,
                )
            }

            private fun EvaluationCase.validated(): EvaluationCase {
                require(id.matches(Regex("[A-Za-z0-9._-]{1,80}")))
                require(
                    speakerId.matches(
                        Regex("[A-Za-z0-9._-]{1,80}"),
                    ),
                )
                require(
                    speakerClass in setOf(
                        "reference_reciter",
                        "learner",
                    ),
                )
                require(
                    errorKind in setOf(
                        "clean",
                        "substitution",
                        "omission",
                        "repetition",
                        "noisy",
                    ),
                )
                val expectedForKind = when (errorKind) {
                    "clean" ->
                        ReviewEvaluationExpectation.CLEAR_MATCH
                    "noisy" ->
                        ReviewEvaluationExpectation.ABSTAIN
                    else ->
                        ReviewEvaluationExpectation.POSSIBLE_ISSUE
                }
                require(expectation == expectedForKind)
                require(surah in 1..114)
                require(ayah >= 1)
                require(ayahEnd >= ayah)
                require(ayahEnd - ayah + 1 in 1..5)
                require(
                    wavFile.matches(
                        Regex("[A-Za-z0-9._-]{1,120}\\.wav"),
                    ),
                )
                require(
                    sha256.matches(Regex("[a-f0-9]{64}")),
                )
                return this
            }
        }
    }

    private data class EvaluationResult(
        val id: String,
        val observation: ReviewEvaluationObservation,
        val inferenceMillis: Double,
        val audioMillis: Long,
        val finalizeMillis: Double,
        val firstTokenComputeMillis: Double?,
        val firstTokenAudioMillis: Long?,
        val peakPssKb: Int,
        val modelFrames: Long,
        val rawTokens: Long,
    ) {
        fun toJson(): JsonObject = JsonObject().apply {
            addProperty("id", id)
            addProperty("observation", observation.name)
            addProperty("inference_ms", inferenceMillis)
            addProperty("audio_ms", audioMillis)
            addProperty("finalize_ms", finalizeMillis)
            if (firstTokenComputeMillis == null) {
                add("first_token_compute_ms", JsonNull.INSTANCE)
            } else {
                addProperty(
                    "first_token_compute_ms",
                    firstTokenComputeMillis,
                )
            }
            if (firstTokenAudioMillis == null) {
                add("first_token_audio_ms", JsonNull.INSTANCE)
            } else {
                addProperty(
                    "first_token_audio_ms",
                    firstTokenAudioMillis,
                )
            }
            addProperty("peak_pss_kb", peakPssKb)
            addProperty("model_frames", modelFrames)
            addProperty("raw_tokens", rawTokens)
        }
    }

    private object EvaluationWav {
        private const val MAX_BYTES = 5L * 1024L * 1024L
        private const val RIFF_HEADER = 12

        fun read(file: File): FloatArray {
            require(file.isFile && file.length() in RIFF_HEADER..MAX_BYTES)
            val bytes = file.readBytes()
            require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
            require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")

            var offset = RIFF_HEADER
            var channels = -1
            var sampleRate = -1
            var bits = -1
            var format = -1
            var dataOffset = -1
            var dataSize = -1

            while (offset + 8 <= bytes.size) {
                val id = String(
                    bytes,
                    offset,
                    4,
                    Charsets.US_ASCII,
                )
                val size = intLe(bytes, offset + 4)
                require(size >= 0)
                val start = offset + 8
                val end = start + size
                require(end <= bytes.size)
                when (id) {
                    "fmt " -> {
                        require(size >= 16)
                        format = shortLe(bytes, start)
                        channels = shortLe(bytes, start + 2)
                        sampleRate = intLe(bytes, start + 4)
                        bits = shortLe(bytes, start + 14)
                    }
                    "data" -> {
                        dataOffset = start
                        dataSize = size
                    }
                }
                offset = end + (size and 1)
            }

            require(format == 1)
            require(channels == 1)
            require(sampleRate == ReviewAudioCaptureContract.SAMPLE_RATE_HZ)
            require(bits == 16)
            require(dataOffset >= 0 && dataSize > 0 && dataSize % 2 == 0)
            val sampleCount = dataSize / 2
            require(
                sampleCount in
                    1..(120 * ReviewAudioCaptureContract.SAMPLE_RATE_HZ),
            )
            val output = FloatArray(sampleCount)
            var cursor = dataOffset
            for (index in output.indices) {
                val value = shortLeSigned(bytes, cursor)
                output[index] =
                    (value / 32768f).coerceIn(-1f, 1f)
                cursor += 2
            }
            return output
        }

        private fun intLe(bytes: ByteArray, offset: Int): Int =
            ByteBuffer.wrap(bytes, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int

        private fun shortLe(bytes: ByteArray, offset: Int): Int =
            ByteBuffer.wrap(bytes, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt() and 0xffff

        private fun shortLeSigned(bytes: ByteArray, offset: Int): Int =
            ByteBuffer.wrap(bytes, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .short
                .toInt()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }
}
