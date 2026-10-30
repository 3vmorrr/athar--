package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.nightprayer.*
import org.athar.app.quran.QuranIndex
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*
import java.util.UUID

class NightVersePlanTest {
    private val namespace = "123456abcdef"
    private val now = Instant.parse("2026-10-30T04:00:00Z")
    private fun fresh() = NightPlanState(1, namespace)
    private fun state() = fresh().save(7, 12, 0, now)
    @Test fun strictRoundTripBindsOwnerEnvironmentAndCompleteShape() {
        val saved = state(); val raw = NightPlanCodec.encode(saved)
        assertEquals(saved, NightPlanCodec.decode(raw, 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { NightPlanCodec.decode(raw, 2, namespace) }
        assertThrows(IllegalArgumentException::class.java) { NightPlanCodec.decode(raw, 1, "fedcba654321") }
        val root = JsonParser.parseString(raw).asJsonObject
        root.remove("version")
        assertThrows(IllegalArgumentException::class.java) { NightPlanCodec.decode(root.toString(), 1, namespace) }
    }
    @Test fun coercionMissingAndCorruptRangesCannotBypassConstructorChecks() {
        val raw = NightPlanCodec.encode(state())
        for ((key, value) in listOf("start_index" to "0", "end_index" to "6237", "start_index" to "1.5", "end_index" to "\"12\"", "id" to "\"not-a-uuid\"", "updated_at" to "\"invalid\"")) {
            val root = JsonParser.parseString(raw).asJsonObject
            root.getAsJsonObject("plan").add(key, JsonParser.parseString(value))
            assertTrue(key, runCatching { NightPlanCodec.decode(root.toString(), 1, namespace) }.isFailure)
        }
        assertThrows(IllegalArgumentException::class.java) { NightPlanCodec.decode("x".repeat(16385), 1, namespace) }
    }
    @Test fun clearTombstoneAndVersionPreconditionsPreventAbaAndStaleWrites() {
        val saved = state(); val clear = saved.clear(saved.version)
        assertNull(clear.plan); assertEquals(2L, clear.version)
        assertEquals(clear, NightPlanCodec.decode(NightPlanCodec.encode(clear), 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { clear.save(1, 100, saved.version, now) }
        assertThrows(IllegalArgumentException::class.java) { clear.clear(saved.version) }
        val newer = clear.save(1, 100, clear.version, now)
        assertEquals(3L, newer.version); assertNotEquals(saved.plan!!.id, newer.plan!!.id)
    }
    @Test fun unchangedSaveDoesNotInventAnotherVersionOrPlanIdentity() {
        val saved = state()
        assertEquals(saved, saved.save(7, 12, saved.version, now.plusSeconds(60)))
        assertEquals(fresh(), fresh().clear(0))
    }
    @Test fun referencesAreDerivedFromTheCanonicalQuranIndex() {
        val index = QuranIndex.fromJson(File("src/main/assets/quran/index.json").readText())
        val saved = fresh().save(7, 8, 0, now).plan!!
        val range = saved.range(index)
        assertEquals(1, range.start.surah); assertEquals(7, range.start.ayah)
        assertEquals(2, range.end.surah); assertEquals(1, range.end.ayah); assertEquals(2, saved.verseCount)
        assertEquals(6236, index.target(114, 6, 1).end.globalIndex)
        assertThrows(IllegalArgumentException::class.java) { index.target(114, 6, 2) }
    }
    @Test fun immutableReceiptDeduplicatesIntentWithoutRetainingDeletedNoteOrDate() {
        val plan = state().plan!!; val id = UUID.randomUUID().toString()
        val request = NightPlanRecord.create(1, namespace, id, plan, LocalDate.of(2026, 10, 29), ZoneOffset.UTC, "private note canary")
        val receipt = request.receipt(); val raw = NightPlanCodec.encode(receipt)
        assertEquals(receipt, NightPlanCodec.decodeReceipt(raw, 1, namespace))
        assertFalse(raw.contains("private note canary")); assertFalse(raw.contains("2026-10-29")); assertFalse(raw.contains("start_index"))
        assertEquals(receipt, request.copy(body = request.body.deepCopy()).receipt())
        assertNotEquals(receipt, NightPlanRecord.create(1, namespace, id, plan, LocalDate.of(2026, 10, 29), ZoneOffset.UTC, "new note").receipt())
        assertThrows(IllegalArgumentException::class.java) { NightPlanCodec.decodeReceipt(raw, 2, namespace) }
    }
    @Test fun explicitRecordingPayloadValidatesDateRangeAndNoteWithoutChangingPlan() {
        val saved = state(); val plan = saved.plan!!
        val request = NightPlanRecord.create(1, namespace, UUID.randomUUID().toString(), plan, LocalDate.of(2026, 10, 29), ZoneOffset.UTC, "")
        assertEquals("manual", request.body["boundary_source"].asString)
        assertEquals("range", request.body["entry_type"].asString)
        assertEquals(6, request.body["verse_count"].asInt)
        assertEquals(saved, NightPlanCodec.decode(NightPlanCodec.encode(saved), 1, namespace))
        request.body.addProperty("verse_count", 1)
        assertThrows(IllegalArgumentException::class.java) { request.receipt() }
    }
    @Test fun exportAcceptsOlderCopiesButRequiresBoundCompleteUniquePlanSections() {
        val device = com.google.gson.JsonObject().apply {
            addProperty("owner_id", 1); addProperty("namespace", namespace)
            org.athar.app.core.data.AccountExportPolicy.deviceSections.forEach { key -> add(key, if (key == "device_reading_position") com.google.gson.JsonNull.INSTANCE else com.google.gson.JsonArray()) }
        }
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        device.add("device_night_plan", com.google.gson.JsonArray().apply { add(JsonParser.parseString(NightPlanCodec.encode(state()))) })
        assertThrows(IllegalArgumentException::class.java) { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }
        val receipt = NightPlanRecord.create(1, namespace, UUID.randomUUID().toString(), state().plan!!, LocalDate.of(2026, 10, 29), ZoneOffset.UTC, "").receipt()
        device.add("device_night_plan_records", com.google.gson.JsonArray().apply { add(JsonParser.parseString(NightPlanCodec.encode(receipt))) })
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        device.getAsJsonArray("device_night_plan_records").add(device.getAsJsonArray("device_night_plan_records")[0].deepCopy())
        assertThrows(IllegalArgumentException::class.java) { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }
    }
}
