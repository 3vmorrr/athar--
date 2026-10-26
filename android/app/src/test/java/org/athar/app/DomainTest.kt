package org.athar.app

import org.junit.Assert.*
import org.junit.Test
import org.athar.app.quran.QuranIndex
import org.athar.app.nightprayer.*
import java.io.File
import java.time.*

class DomainTest {
    @Test fun calendarUsesRealDailyTotalsAndKeepsExemptionsSeparate() {
        val month = java.time.YearMonth.of(2028, 2)
        val first = month.atDay(1); val second = month.atDay(2)
        val days = org.athar.app.nightprayer.NightCalendar.month(month, mapOf(first to 100, month.minusMonths(1).atDay(1) to 50), setOf(second))
        assertEquals(29, days.size)
        assertEquals(100, days.sumOf { it.verseCount })
        assertEquals(1, days.count { it.exempt })
        assertEquals(0, days[1].verseCount)
        assertEquals("UTC", NightRules.serverTimezone(ZoneId.of("GMT")))
        assertEquals("Africa/Cairo", NightRules.serverTimezone(ZoneId.of("Africa/Cairo")))
    }
    @Test fun searchNormalizationDoesNotChangeOriginalQuranText() {
        val original = "ٱلْحَمْدُ لِلَّهِ"
        assertEquals("الحمد لله", org.athar.app.quran.QuranData.normalize(original))
        assertEquals("ٱلْحَمْدُ لِلَّهِ", original)
        assertEquals("الي", org.athar.app.quran.QuranData.normalize("  إِلَى  "))
    }
    private fun index() = QuranIndex.fromJson(File("src/main/assets/quran/index.json").readText())
    @Test fun everyCanonicalVerseRoundTrips() {
        val index = index()
        for (global in 1..6236) { val position = index.position(global); assertEquals(global, index.global(position.surah, position.ayah)) }
        assertEquals(6, index.surahs.last().ayahCount)
    }
    @Test fun rangesAreInclusiveAndNeverWrap() {
        val index = index()
        assertEquals(7, index.range(1, 1, 1, 7).verseCount)
        assertEquals(2, index.target(1, 7, 2).end.surah)
        assertEquals(1, index.target(114, 6, 1).verseCount)
        assertThrows(IllegalArgumentException::class.java) { index.target(114, 6, 2) }
        assertThrows(IllegalArgumentException::class.java) { index.range(2, 1, 1, 1) }
    }
    @Test fun sectionsCoverWholeQuranWithoutGaps() {
        val index = index()
        for ((kind, count) in listOf("page" to 604, "juz" to 30, "hizb" to 60)) {
            var next = 1
            for (number in 1..count) { val section = index.section(kind, number); assertEquals(next, section.first); assertTrue(section.last >= section.first); next = section.last + 1 }
            assertEquals(6237, next)
        }
    }
    @Test fun lastThirdUsesElapsedInstantsAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val maghrib = ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, zone).toInstant()
        val fajr = ZonedDateTime.of(2026, 11, 1, 5, 0, 0, 0, zone).toInstant()
        val context = NightContext(LocalDate.of(2026, 10, 31), zone, maghrib, fajr)
        assertEquals(12 * 3600L, Duration.between(maghrib, fajr).seconds)
        assertEquals(8 * 3600L, Duration.between(maghrib, context.lastThird).seconds)
        assertTrue(context.contains(context.lastThird)); assertFalse(context.contains(fajr))
    }
    @Test fun measuredPaceIsWeightedAndExemptionsNeverAddNights() {
        assertEquals(3.0, NightRules.pace(listOf(10 to 10, 20 to 80))!!, 0.00001)
        assertNull(NightRules.pace(listOf(0 to 100, 10 to 0)))
        val today = LocalDate.of(2026, 10, 25)
        val totals = mapOf(today to 100, today.minusDays(2) to 100)
        assertEquals(1 to 1, NightRules.streak(totals, 100, today, true))
        assertEquals(2 to 2, NightRules.streak(totals, 100, today, true, setOf(today.minusDays(1))))
        assertEquals(2 to 2, NightRules.streak(totals, 100, today.plusDays(1), true, setOf(today.minusDays(1))))
    }
    @Test fun astronomicalNightUsesNextDayFajrForEverySupportedMethod() {
        val date = LocalDate.of(2026, 10, 25); val zone = ZoneId.of("Africa/Cairo")
        for (method in PrayerCalculator.methods) {
            val context = PrayerCalculator.calculate(date, 30.0444, 31.2357, zone, method)
            assertEquals(date, context.maghrib.atZone(zone).toLocalDate())
            assertEquals(date.plusDays(1), context.fajr.atZone(zone).toLocalDate())
            assertTrue(context.lastThird > context.maghrib && context.lastThird < context.fajr)
            assertTrue(Duration.between(context.maghrib, context.fajr).toHours() in 8..16)
        }
        assertThrows(IllegalArgumentException::class.java) { PrayerCalculator.calculate(date, Double.NaN, 31.2, zone, "EGYPTIAN") }
        assertThrows(IllegalArgumentException::class.java) { PrayerCalculator.calculate(date, 30.0, 31.2, zone, "UNKNOWN") }
    }
}
