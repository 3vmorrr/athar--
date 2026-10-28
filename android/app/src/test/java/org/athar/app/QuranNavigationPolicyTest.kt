package org.athar.app

import kotlinx.coroutines.runBlocking
import org.athar.app.quran.QuranReferenceInput
import org.junit.Assert.*
import org.junit.Test

class QuranNavigationPolicyTest {
    private fun data() = QuranJvmFixture.data

    @Test fun canonicalBoundariesAndSectionsResolveExactVerses() = runBlocking {
        val data = data()
        val index = data.index
        assertEquals(1, index.global(1, 1))
        assertEquals(6236, index.global(114, 6))
        assertEquals(6236, index.range(1, 1, 114, 6).verseCount)
        assertEquals(604, index.sectionCount("page"))
        assertEquals(30, index.sectionCount("juz"))
        assertEquals(60, index.sectionCount("hizb"))
        val secondJuz = data.section("juz", 2)
        assertEquals(2, secondJuz.first().position.surah)
        assertEquals(142, secondJuz.first().position.ayah)
        val secondPage = data.section("page", 2)
        assertEquals(2, secondPage.first().position.surah)
        assertEquals(1, secondPage.first().position.ayah)
        assertEquals(6236, data.section("page", 604).last().position.globalIndex)
        val exact = data.range(index.range(2, 285, 3, 1))
        assertEquals(index.global(2, 285), exact.first().position.globalIndex)
        assertEquals(index.global(3, 1), exact.last().position.globalIndex)
    }

    @Test fun invalidReferencesFailClosedWithoutWrapping() = runBlocking {
        val index = data().index
        assertTrue(runCatching { index.global(0, 1) }.isFailure)
        assertTrue(runCatching { index.global(115, 1) }.isFailure)
        assertTrue(runCatching { index.global(2, 287) }.isFailure)
        assertTrue(runCatching { index.range(2, 2, 2, 1) }.isFailure)
        assertTrue(runCatching { index.section("page", 605) }.isFailure)
        assertTrue(runCatching { index.section("juz", 31) }.isFailure)
        assertTrue(runCatching { index.section("hizb", 61) }.isFailure)
        assertTrue(runCatching { index.section("quarter", 1) }.isFailure)
    }

    @Test fun mixedArabicAndWesternDigitsNormalizeBeforeValidation() = runBlocking {
        val index = data().index
        assertEquals("123", QuranReferenceInput.canonicalizeDigits("١2۳"))
        assertEquals(604, QuranReferenceInput.number("۶٠4", 1..604))
        assertNull(QuranReferenceInput.canonicalizeDigits("١ ٢"))
        assertNull(QuranReferenceInput.canonicalizeDigits("12a"))
        assertNull(QuranReferenceInput.number("٦٠٥", 1..604))
        assertEquals(286, QuranReferenceInput.position(index, "٢", "٢٨٦")?.ayah)
        assertNull(QuranReferenceInput.position(index, "٢", "٢٨٧"))
        val range = QuranReferenceInput.range(index, "٢", "٢٨٥", "٣", "١")
        assertEquals(index.global(2, 285), range?.start?.globalIndex)
        assertEquals(index.global(3, 1), range?.end?.globalIndex)
        assertNull(QuranReferenceInput.range(index, "٣", "١", "٢", "٢٨٥"))
    }
}
