package org.athar.app

import kotlinx.coroutines.runBlocking
import org.athar.app.quran.QuranSearchNormalizer
import org.junit.Assert.*
import org.junit.Test

class QuranSearchPolicyTest {
    private fun data() = QuranJvmFixture.data

    @Test fun normalizationHandlesUthmaniMarksAndCommonArabicVariants() {
        assertEquals("انا اعطيناك", QuranSearchNormalizer.normalize(" إِنَّا   أَعْطَيْنَاكَ "))
        assertEquals("رحمه", QuranSearchNormalizer.normalize("رَحْمَة"))
        assertEquals("مومن", QuranSearchNormalizer.normalize("مُؤْمِن"))
        assertEquals("شي", QuranSearchNormalizer.normalize("شِئ"))
    }

    @Test fun pagedSearchPreservesCanonicalTextAndStableOrdering() = runBlocking {
        val data = data()
        val first = data.searchPage("الله", 0, 20)
        assertTrue(first.total > 20)
        assertEquals(20, first.hits.size)
        assertTrue(first.hasNext)
        assertFalse(first.hasPrevious)
        val canonical = first.hits.first().ayah.text
        assertTrue(first.hits.first().highlightRanges.isNotEmpty())
        assertEquals(canonical, first.hits.first().ayah.text)

        val second = data.searchPage("الله", 20, 20)
        assertTrue(second.hasPrevious)
        assertTrue(
            first.hits.map { it.ayah.position.globalIndex }.toSet()
                .intersect(second.hits.map { it.ayah.position.globalIndex }.toSet())
                .isEmpty(),
        )
        assertTrue(second.hits.first().ayah.position.globalIndex > first.hits.first().ayah.position.globalIndex)
    }

    @Test fun normalizedPhraseMapsHighlightBackToCanonicalAyah() = runBlocking {
        val data = data()
        val page = data.searchPage("انا اعطيناك", 0, 20)
        val hit = page.hits.firstOrNull {
            it.ayah.position.surah == 108 && it.ayah.position.ayah == 1
        }
        assertNotNull(hit)
        val match = requireNotNull(hit)
        assertTrue(match.highlightRanges.isNotEmpty())
        assertEquals("إِنَّآ أَعْطَيْنَـٰكَ ٱلْكَوْثَرَ", match.ayah.text)
        val canonicalSlice = match.ayah.text.substring(
            match.highlightRanges.first().first,
            match.highlightRanges.first().last + 1,
        )
        assertEquals(
            "انا اعطينك",
            QuranSearchNormalizer.normalize(canonicalSlice),
        )
    }

    @Test fun daggerAlifAcceptsWrittenAndOmittedFormsWithoutChangingTheSource() = runBlocking {
        val data = data()
        for (query in listOf("انا اعطيناك", "انا اعطينك")) {
            val hit = data.searchPage(query).hits.single { it.ayah.position.surah == 108 && it.ayah.position.ayah == 1 }
            val range = hit.highlightRanges.single()
            assertEquals("إِنَّآ أَعْطَيْنَـٰكَ", hit.ayah.text.substring(range.first, range.last + 1))
        }
        for (query in listOf("ملك يوم الدين", "مالك يوم الدين")) {
            assertTrue(data.searchPage(query).hits.any { it.ayah.position.surah == 1 && it.ayah.position.ayah == 4 })
        }
        assertTrue(data.searchPage("موسى").total > 0)
    }

    @Test fun invalidAndBoundaryPagingFailClosed() = runBlocking {
        val data = data()
        assertTrue(runCatching { data.searchPage(" َ ", 0, 20) }.isFailure)
        assertTrue(runCatching { data.searchPage("الله", -1, 20) }.isFailure)
        assertTrue(runCatching { data.searchPage("الله", 0, 0) }.isFailure)
        assertTrue(runCatching { data.searchPage("الله", 0, 51) }.isFailure)

        val first = data.searchPage("الله", 0, 20)
        val lastOffset = ((first.total - 1) / 20) * 20
        val last = data.searchPage("الله", lastOffset, 20)
        assertFalse(last.hasNext)
        assertTrue(last.hits.size in 1..20)
    }
}
