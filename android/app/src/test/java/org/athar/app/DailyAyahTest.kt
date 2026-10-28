package org.athar.app

import com.google.gson.JsonObject
import java.time.LocalDate
import org.athar.app.quran.DailyAyah
import org.junit.Assert.*
import org.junit.Test

class DailyAyahTest {
    @Test fun datedSelectionMatchesTheExistingPhpApiIncludingLeapDay() {
        // Values captured from QuranText::standard()->daily on the integration API.
        for ((date, expected) in mapOf("2026-10-28" to 3791, "2026-10-29" to 6021,
            "2028-02-29" to 2556, "2000-01-01" to 4813)) {
            val day = LocalDate.parse(date)
            assertEquals(expected, DailyAyah.globalIndex(day))
            assertEquals(expected, DailyAyah.local(QuranJvmFixture.data, day).position.globalIndex)
        }
    }

    private fun payload(date: LocalDate): JsonObject {
        val verse = DailyAyah.local(QuranJvmFixture.data, date)
        return JsonObject().apply {
            addProperty("date", date.toString())
            add("source", JsonObject().apply {
                addProperty("url", "https://tanzil.net/")
                addProperty("version", "1.1")
            })
            add("ayah", JsonObject().apply {
                addProperty("global_index", verse.position.globalIndex)
                addProperty("surah", verse.position.surah)
                addProperty("ayah", verse.position.ayah)
                addProperty("text", verse.text)
            })
        }
    }

    @Test fun verifiedApiPointerReturnsOnlyTheBundledCanonicalVerse() {
        val date = LocalDate.of(2026, 10, 28)
        assertSame(DailyAyah.local(QuranJvmFixture.data, date), DailyAyah.verified(QuranJvmFixture.data, date, payload(date)))
    }

    @Test fun staleDateWrongSourceAndAlteredTextFailClosed() {
        val date = LocalDate.of(2026, 10, 28)
        val stale = payload(date.minusDays(1))
        val source = payload(date).apply { getAsJsonObject("source").addProperty("url", "https://invalid.example/") }
        val text = payload(date).apply { getAsJsonObject("ayah").addProperty("text", "altered") }
        val reference = payload(date).apply { getAsJsonObject("ayah").addProperty("surah", 1) }
        for (invalid in listOf(stale, source, text, reference)) {
            assertTrue(runCatching { DailyAyah.verified(QuranJvmFixture.data, date, invalid) }.isFailure)
        }
    }
}
