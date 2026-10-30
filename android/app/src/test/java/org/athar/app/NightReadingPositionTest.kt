package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class NightReadingPositionTest {
    private val namespace = "123456abcdef"
    private val id = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-30T06:00:00Z")
    private fun scope(start: Int = 7, end: Int = 100) = NightReadingScope(1, namespace, "plan", id, start, end)
    @Test fun strictPositionRoundTripBindsActorEnvironmentContextAndRange() {
        val value = NightReadingPosition(scope()).save(8, 0, now)
        val raw = NightReadingPositionCodec.encode(value)
        assertEquals(value, NightReadingPositionCodec.decode(raw, 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { NightReadingPositionCodec.decode(raw, 2, namespace) }
        assertThrows(IllegalArgumentException::class.java) { NightReadingPositionCodec.decode(raw, 1, "fedcba654321") }
        assertNotEquals(value.scope, value.scope.copy(kind = "session"))
        assertNotEquals(value.scope.key, value.scope.copy(kind = "session").key)
    }
    @Test fun positionNeverEscapesTheExactInclusiveRange() {
        val fresh = NightReadingPosition(scope())
        assertEquals(7, fresh.save(7, 0, now).index)
        assertEquals(100, fresh.save(100, 0, now).index)
        for (position in listOf(6, 101, 0, 6237)) assertThrows(IllegalArgumentException::class.java) { fresh.save(position, 0, now) }
    }
    @Test fun clearingAnAbsentMarkerStillFencesAnInFlightFirstSave() {
        val fresh = NightReadingPosition(scope()); val cleared = fresh.clear(0, now)
        assertNull(cleared.index); assertEquals(1L, cleared.version)
        assertThrows(IllegalArgumentException::class.java) { cleared.save(10, 0, now) }
        val next = cleared.save(10, 1, now); assertEquals(2L, next.version)
        assertEquals(next, next.save(10, 2, now.plusSeconds(10)))
    }
    @Test fun malformedCoercedOrIncompleteMarkersCannotBecomeAnEmptyPosition() {
        val raw = NightReadingPositionCodec.encode(NightReadingPosition(scope()).save(8, 0, now))
        for ((key, value) in listOf("position" to "6", "position" to "101", "position" to "8.5", "position" to "\"8\"", "version" to "\"1\"", "kind" to "\"quran\"", "schema" to "2")) {
            val root = JsonParser.parseString(raw).asJsonObject; root.add(key, JsonParser.parseString(value))
            assertTrue(key, runCatching { NightReadingPositionCodec.decode(root.toString(), 1, namespace) }.isFailure)
        }
        val missing = JsonParser.parseString(raw).asJsonObject.apply { remove("position") }
        assertTrue(runCatching { NightReadingPositionCodec.decode(missing.toString(), 1, namespace) }.isFailure)
        assertThrows(IllegalArgumentException::class.java) { NightReadingPositionCodec.decode("x".repeat(4097), 1, namespace) }
    }
    @Test fun sessionIdsMatchTheExistingApiUuidVersionsWithoutWeakeningNewPlanIds() {
        val versionOne = "12345678-1234-1234-8234-123456789abc"
        NightReadingScope(1, namespace, "session", versionOne, 1, 7)
        assertThrows(IllegalArgumentException::class.java) { NightReadingScope(1, namespace, "plan", versionOne, 1, 7) }
    }
    private fun coverage(scope: NightReadingScope, range: IntRange) = NightPlanCoverage(scope.owner, scope.namespace, scope.id, UUID.randomUUID().toString(), range)
    @Test fun olderOrForeignPlanHistoryCannotAdvanceThisPlan() {
        val current = scope(1, 100)
        val unrelated = listOf(coverage(current, 1..100).copy(planId = UUID.randomUUID().toString()), coverage(current, 1..100).copy(owner = 2), coverage(current, 1..100).copy(namespace = "fedcba654321"))
        assertEquals(1, firstUncoveredNightIndex(current, unrelated))
        assertEquals(1, firstUncoveredNightIndex(current, emptyList()))
    }
    @Test fun correlatedRangesMergeOverlapButNeverSkipAnEarlierGap() {
        val current = scope(1, 100)
        val records = listOf(coverage(current, 50..100), coverage(current, 1..10), coverage(current, 8..15))
        assertEquals(16, firstUncoveredNightIndex(current, records))
        assertNull(firstUncoveredNightIndex(current, records + coverage(current, 16..49)))
    }
    @Test fun coverageIsClampedToThisRangeAndSessionScopeCannotUsePlanHistory() {
        val current = scope(10, 20)
        assertEquals(13, firstUncoveredNightIndex(current, listOf(coverage(current, 1..12), coverage(current, 15..100))))
        assertNull(firstUncoveredNightIndex(current, listOf(coverage(current, 1..100))))
        assertThrows(IllegalArgumentException::class.java) { firstUncoveredNightIndex(current.copy(kind = "session"), emptyList()) }
    }
    @Test fun ownedExportPreservesLegacyCopiesAndRejectsForeignOrDuplicatePrayerPositions() {
        val device = com.google.gson.JsonObject().apply {
            addProperty("owner_id", 1); addProperty("namespace", namespace)
            org.athar.app.core.data.AccountExportPolicy.deviceSections.forEach { key -> add(key, if (key == "device_reading_position") com.google.gson.JsonNull.INSTANCE else com.google.gson.JsonArray()) }
        }
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        val marker = NightReadingPosition(scope()).save(8, 0, now)
        device.add("device_night_reading_positions", com.google.gson.JsonArray().apply { add(JsonParser.parseString(NightReadingPositionCodec.encode(marker))) })
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        val entries = device.getAsJsonArray("device_night_reading_positions"); entries.add(entries[0].deepCopy())
        assertThrows(IllegalArgumentException::class.java) { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }
        entries.remove(1); entries[0].asJsonObject.addProperty("owner", 2)
        assertThrows(IllegalArgumentException::class.java) { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }
    }
}
