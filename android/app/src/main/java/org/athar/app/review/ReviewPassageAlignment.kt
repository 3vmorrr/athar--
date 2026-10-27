package org.athar.app.review

import kotlin.math.max

enum class ReviewWordState {
    EMPTY,
    PENDING,
    CONFIRMED,
    UNCERTAIN,
    WRONG,
    SKIPPED,
}

data class ReviewHeardChar(
    val value: Char,
    val frame: Int,
    val margin: Float,
) {
    fun validated(): ReviewHeardChar {
        require(frame >= 0)
        require(margin.isFinite() && margin >= 0f)
        return this
    }
}

data class ReviewWordVerdict(
    val surah: Int,
    val ayah: Int,
    val word: Int,
    val glyph: String,
    val state: ReviewWordState,
    val distance: Float,
    val heardRatio: Float,
    val margin: Float,
) {
    fun validated(): ReviewWordVerdict {
        require(surah in 1..114)
        require(ayah in 1..286)
        require(word >= 0)
        require(glyph.isNotBlank())
        require(distance.isFinite() && distance in 0f..10f)
        require(heardRatio.isFinite() && heardRatio in 0f..10f)
        require(margin.isFinite() && margin >= 0f)
        return this
    }
}

data class ReviewAyahVerdict(
    val surah: Int,
    val ayah: Int,
    val words: List<ReviewWordVerdict>,
) {
    val state: ReviewWordState
        get() = when {
            words.all { it.state == ReviewWordState.EMPTY } ->
                ReviewWordState.EMPTY
            words.any {
                it.state == ReviewWordState.WRONG ||
                    it.state == ReviewWordState.SKIPPED
            } -> ReviewWordState.WRONG
            words.any { it.state == ReviewWordState.UNCERTAIN } ->
                ReviewWordState.UNCERTAIN
            words.any { it.state == ReviewWordState.PENDING } ->
                ReviewWordState.PENDING
            words.all { it.state == ReviewWordState.CONFIRMED } ->
                ReviewWordState.CONFIRMED
            else -> ReviewWordState.PENDING
        }
}

data class ReviewPassageVerdict(
    val ayahs: List<ReviewAyahVerdict>,
    val heardCharacters: Int,
    val final: Boolean,
) {
    val hasProblem: Boolean
        get() = ayahs.flatMap { it.words }.any {
            it.state == ReviewWordState.WRONG ||
                it.state == ReviewWordState.SKIPPED
        }

    val hasUncertainty: Boolean
        get() = ayahs.flatMap { it.words }.any {
            it.state == ReviewWordState.UNCERTAIN ||
                it.state == ReviewWordState.PENDING
        }
}

object ReviewCtcExpansion {
    fun expand(tokens: List<ReviewCtcToken>): List<ReviewHeardChar> {
        val output = ArrayList<ReviewHeardChar>()
        tokens.forEach { token ->
            val symbol = ReviewCtcVocabulary.symbol(token.id)
            symbol.forEach { character ->
                output += ReviewHeardChar(
                    value = character,
                    frame = token.frame,
                    margin = token.margin,
                ).validated()
            }
        }
        return output
    }
}

object ReviewPhonemeCost {
    private const val INSERT_DELETE = 1f
    private val canonical = mapOf(
        'ۦ' to 'ي',
        'ۥ' to 'و',
        'ں' to 'ن',
        '۾' to 'م',
        'ٱ' to 'ا',
        'ى' to 'ي',
    )
    private val hamza = setOf('ء', 'أ', 'إ', 'آ', 'ا', 'ؤ', 'ئ', 'ٲ')
    private val shortVowels = setOf('َ', 'ُ', 'ِ')
    private val otherMarks = setOf('ڇ', 'ؙ', 'ۣ', 'ٞ', 'ۜ', '۪', 'ـ')
    private val marks = shortVowels + otherMarks
    private val neighborGroups = listOf(
        "ذدضتط",
        "ظزذصسث",
        "جزش",
        "ةهت",
        "قكغ",
        "فبم",
    )
    private val neighborPairs = setOf(
        ordered('ه', 'ح'),
        ordered('غ', 'خ'),
        ordered('ء', 'ع'),
        ordered('ن', 'م'),
        ordered('ن', 'ل'),
        ordered('ظ', 'ض'),
    )
    private val neighbors = buildSet {
        neighborGroups.forEach { group ->
            for (i in group.indices) {
                for (j in i + 1 until group.length) {
                    add(ordered(group[i], group[j]))
                }
            }
        }
        addAll(neighborPairs)
    }

