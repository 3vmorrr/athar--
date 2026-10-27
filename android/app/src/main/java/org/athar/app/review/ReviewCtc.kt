package org.athar.app.review

import kotlin.math.exp

data class ReviewCtcToken(
    val id: Int,
    val frame: Int,
    val margin: Float,
) {
    fun validated(vocabSize: Int): ReviewCtcToken {
        require(id in 0 until vocabSize)
        require(frame >= 0)
        require(margin.isFinite() && margin >= 0f)
        return this
    }
}

/**
 * Greedy streaming CTC collapse. Task 09.3 intentionally exposes token IDs only.
 * Quran text/alignment and correctness verdicts remain Task 09.4.
 */
class ReviewGreedyCtcDecoder(
    private val vocabSize: Int = 251,
    private val blankId: Int = 250,
) {
    private data class Run(
        val id: Int,
        val frame: Int,
        var top: Float,
        var second: Float,
    )

    private var previousBest = blankId
    private var frameIndex = 0
    private var run: Run? = null

    val framesDecoded: Int get() = frameIndex

    init {
        require(vocabSize in 2..4096)
        require(blankId in 0 until vocabSize)
    }

    fun consume(
        logProbabilities: FloatArray,
        frames: Int,
    ): List<ReviewCtcToken> {
        require(frames >= 0)
        require(logProbabilities.size == frames * vocabSize)
        val out = ArrayList<ReviewCtcToken>()

        for (time in 0 until frames) {
            val row = time * vocabSize
            var best = 0
            var top = logProbabilities[row]
            var second = Float.NEGATIVE_INFINITY
            for (candidate in 1 until vocabSize) {
                val value = logProbabilities[row + candidate]
                if (value > top) {
                    second = top
                    top = value
                    best = candidate
                } else if (value > second) {
                    second = value
                }
            }
            step(best, top, second, out)
        }
        return out
    }

    fun flush(): List<ReviewCtcToken> {
        val current = run ?: run {
            previousBest = blankId
            return emptyList()
        }
        run = null
        previousBest = blankId
        return listOf(emit(current))
    }

    fun reset() {
        previousBest = blankId
        frameIndex = 0
        run = null
    }

    private fun step(
        best: Int,
        top: Float,
        second: Float,
        out: MutableList<ReviewCtcToken>,
    ) {
        if (best != blankId && best != previousBest) {
            run?.let { out += emit(it) }
            run = Run(best, frameIndex, top, second)
        } else if (
            best != blankId &&
            best == previousBest &&
            run != null &&
            top > requireNotNull(run).top
        ) {
            requireNotNull(run).top = top
            requireNotNull(run).second = second
        } else if (best == blankId && run != null) {
            out += emit(requireNotNull(run))
            run = null
        }
        previousBest = best
        frameIndex++
    }

    private fun emit(value: Run): ReviewCtcToken {
        val topProbability = exp(value.top.toDouble())
        val secondProbability = exp(value.second.toDouble())
        return ReviewCtcToken(
            id = value.id,
            frame = value.frame,
            margin = (topProbability - secondProbability)
                .coerceAtLeast(0.0)
                .toFloat(),
        ).validated(vocabSize)
    }
}
