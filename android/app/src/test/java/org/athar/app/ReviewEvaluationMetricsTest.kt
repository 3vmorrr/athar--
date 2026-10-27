package org.athar.app

import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewEvaluationMetricsTest {
    private fun record(
        id: String,
        expectation: ReviewEvaluationExpectation,
        observation: ReviewEvaluationObservation,
        errorKind: String,
        inference: Double = 100.0,
        audio: Long = 1000,
    ) = ReviewEvaluationRecord(
        id = id,
        speakerClass = "learner",
        errorKind = errorKind,
        expectation = expectation,
        observation = observation,
        inferenceMillis = inference,
        audioMillis = audio,
        finalizeMillis = 10.0,
        firstTokenComputeMillis = 20.0,
        firstTokenAudioMillis = 300,
        peakPssKb = 100_000,
    ).validated()

    @Test fun falseAcceptRejectAndAbstentionRemainSeparateMetrics() {
        val metrics = ReviewEvaluationMetricsCalculator.calculate(
            listOf(
                record(
                    "clean-ok",
                    ReviewEvaluationExpectation.CLEAR_MATCH,
                    ReviewEvaluationObservation.CLEAR_MATCH,
                    "clean",
                    inference = 80.0,
                ),
                record(
                    "clean-rejected",
                    ReviewEvaluationExpectation.CLEAR_MATCH,
                    ReviewEvaluationObservation.POSSIBLE_ISSUE,
                    "clean",
                    inference = 100.0,
                ),
                record(
                    "error-missed",
                    ReviewEvaluationExpectation.POSSIBLE_ISSUE,
                    ReviewEvaluationObservation.CLEAR_MATCH,
                    "substitution",
                    inference = 120.0,
                ),
                record(
                    "error-abstain",
                    ReviewEvaluationExpectation.POSSIBLE_ISSUE,
                    ReviewEvaluationObservation.ABSTAIN,
                    "omission",
                    inference = 140.0,
                ),
                record(
                    "noise-abstain",
                    ReviewEvaluationExpectation.ABSTAIN,
                    ReviewEvaluationObservation.ABSTAIN,
                    "noisy",
                    inference = 160.0,
                ),
            ),
        )
        assertEquals(1, metrics.falseAccepts)
        assertEquals(1, metrics.falseRejects)
        assertEquals(2, metrics.abstentions)
        assertEquals(0.5, metrics.falseAcceptRate, 0.0001)
        assertEquals(0.5, metrics.falseRejectRate, 0.0001)
        assertEquals(0.4, metrics.abstentionRate, 0.0001)
        assertEquals(0.0, metrics.noisyUnsafeDecisionRate, 0.0001)
        assertEquals(120.0, metrics.medianInferenceMillis, 0.0001)
        assertEquals(160.0, metrics.p95InferenceMillis, 0.0001)
    }

    @Test fun uncertainVerdictAlwaysMapsToAbstention() {
        val verdict = ReviewPassageVerdict(
            ayahs = listOf(
                ReviewAyahVerdict(
                    1,
                    1,
                    listOf(
                        ReviewWordVerdict(
                            1,
                            1,
                            0,
                            "بسم",
                            ReviewWordState.UNCERTAIN,
                            0.2f,
                            1f,
                            0.1f,
                        ).validated(),
                    ),
                ),
            ),
            heardCharacters = 3,
            final = true,
        )
        assertEquals(
            ReviewEvaluationObservation.ABSTAIN,
            ReviewEvaluationPolicy.observe(verdict),
        )
    }
}