    fun substitution(heard: Char, expected: Char): Float {
        if (heard == expected) return 0f
        val h = canonical[heard] ?: heard
        val e = canonical[expected] ?: expected
        if (h == e) return 0f

        val heardMark = heard in marks
        val expectedMark = expected in marks
        if (heardMark || expectedMark) {
            return if (heardMark && expectedMark) {
                if (
                    heard in shortVowels &&
                    expected in shortVowels
                ) 0.1f else 0.25f
            } else {
                1f
            }
        }
        if (h in hamza && e in hamza) return 0.1f
        if (ordered(h, e) in neighbors) return 0.25f
        return 1f
    }

    fun insertionDeletion(): Float = INSERT_DELETE

    private fun ordered(a: Char, b: Char): String =
        if (a <= b) "$a\u0000$b" else "$b\u0000$a"
}

class ReviewPassageAligner(
    private val okDistance: Float = 0.15f,
    private val unsureDistance: Float = 0.40f,
    private val minimumMargin: Float = 0.35f,
    private val minimumHeardFraction: Float = 0.34f,
) {
    init {
        require(okDistance in 0f..1f)
        require(unsureDistance in okDistance..1f)
        require(minimumMargin in 0f..1f)
        require(minimumHeardFraction in 0f..1f)
    }

    fun align(
        passage: ReviewExpectedPassage,
        heard: List<ReviewHeardChar>,
        final: Boolean,
    ): ReviewPassageVerdict {
        passage.validated()
        heard.forEach { it.validated() }

        if (heard.isEmpty()) {
            return ReviewPassageVerdict(
                ayahs = passage.ayahs.map { ayah ->
                    ReviewAyahVerdict(
                        ayah.surah,
                        ayah.ayah,
                        ayah.words.map { word ->
                            ReviewWordVerdict(
                                ayah.surah,
                                ayah.ayah,
                                word.index,
                                word.glyph,
                                ReviewWordState.EMPTY,
                                0f,
                                0f,
                                0f,
                            )
                        },
                    )
                },
                heardCharacters = 0,
                final = final,
            )
        }

        val words = passage.words
        val expected = buildString {
            words.forEach { append(it.phonemes) }
        }
        require(expected.isNotEmpty() && expected.length <= 5_000)
        require(heard.size <= MAX_HEARD_CHARS)

        val expectedWord = IntArray(expected.length)
        var expectedOffset = 0
        words.forEachIndexed { wordIndex, word ->
            repeat(word.phonemes.length) {
                expectedWord[expectedOffset++] = wordIndex
            }
        }

        val trace = globalTrace(heard, expected, final)
        val wordCosts = FloatArray(words.size)
        val wordHeard = IntArray(words.size)
        val wordMarginSum = FloatArray(words.size)
        val wordMarginCount = IntArray(words.size)
        val touched = BooleanArray(words.size)
        var furthestExpected = -1

        trace.forEach { step ->
            val expectedIndex = step.expectedIndex
            val heardIndex = step.heardIndex
            when {
                expectedIndex >= 0 -> {
                    val wi = expectedWord[expectedIndex]
                    touched[wi] = true
                    furthestExpected = max(furthestExpected, expectedIndex)
                    wordCosts[wi] += step.cost
                    if (heardIndex >= 0) {
                        wordHeard[wi]++
                        wordMarginSum[wi] += heard[heardIndex].margin
                        wordMarginCount[wi]++
                    }
                }
                heardIndex >= 0 -> {
                    val anchor = insertionWord(
                        step.previousExpectedIndex,
                        expectedWord,
                        words.size,
                    )
                    wordCosts[anchor] += step.cost
                    wordHeard[anchor]++
                    wordMarginSum[anchor] += heard[heardIndex].margin
                    wordMarginCount[anchor]++
                    touched[anchor] = true
                }
            }
        }

        val furthestWord = if (furthestExpected >= 0) {
            expectedWord[furthestExpected]
        } else {
            -1
        }

        val verdicts = words.mapIndexed { index, word ->
            val expectedLength = word.phonemes.length.coerceAtLeast(1)
            val heardCount = wordHeard[index]
            val ratio = heardCount.toFloat() / expectedLength
            val margin = if (wordMarginCount[index] > 0) {
                wordMarginSum[index] / wordMarginCount[index]
            } else {
                0f
            }
            val distance = (
                wordCosts[index] /
                    max(expectedLength, heardCount).coerceAtLeast(1)
                ).coerceIn(0f, 10f)

            val state = when {
                !touched[index] && index > furthestWord && !final ->
                    ReviewWordState.PENDING
                heardCount == 0 && index > furthestWord ->
                    if (final) ReviewWordState.UNCERTAIN
                    else ReviewWordState.PENDING
                heardCount < minimumHeardFraction * expectedLength &&
                    index > 0 &&
                    index < words.lastIndex &&
                    index < furthestWord ->
                    ReviewWordState.SKIPPED
                margin < minimumMargin ->
                    ReviewWordState.UNCERTAIN
                distance <= okDistance &&
                    ratio in 0.55f..1.60f ->
                    ReviewWordState.CONFIRMED
                distance <= unsureDistance ->
                    ReviewWordState.UNCERTAIN
                else -> ReviewWordState.WRONG
            }

            ReviewWordVerdict(
                surah = word.surah,
                ayah = word.ayah,
                word = word.word,
                glyph = word.glyph,
                state = state,
                distance = distance,
                heardRatio = ratio.coerceIn(0f, 10f),
                margin = margin,
            ).validated()
        }

        return ReviewPassageVerdict(
            ayahs = passage.ayahs.map { ayah ->
                ReviewAyahVerdict(
                    surah = ayah.surah,
                    ayah = ayah.ayah,
                    words = verdicts.filter {
                        it.surah == ayah.surah &&
                            it.ayah == ayah.ayah
                    },
                )
            },
            heardCharacters = heard.size,
            final = final,
        )
    }

    private data class TraceStep(
        val heardIndex: Int,
        val expectedIndex: Int,
        val previousExpectedIndex: Int,
        val cost: Float,
    )

    private fun globalTrace(
        heard: List<ReviewHeardChar>,
        expected: String,
        final: Boolean,
    ): List<TraceStep> {
        val n = heard.size
        val m = expected.length
        val columns = m + 1
        require((n + 1L) * columns <= MAX_DP_CELLS)

        val costs = FloatArray((n + 1) * columns)
        val trace = ByteArray((n + 1) * columns)
        for (j in 0..m) costs[j] = j.toFloat()
        for (i in 1..n) costs[i * columns] = i.toFloat()

        for (i in 1..n) {
            val row = i * columns
            val previous = (i - 1) * columns
            val heardCharacter = heard[i - 1].value
            for (j in 1..m) {
                var best =
                    costs[previous + j - 1] +
                        ReviewPhonemeCost.substitution(
                            heardCharacter,
                            expected[j - 1],
                        )
                var direction: Byte = 0

                val insertion =
                    costs[previous + j] +
                        ReviewPhonemeCost.insertionDeletion()
                if (insertion < best) {
                    best = insertion
                    direction = 1
                }

                val deletion =
                    costs[row + j - 1] +
                        ReviewPhonemeCost.insertionDeletion()
                if (deletion < best) {
                    best = deletion
                    direction = 2
                }

                costs[row + j] = best
                trace[row + j] = direction
            }
        }

        var i = n
        var j = if (final) {
            m
        } else {
            val row = n * columns
            var bestColumn = 0
            var bestCost = costs[row]
            for (candidate in 1..m) {
                val value = costs[row + candidate]
                if (
                    value < bestCost ||
                    (value == bestCost && candidate > bestColumn)
                ) {
                    bestCost = value
                    bestColumn = candidate
                }
            }
            bestColumn
        }

        val reverse = ArrayList<TraceStep>(n + j)
        while (i > 0 || j > 0) {
            if (i == 0) {
                reverse += TraceStep(
                    heardIndex = -1,
                    expectedIndex = j - 1,
                    previousExpectedIndex = j - 2,
                    cost = 1f,
                )
                j--
                continue
            }
            if (j == 0) {
                reverse += TraceStep(
                    heardIndex = i - 1,
                    expectedIndex = -1,
                    previousExpectedIndex = -1,
                    cost = 1f,
                )
                i--
                continue
            }

            when (trace[i * columns + j].toInt()) {
                0 -> {
                    reverse += TraceStep(
                        heardIndex = i - 1,
                        expectedIndex = j - 1,
                        previousExpectedIndex = j - 2,
                        cost = ReviewPhonemeCost.substitution(
                            heard[i - 1].value,
                            expected[j - 1],
                        ),
                    )
                    i--
                    j--
                }
                1 -> {
                    reverse += TraceStep(
                        heardIndex = i - 1,
                        expectedIndex = -1,
                        previousExpectedIndex = j - 1,
                        cost = 1f,
                    )
                    i--
                }
                else -> {
                    reverse += TraceStep(
                        heardIndex = -1,
                        expectedIndex = j - 1,
                        previousExpectedIndex = j - 2,
                        cost = 1f,
                    )
                    j--
                }
            }
        }
        reverse.reverse()
        return reverse
    }

    private fun insertionWord(
        previousExpectedIndex: Int,
        expectedWord: IntArray,
        wordCount: Int,
    ): Int {
        if (previousExpectedIndex < 0) return 0
        return expectedWord[
            previousExpectedIndex.coerceAtMost(
                expectedWord.lastIndex,
            )
        ].coerceIn(0, wordCount - 1)
    }

    companion object {
        const val MAX_HEARD_CHARS = 7_500
        private const val MAX_DP_CELLS = 12_000_000L
    }
}
