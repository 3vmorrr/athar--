package org.athar.app

import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import com.google.gson.JsonParser
import com.google.gson.JsonObject

class NightIdentityTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val date = LocalDate.of(2026, 10, 29)
    private fun night(day: LocalDate) = NightContext(day, zone,
        day.atTime(18, 0).atZone(zone).toInstant(), day.plusDays(1).atTime(5, 0).atZone(zone).toInstant())
    @Test fun maghribIsInclusiveAndMidnightKeepsTheOriginalDate() {
        val first = night(date)
        for (now in listOf(first.maghrib, date.plusDays(1).atStartOfDay(zone).toInstant(), first.fajr.minusMillis(1))) {
            val result = resolveNightIdentity(now, zone, ::night)
            assertTrue(result.inProgress); assertEquals(date, result.nightDate)
        }
    }
    @Test fun fajrIsExclusiveAndDaytimeUsesTheUpcomingMaghribDate() {
        val first = night(date)
        for (now in listOf(first.fajr, first.fajr.plusSeconds(6 * 3600))) {
            val result = resolveNightIdentity(now, zone, ::night)
            assertFalse(result.inProgress); assertEquals(date.plusDays(1), result.nightDate)
        }
    }
    @Test fun refreshTargetsTheNextBoundaryWithABoundedClockSanityCheck() {
        val context = night(date); val now = context.maghrib.minusMillis(450)
        val result = resolveNightIdentity(now, zone, ::night)
        assertEquals(450L, nightRefreshDelay(result, now))
        assertEquals(30_000L, nightRefreshDelay(result, now.minusSeconds(600)))
        assertEquals(30_000L, nightRefreshDelay(null, now))
        assertEquals(1L, nightRefreshDelay(result, context.maghrib.minusNanos(1)))
    }
    @Test fun unavailableAdjacentDateCannotEraseAKnownCurrentNight() {
        val current = night(date)
        val beforeFajr = current.fajr.minusSeconds(1)
        val result = resolveNightIdentity(beforeFajr, zone) { day -> require(day == date); current }
        assertTrue(result.inProgress); assertEquals(date, result.nightDate)
        val daytime = date.atTime(12, 0).atZone(zone).toInstant()
        assertFalse(resolveNightIdentity(daytime, zone) { day -> require(day == date); current }.inProgress)
    }
    @Test fun unavailableBoundariesAreNeverGuessedFromCivilDate() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveNightIdentity(date.atStartOfDay(zone).toInstant(), zone) { errorDate -> throw IllegalArgumentException(errorDate.toString()) }
        }
    }
    @Test fun recalculationAfterClockOrTimezoneChangeDoesNotReuseAnOldNight() {
        val before = resolveNightIdentity(night(date).maghrib, zone, ::night)
        val after = resolveNightIdentity(night(date.plusDays(2)).maghrib, zone, ::night)
        assertNotEquals(before.nightDate, after.nightDate)
        val utc = ZoneOffset.UTC
        val instant = Instant.parse("2026-10-30T00:30:00Z")
        val result = resolveNightIdentity(instant, utc) { day -> NightContext(day, utc, day.atTime(18, 0).toInstant(utc), day.plusDays(1).atTime(5, 0).toInstant(utc)) }
        assertEquals(LocalDate.of(2026, 10, 29), result.nightDate)
    }
    private fun settings() = JsonParser.parseString("""{"timezone":"Africa/Cairo","latitude":30.0444,"longitude":31.2357,"calculation_method":"EGYPTIAN","madhab":"SHAFI","high_latitude_rule":"MIDDLE_OF_THE_NIGHT"}""").asJsonObject
    @Test fun savedSettingsUseTheChosenZoneAndNeverSupplyMissingCoordinates() {
        val parsed = NightSavedSettings.parse(settings())
        assertEquals(zone, parsed.zone); assertEquals(30.0444, parsed.latitude, 0.00001)
        assertThrows(IllegalArgumentException::class.java) { NightSavedSettings.parse(JsonObject()) }
        val missing = settings().apply { remove("latitude") }
        assertThrows(IllegalArgumentException::class.java) { NightSavedSettings.parse(missing) }
    }
    @Test fun malformedOrUnsupportedSavedSettingsAreNotCoercedIntoAValidCity() {
        for (invalid in listOf(settings().apply { addProperty("latitude", "30.0") }, settings().apply { addProperty("longitude", 181) }, settings().apply { addProperty("latitude", Double.NaN) }, settings().apply { addProperty("calculation_method", "UNKNOWN") }, settings().apply { addProperty("madhab", "unknown") })) {
            assertThrows(IllegalArgumentException::class.java) { NightSavedSettings.parse(invalid) }
        }
    }
}
