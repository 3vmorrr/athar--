package org.athar.app

import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewPassageAlignmentTest {
    private fun passage(vararg phonemes: String): ReviewExpectedPassage {
        val words = phonemes.mapIndexed { index, value ->
            ReviewExpectedWord(
                index = index,
                glyph = "كلمة" + (index + 1),
                phonemes = value,
                plain = "word" + (index + 1),
            )
        }
        return ReviewExpectedPassage(
            surah = 1,
            ayahStart = 1,
            ayahEnd = 1,
            ayahs = listOf(
                ReviewExpectedAyah(
                    surah = 1,
                    ayah = 1,
                    marker = "",
                    words = words,
                ),
            ),
        ).validated()
    }

    private fun heard(
        value: String,
        margin: Float = 0.9f,
    ): List<ReviewHeardChar> = value.mapIndexed { index, ch ->
        ReviewHeardChar(
            value = ch,
            frame = index,
            margin = margin,
        )
    }

    @Test fun exactExpectedPassageConfirmsClearWords() {
        val result = ReviewPassageAligner().align(
            passage("بَ", "سِ", "مِ"),
            heard("بَسِمِ"),
            final = true,
        )
        assertEquals(
            listOf(
                ReviewWordState.CONFIRMED,
                ReviewWordState.CONFIRMED,
                ReviewWordState.CONFIRMED,
            ),
            result.ayahs.single().words.map { it.state },
        )
        assertFalse(result.hasProblem)
        assertFalse(result.hasUncertainty)
    }

    @Test fun interiorDeletionBecomesSkippedInsteadOfFalseCorrect() {
        val result = ReviewPassageAligner().align(
            passage("با", "تم", "نو"),
            heard("بانو"),
            final = true,
        )
        assertEquals(
            ReviewWordState.SKIPPED,
            result.ayahs.single().words[1].state,
        )
        assertTrue(result.hasProblem)
    }

    @Test fun substitutionWithClearAudioBecomesWrong() {
        val result = ReviewPassageAligner().align(
            passage("سس", "بب", "نن"),
            heard("سسددنن"),
            final = true,
        )
        assertEquals(
            ReviewWordState.WRONG,
            result.ayahs.single().words[1].state,
        )
    }

    @Test fun insertionAndRepeatedWordDoNotDisappear() {
        val inserted = ReviewPassageAligner().align(
            passage("با", "تم", "نو"),
            heard("باخختمنو"),
            final = true,
        )
        assertNotEquals(
            ReviewWordState.CONFIRMED,
            inserted.ayahs.single().words[0].state,
        )

        val repeated = ReviewPassageAligner().align(
            passage("با", "تم", "نو"),
            heard("باتمتمنو"),
            final = true,
        )
        assertTrue(
            repeated.ayahs.single().words.any {
                it.state == ReviewWordState.WRONG ||
                    it.state == ReviewWordState.UNCERTAIN
            },
        )
    }

    @Test fun lowMarginNoisyAudioAbstainsAsUncertain() {
        val result = ReviewPassageAligner().align(
            passage("بَ", "سِ", "مِ"),
            heard("بَسِمِ", margin = 0.1f),
            final = true,
        )
        assertTrue(
            result.ayahs.single().words.all {
                it.state == ReviewWordState.UNCERTAIN
            },
        )
        assertFalse(result.hasProblem)
        assertTrue(result.hasUncertainty)
    }

    @Test fun futureWordsRemainPendingUntilFinalization() {
        val result = ReviewPassageAligner().align(
            passage("با", "تم", "نو"),
            heard("با"),
            final = false,
        )
        assertEquals(
            ReviewWordState.CONFIRMED,
            result.ayahs.single().words[0].state,
        )
        assertEquals(
            ReviewWordState.PENDING,
            result.ayahs.single().words[1].state,
        )
        assertEquals(
            ReviewWordState.PENDING,
            result.ayahs.single().words[2].state,
        )
    }

    @Test fun noAudioKeepsEmptySlotsEvenAtStop() {
        val result = ReviewPassageAligner().align(
            passage("با", "تم"),
            emptyList(),
            final = true,
        )
        assertTrue(
            result.ayahs.single().words.all {
                it.state == ReviewWordState.EMPTY
            },
        )
    }

    @Test fun disjointMultiCharacterCtcTokensExpandAtSameFrame() {
        val tokens = listOf(
            ReviewCtcToken(
                id = 46,
                frame = 7,
                margin = 0.8f,
            ),
        )
        assertEquals("بَ", ReviewCtcVocabulary.symbol(46))
        val expanded = ReviewCtcExpansion.expand(tokens)
        assertEquals(listOf('ب', 'َ'), expanded.map { it.value })
        assertEquals(listOf(7, 7), expanded.map { it.frame })
        assertEquals(listOf(0.8f, 0.8f), expanded.map { it.margin })
    }

    @Test fun phoneticNeighborsAreCheaperThanUnrelatedSubstitution() {
        assertEquals(
            0.25f,
            ReviewPhonemeCost.substitution('ق', 'ك'),
            0.0001f,
        )
        assertEquals(
            0.1f,
            ReviewPhonemeCost.substitution('أ', 'ا'),
            0.0001f,
        )
        assertEquals(
            1f,
            ReviewPhonemeCost.substitution('ب', 'س'),
            0.0001f,
        )
    }
}
