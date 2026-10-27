package org.athar.app.review

import kotlin.math.ceil

enum class ReviewEvaluationExpectation {
    CLEAR_MATCH,
    POSSIBLE_ISSUE,
    ABSTAIN,
}

enum class ReviewEvaluationObservation {
    CLEAR_MATCH,
    POSSIBLE_ISSUE,
    ABSTAIN,
}

data class ReviewEvaluationRecord(
    val id: String,
    val speakerClass: String,
    val errorKind: String,
    val expectation: ReviewEvaluationExpectation,
    val observation: ReviewEvaluationObservation,
    val inferenceMillis: Double,
    val audioMillis: Long,
    val finalizeMillis: Double,
    val firstTokenComputeMillis: Double?,
    val firstTokenAudioMillis: Long?,
    val peakPssKb: Int,
) {
    fun validated(): ReviewEvaluationRecord {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        require(speakerClass in setOf("reference_reciter", "learner"))
        require(
            errorKind in setOf(
                "clean",
                "substitution",
                "omission",
                "repetition",
                "noisy",
            ),
        )
        require(inferenceMillis.isFinite() && inferenceMillis >= 0.0)
        require(audioMillis > 0)
        require(finalizeMillis.isFinite() && finalizeMillis >= 0.0)
        require(
            firstTokenComputeMillis == null ||
                (
                    firstTokenComputeMillis.isFinite() &&
                        firstTokenComputeMillis >= 0.0
                ),
        )
        require(firstTokenAudioMillis == null || firstTokenAudioMillis >= 0)
        require(peakPssKb > 0)
        return this
    }
}

object ReviewEvaluationPolicy {
    fun observe(
        verdict: ReviewPassageVerdict,
    ): ReviewEvaluationObservation = when {
        verdict.heardCharacters <= 0 ->
            ReviewEvaluationObservation.ABSTAIN
        verdict.hasProblem ->
            ReviewEvaluationObservation.POSSIBLE_ISSUE
        verdict.hasUncertainty ->
            ReviewEvaluationObservation.ABSTAIN
        else ->
            ReviewEvaluationObservation.CLEAR_MATCH
    }
}

data class ReviewEvaluationMetrics(
    val cleanCases: Int,
    val issueCases: Int,
    val abstainExpectedCases: Int,
    val falseAccepts: Int,
    val falseRejects: Int,
    val abstentions: Int,
    val noisyUnsafeDecisions: Int,
    val falseAcceptRate: Double,
    val falseRejectRate: Double,
    val abstentionRate: Double,
    val noisyUnsafeDecisionRate: Double,
    val medianInferenceMillis: Double,
    val p95InferenceMillis: Double,
    val medianRealTimeFactor: Double,
    val p95RealTimeFactor: Double,
    val p95FirstTokenComputeMillis: Double?,
    val p95FirstTokenAudioMillis: Long?,
    val maxPeakPssKb: Int,
)

object ReviewEvaluationMetricsCalculator {
    fun calculate(
        records: List<ReviewEvaluationRecord>,
    ): ReviewEvaluationMetrics {
        require(records.isNotEmpty())
        records.forEach { it.validated() }
        require(records.map { it.id }.toSet().size == records.size)

        val clean = records.filter {
            it.expectation == ReviewEvaluationExpectation.CLEAR_MATCH
        }
        val issue = records.filter {
            it.expectation == ReviewEvaluationExpectation.POSSIBLE_ISSUE
        }
        val abstainExpected = records.filter {
            it.expectation == ReviewEvaluationExpectation.ABSTAIN
        }
        require(clean.isNotEmpty() && issue.isNotEmpty())

        val falseAccepts = issue.count {
            it.observation == ReviewEvaluationObservation.CLEAR_MATCH
        }
        val falseRejects = clean.count {
            it.observation == ReviewEvaluationObservation.POSSIBLE_ISSUE
        }
        val abstentions = records.count {
            it.observation == ReviewEvaluationObservation.ABSTAIN
        }
        val noisyUnsafe = abstainExpected.count {
            it.observation != ReviewEvaluationObservation.ABSTAIN
        }

        val inference = records.map { it.inferenceMillis }
        val realTimeFactors = records.map {
            it.inferenceMillis / it.audioMillis.toDouble()
        }
        val firstCompute = records.mapNotNull {
            it.firstTokenComputeMillis
        }
        val firstAudio = records.mapNotNull {
            it.firstTokenAudioMillis
        }

        return ReviewEvaluationMetrics(
            cleanCases = clean.size,
            issueCases = issue.size,
            abstainExpectedCases = abstainExpected.size,
            falseAccepts = falseAccepts,
            falseRejects = falseRejects,
            abstentions = abstentions,
            noisyUnsafeDecisions = noisyUnsafe,
            falseAcceptRate = falseAccepts.toDouble() / issue.size,
            falseRejectRate = falseRejects.toDouble() / clean.size,
            abstentionRate = abstentions.toDouble() / records.size,
            noisyUnsafeDecisionRate = if (abstainExpected.isEmpty()) {
                0.0
            } else {
                noisyUnsafe.toDouble() / abstainExpected.size
            },
            medianInferenceMillis = median(inference),
            p95InferenceMillis = percentile95(inference),
            medianRealTimeFactor = median(realTimeFactors),
            p95RealTimeFactor = percentile95(realTimeFactors),
            p95FirstTokenComputeMillis = firstCompute
                .takeIf { it.isNotEmpty() }
                ?.let(::percentile95),
            p95FirstTokenAudioMillis = firstAudio
                .takeIf { it.isNotEmpty() }
                ?.let { percentile95(it.map(Long::toDouble)).toLong() },
            maxPeakPssKb = records.maxOf { it.peakPssKb },
        )
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun percentile95(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val index = (ceil(0.95 * sorted.size).toInt() - 1)
            .coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
