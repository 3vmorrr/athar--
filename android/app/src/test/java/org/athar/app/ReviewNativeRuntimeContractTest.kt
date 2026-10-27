package org.athar.app

import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewNativeRuntimeContractTest {
    @Test fun ioContractAcceptsCurrentZipformerShapeAndBoundsMemory() {
        val json = """
        {
          "T":61,
          "hop":48,
          "featureDim":80,
          "vocabSize":251,
          "inputs":[
            {"name":"x","dims":[1,61,80],"dtype":"float32"},
            {"name":"processed_lens","dims":[1],"dtype":"int64"}
          ],
          "outputs":[
            {"name":"log_probs","dims":[1,12,251],"dtype":"float32"},
            {"name":"new_processed_lens","dims":[1],"dtype":"int64"}
          ]
        }
        """.trimIndent()
        val value = ReviewModelIoCodec.decode(json)
        assertEquals(61, value.timeFrames)
        assertEquals(48, value.hopFrames)
        assertEquals(80, value.featureDim)
        assertEquals(251, value.vocabSize)
        assertTrue(value.inputBytes <= 16L * 1024L * 1024L)
    }

    @Test fun ioContractRejectsUnexpectedFrontendOrDtype() {
        val wrongFeature = """
        {
          "T":61,"hop":48,"featureDim":40,"vocabSize":251,
          "inputs":[
            {"name":"x","dims":[1,61,40],"dtype":"float32"},
            {"name":"processed_lens","dims":[1],"dtype":"int64"}
          ],
          "outputs":[
            {"name":"log_probs","dims":[1,12,251],"dtype":"float32"},
            {"name":"new_processed_lens","dims":[1],"dtype":"int64"}
          ]
        }
        """.trimIndent()
        assertTrue(runCatching { ReviewModelIoCodec.decode(wrongFeature) }.isFailure)

        assertTrue(
            runCatching {
                ReviewTensorSpec(
                    name = "bad",
                    dims = longArrayOf(1),
                    dtype = "float64",
                ).validated()
            }.isFailure,
        )
    }

    @Test fun pcm16ConversionIsBoundedAndDoesNotRetainAudio() {
        val samples = shortArrayOf(
            Short.MIN_VALUE,
            -16384,
            0,
            16384,
            Short.MAX_VALUE,
        )
        val normalized = ReviewAudioCaptureContract.normalizePcm16(
            samples,
            samples.size,
        )
        assertEquals(-1f, normalized.first(), 0.0001f)
        assertEquals(0f, normalized[2], 0.0001f)
        assertTrue(normalized.last() <= 1f)
        assertEquals(1f, ReviewAudioCaptureContract.peak(normalized), 0.0001f)
        assertEquals(1600, ReviewAudioCaptureContract.CHUNK_SAMPLES)
        assertEquals(6400, ReviewAudioCaptureContract.MAX_CALLER_BUFFERED_SAMPLES)
    }

    @Test fun runtimeProbeResultNeverClaimsRecognitionAccuracy() {
        val value = ReviewRuntimeProbeResult(
            runtimeVersion = "1.31.0",
            inputCount = 99,
            outputCount = 99,
            boundedInputBytes = 3_279_432,
            logProbabilityShape = longArrayOf(1, 12, 251),
        ).validated()
        assertEquals(99, value.inputCount)
        assertFalse(value.toString().contains("accuracy", ignoreCase = true))
        assertFalse(value.toString().contains("correct", ignoreCase = true))
    }
}
