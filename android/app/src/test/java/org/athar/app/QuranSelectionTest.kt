package org.athar.app

import org.athar.app.quran.QuranSelection
import org.junit.Assert.*
import org.junit.Test

class QuranSelectionTest {
    @Test fun savedSurahAndExactAyahRestoreWithoutChangingCanonicalScope() {
        val selection = QuranSelection(number = 2, ayah = 255)
        val restored = QuranSelection.restore(selection.kind, selection.arguments())
        val state = restored.resolve(QuranJvmFixture.data, 17)
        assertEquals(2, state.surah)
        assertEquals(286, state.verses.size)
        assertEquals(262, state.scrollTarget)
        assertEquals(17L, state.scrollRequest)
        assertNull(state.range)
    }

    @Test fun rangeAndCanonicalSectionsSurvivePrimitiveStateRoundTrip() {
        val data = QuranJvmFixture.data
        for ((kind, number) in listOf("page" to 604, "juz" to 2, "hizb" to 60)) {
            val selection = QuranSelection(kind, number)
            val state = QuranSelection.restore(selection.kind, selection.arguments()).resolve(data, 2)
            assertEquals(kind, state.sectionKind)
            assertEquals(number, state.sectionNumber)
            assertEquals(data.index.section(kind, number).first, state.scrollTarget)
            if (kind == "juz") assertEquals(149, state.scrollTarget)
            assertEquals(data.index.section(kind, number).last, state.verses.last().position.globalIndex)
        }
        val selection = QuranSelection("range", 2, 285, 3, 1)
        val state = QuranSelection.restore(selection.kind, selection.arguments()).resolve(data, 3)
        assertEquals(3, state.verses.size)
        assertEquals(2, state.range?.start?.surah)
        assertEquals(3, state.range?.end?.surah)
        assertEquals(data.index.global(2, 285), state.scrollTarget)
    }

    @Test fun missingStateStartsAtFatihaAndMalformedStateFailsClosed() {
        val data = QuranJvmFixture.data
        assertEquals(7, QuranSelection.restore(null, null).resolve(data, 1).verses.size)
        assertTrue(runCatching { QuranSelection.restore("unknown", intArrayOf(1, 1, 1, 1)) }.isFailure)
        assertTrue(runCatching { QuranSelection.restore("surah", intArrayOf(1)) }.isFailure)
        assertTrue(runCatching { QuranSelection("surah", 114, 7).resolve(data, 1) }.isFailure)
        assertTrue(runCatching { QuranSelection("range", 3, 1, 2, 285).resolve(data, 1) }.isFailure)
    }
}
