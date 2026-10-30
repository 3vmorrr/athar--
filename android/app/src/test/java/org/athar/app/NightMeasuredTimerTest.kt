package org.athar.app

import com.google.gson.*
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NightMeasuredTimerTest {
    private val namespace = "123456abcdef"
    private val start = Instant.parse("2026-10-29T23:59:50Z")
    private val context = NightContext(LocalDate.parse("2026-10-29"), ZoneOffset.UTC,
        Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-30T05:00:00Z"))
    private fun timer() = NightMeasuredTimer.start(NightIdentity(context.date, true, context), "EGYPTIAN", start)
    private fun entry() = JsonParser.parseString("""{"entry_type":"range","start_index":7,"end_index":8,"verse_count":2,"note":"fixture private note"}""").asJsonObject
    @Test fun startKeepsTheMaghribDateAcrossCivilMidnight() {
        val timer = NightMeasuredTimer.start(NightIdentity(context.date, true, context), "EGYPTIAN", start.plusSeconds(20))
        assertEquals("2026-10-29", timer.date); assertEquals("UTC", timer.zone); assertEquals(0L, timer.millis)
        assertThrows(IllegalArgumentException::class.java) { NightMeasuredTimer.start(NightIdentity(context.date, true, context), "EGYPTIAN", context.fajr) }
        assertThrows(IllegalArgumentException::class.java) { NightMeasuredTimer.start(NightIdentity(context.date, false, context), "EGYPTIAN", start) }
    }
    @Test fun monotonicForegroundTimeIsMeasuredAndPausedWallTimeIsExcluded() {
        val first = NightTimerAnchor(timer(), 1000, start).sample(6000, start.plusSeconds(5))
        assertEquals(5000L, first.millis)
        val paused = first.copy(phase = "paused", reason = "user")
        val resumed = paused.resume(start.plusSeconds(65))
        val last = NightTimerAnchor(resumed, 100000, start.plusSeconds(65)).sample(103000, start.plusSeconds(68))
        assertEquals(8000L, last.millis); assertEquals(timer().date, last.date)
    }
    @Test fun recoveryPausesAtTheLastVerifiedCheckpointWithoutCountingTheGap() {
        val stored = timer().checkpoint(5000, start.plusSeconds(5))
        val recovered = stored.recover()
        assertEquals(5000L, recovered.millis); assertEquals(stored.activeAt, recovered.activeAt)
        assertEquals("paused", recovered.phase); assertEquals("recovered", recovered.reason)
        assertEquals(recovered, recovered.recover())
    }
    @Test fun crossingFajrCountsOnlyTheRemainingVerifiedInterval() {
        val near = NightMeasuredTimer.start(NightIdentity(context.date, true, context), "EGYPTIAN", context.fajr.minusSeconds(2))
        val sampled = NightTimerAnchor(near, 1000, context.fajr.minusSeconds(2)).sample(6000, context.fajr.plusSeconds(3))
        assertEquals(2000L, sampled.millis); assertEquals(context.fajr.toString(), sampled.activeAt)
        assertEquals("boundary", sampled.reason); assertEquals("paused", sampled.phase)
        assertThrows(IllegalArgumentException::class.java) { sampled.resume(context.fajr.plusSeconds(5)) }
    }
    @Test fun wallClockJumpsAndUptimeResetPauseWithoutInventingElapsedTime() {
        val stored = timer().checkpoint(5000, start.plusSeconds(5)); val anchor = NightTimerAnchor(stored, 10000, start.plusSeconds(5))
        for (now in listOf(start.minusSeconds(60), start.plusSeconds(3600))) {
            val sample = anchor.sample(11000, now)
            assertEquals(stored.millis, sample.millis); assertEquals(stored.activeAt, sample.activeAt); assertEquals("clock", sample.reason)
        }
        val reboot = anchor.sample(100, start.plusSeconds(20))
        assertEquals(5000L, reboot.millis); assertEquals("recovered", reboot.reason)
    }
    @Test fun finishingRequiresRealMeasuredTimeAndPreservesTheVerifiedEndpoint() {
        assertThrows(IllegalArgumentException::class.java) { timer().finish() }
        val finished = timer().checkpoint(5000, start.plusSeconds(5), "background").finish()
        assertEquals(finished.activeAt, finished.ended); assertEquals(5000L, finished.millis)
        assertThrows(IllegalArgumentException::class.java) { finished.resume(start.plusSeconds(10)) }
        assertThrows(IllegalArgumentException::class.java) { finished.checkpoint(6000, start.plusSeconds(6)) }
    }
    @Test fun frameAndReceiptRoundTripsBindOwnerNamespaceAndStrictTypes() {
        val frame = NightTimerFrame(1, namespace).replace(timer().checkpoint(5000, start.plusSeconds(5)).finish())
        val raw = NightTimerCodec.encode(frame)
        assertEquals(frame, NightTimerCodec.decode(raw, 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { NightTimerCodec.decode(raw, 2, namespace) }
        assertThrows(IllegalArgumentException::class.java) { NightTimerCodec.decode(raw, 1, "fedcba654321") }
        val body = frame.timer!!.body(entry()); val receipt = NightTimerCodec.receipt(1, namespace, frame.timer, body)
        assertEquals(receipt, NightTimerCodec.decodeReceipt(NightTimerCodec.encode(receipt), 1, namespace))
        assertFalse(NightTimerCodec.encode(receipt).contains("fixture private note"))
    }
    @Test fun malformedOrCoercedRetainedFramesDoNotBecomeFreshTimers() {
        val raw = NightTimerCodec.encode(NightTimerFrame(1, namespace).replace(timer()))
        for ((key, value) in listOf("millis" to "\"0\"", "millis" to "-1", "millis" to "0.1", "phase" to "\"unknown\"", "ended" to "\"2026-10-30T00:00:00Z\"")) {
            val root = JsonParser.parseString(raw).asJsonObject
            root.getAsJsonObject("timer").add(key, JsonParser.parseString(value))
            assertTrue(key, runCatching { NightTimerCodec.decode(root.toString(), 1, namespace) }.isFailure)
        }
        val missing = JsonParser.parseString(raw).asJsonObject.apply { getAsJsonObject("timer").remove("millis") }
        assertTrue(runCatching { NightTimerCodec.decode(missing.toString(), 1, namespace) }.isFailure)
        assertTrue(runCatching { NightTimerCodec.decode("x".repeat(16385), 1, namespace) }.isFailure)
    }
    @Test fun clearingKeepsAVersionedTombstoneAndStartCannotUseAnUnboundIdentity() {
        val frame = NightTimerFrame(1, namespace).replace(timer()).replace(null)
        assertEquals(2L, frame.version); assertNull(frame.timer)
        assertEquals(frame, NightTimerCodec.decode(NightTimerCodec.encode(frame), 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { NightTimerFrame(2, namespace, 0, timer()) }
        assertThrows(IllegalArgumentException::class.java) { timer().copy(millis = 5000) }
    }
    @Test fun recordedBodyUsesMeasuredMetadataAndCanonicalIntentOrder() {
        val finished = timer().checkpoint(5500, start.plusMillis(5500)).finish()
        val body = finished.body(entry())
        assertEquals(5L, body["measured_seconds"].asLong); assertEquals("calculated", body["boundary_source"].asString)
        assertEquals(finished.date, body["night_date"].asString); assertEquals(finished.ended, body["ended_at"].asString)
        val reordered = JsonObject(); entry().entrySet().reversed().forEach { reordered.add(it.key, it.value) }
        assertEquals(body.toString(), finished.body(reordered).toString())
        val injected = entry().apply { addProperty("measured_seconds", 999) }
        assertTrue(runCatching { finished.body(injected) }.isFailure)
        assertTrue(runCatching { timer().body(entry()) }.isFailure)
        assertTrue(runCatching { finished.body(entry().apply { addProperty("verse_count", 3) }) }.isFailure)
    }
    @Test fun exportAcceptsOldCopiesAndValidatesPairedOwnedTimersAndUniqueReceipts() {
        val device = JsonObject().apply {
            addProperty("owner_id", 1); addProperty("namespace", namespace)
            org.athar.app.core.data.AccountExportPolicy.deviceSections.forEach { key -> add(key, if (key == "device_reading_position") JsonNull.INSTANCE else JsonArray()) }
        }
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        device.add("device_night_timer", JsonArray().apply { add(JsonParser.parseString(NightTimerCodec.encode(NightTimerFrame(1, namespace).replace(timer())))) })
        assertTrue(runCatching { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }.isFailure)
        val finished = timer().checkpoint(5000, start.plusSeconds(5)).finish()
        val receipt = NightTimerCodec.receipt(1, namespace, finished, finished.body(entry()))
        device.add("device_night_timer_records", JsonArray().apply { add(JsonParser.parseString(NightTimerCodec.encode(receipt))) })
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        device.getAsJsonArray("device_night_timer_records").add(JsonParser.parseString(NightTimerCodec.encode(receipt)))
        assertTrue(runCatching { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }.isFailure)
        device.getAsJsonArray("device_night_timer_records").remove(1)
        device.getAsJsonArray("device_night_timer")[0].asJsonObject.addProperty("owner", 2)
        assertTrue(runCatching { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }.isFailure)
    }
}
