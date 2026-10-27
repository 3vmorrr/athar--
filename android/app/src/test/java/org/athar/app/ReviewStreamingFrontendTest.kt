package org.athar.app

import kotlin.math.ln
import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewStreamingFrontendTest {
    @Test fun fbankReadinessMatchesPinnedTilawaContract() {
        val rows = listOf(
            Triple(0, 0, 0),
            Triple(119, 0, 1),
            Triple(120, 0, 1),
            Triple(279, 0, 2),
            Triple(280, 1, 2),
            Triple(439, 1, 3),
            Triple(440, 2, 3),
            Triple(759, 3, 5),
            Triple(760, 4, 5),
            Triple(16_000, 99, 100),
        )
        rows.forEach { (samples, streamingExpected, totalExpected) ->
            val fbank = ReviewKaldiFbank()
            val streaming = fbank.acceptWaveform(
                FloatArray(samples) { index ->
                    ((index * 17) % 100) / 200f - 0.25f
                },
            )
            val flushed = fbank.inputFinished()
            assertEquals(
                "streaming samples=$samples",
                streamingExpected,
                streaming.size,
            )
            assertEquals(
                "total samples=$samples",
                totalExpected,
                streaming.size + flushed.size,
            )
        }
    }

    @Test fun zeroSignalUsesConfiguredLogFloorAndEightyBins() {
        val fbank = ReviewKaldiFbank()
        val frames = fbank.acceptWaveform(FloatArray(280))
        assertEquals(1, frames.size)
        val frame = frames.single()
        assertEquals(80, frame.size)
        val expected = ln(1.1920929e-7).toFloat()
        frame.forEach { value ->
            assertEquals(expected, value, 0.0001f)
        }
    }

    @Test fun fbankStreamingStorageStaysBoundedAcrossLongInput() {
        val fbank = ReviewKaldiFbank()
        repeat(2_000) {
            fbank.acceptWaveform(FloatArray(1_600))
            assertTrue(fbank.retainedSamples <= 4_000)
        }
    }

    @Test fun ctcCollapsesRepeatedRunsAndBlankTerminatesRun() {
        val decoder = ReviewGreedyCtcDecoder(
            vocabSize = 5,
            blankId = 4,
        )
        val frames = 4
        val probabilities = FloatArray(frames * 5) { -20f }
        probabilities[0 * 5 + 1] = 0f
        probabilities[0 * 5 + 2] = -1f

        probabilities[1 * 5 + 1] = 1f
        probabilities[1 * 5 + 2] = -1f

        probabilities[2 * 5 + 4] = 0f
        probabilities[2 * 5 + 1] = -5f

        probabilities[3 * 5 + 3] = 0f
        probabilities[3 * 5 + 2] = -0.5f

        val tokens = decoder.consume(probabilities, frames) +
            decoder.flush()
        assertEquals(listOf(1, 3), tokens.map { it.id })
        assertEquals(listOf(0, 3), tokens.map { it.frame })
        assertEquals(4, decoder.framesDecoded)
        assertTrue(tokens.all { it.margin >= 0f })
    }

    @Test fun featureWindowMatchesSixtyOneFrameFortyEightHopContract() {
        val buffer = ReviewFeatureWindowBuffer(
            timeFrames = 61,
            hopFrames = 48,
            featureDim = 80,
        )
        fun frame(value: Float) = FloatArray(80) { value }

        repeat(47) { buffer.add(frame(it.toFloat())) }
        assertFalse(buffer.hasWindow)
        assertEquals(47, buffer.bufferedFrames)

        var windows = 0
        repeat(48) {
            buffer.add(frame((100 + it).toFloat()))
            if (buffer.hasWindow) {
                val value = buffer.takeWindow()
                assertEquals(61 * 80, value.size)
                windows++
            }
        }
        assertEquals(1, windows)
        assertEquals(47, buffer.bufferedFrames)
    }

    @Test fun recognitionChunkNeverCarriesQuranVerdictOrTranscript() {
        val value = ReviewRecognitionChunk(
            featureFrames = 48,
            modelFrames = 12,
            tokens = listOf(ReviewCtcToken(7, 0, 0.9f)),
            processedLens = 24,
            bufferedFeatureFrames = 47,
        ).validated()
        assertEquals(7, value.tokens.single().id)
        assertFalse(
            value.toString().contains("correct", ignoreCase = true),
        )
        assertFalse(
            value.toString().contains("ayah", ignoreCase = true),
        )
        assertFalse(
            value.toString().contains("transcript", ignoreCase = true),
        )
    }
}
