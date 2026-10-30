package org.athar.app

import com.google.gson.JsonParser
import java.time.LocalDate
import org.athar.app.khatmah.*
import org.junit.Assert.*
import org.junit.Test

class KhatmahJournalTest {
    private val day = LocalDate.of(2026, 10, 29)
    private val namespace = "123456abcdef"
    private fun draft(pages: Int) = KhatmahDraft(day.toString(), day.plusDays(30).toString(), pages.toString())
    private fun plan(pages: Int, rev: Int = 10, status: String = "active") = KhatmahPlan(day, day.plusDays(30), pages, status, rev)
    private fun fresh() = KhatmahJournal(1, namespace)
    private fun known(pages: Int = 10) = fresh().refreshed(plan(pages))
    private fun bytes(journal: KhatmahJournal) = KhatmahJournalCodec.encode(journal)

    @Test fun preparedIntentSurvivesRestartAndLateAckPreservesNewerEdits() {
        val first = fresh().append("save", draft(10), 0, day)
        val coalesced = first.append("save", draft(20), first.version, day)
        assertEquals(1, coalesced.intents.size)
        assertNotEquals(first.intents[0].id, coalesced.intents[0].id)
        val prepared = coalesced.prepare()
        val reopened = KhatmahJournalCodec.decode(bytes(prepared), 1, namespace)
        assertEquals(0, reopened.intents[0].expectedRevision)
        val newer = reopened.append("save", draft(30), reopened.version, day)
        assertEquals(2, newer.intents.size)
        val ack = newer.accept(reopened.intents[0].id, plan(20, 1))
        assertEquals(30, ack.projected()!!.completed)
        assertEquals(newer.version, ack.version)
        assertEquals(1, ack.prepare().intents[0].expectedRevision)
        assertEquals(ack, ack.accept(reopened.intents[0].id, plan(20, 1)))
    }

    @Test fun lifecycleBoundariesStayOrderedRatherThanCollapsingIntoPut() {
        var state = known()
        for ((kind, value) in listOf("pause" to null, "save" to draft(20), "resume" to null, "restart" to draft(0), "save" to draft(603))) {
            state = state.append(kind, value, state.version, day)
        }
        assertEquals(listOf("pause", "save", "resume", "restart", "save"), state.intents.map { it.kind })
        assertEquals(603, state.projected()!!.completed)
        assertEquals("active", state.projected()!!.status)
        assertEquals(state, KhatmahJournalCodec.decode(bytes(state), 1, namespace))
    }

    @Test fun wrongAckCannotRemoveACommittedLocalIntent() {
        val state = known().append("save", draft(30), known().version, day).prepare()
        val before = bytes(state); val id = state.intents[0].id
        assertThrows(IllegalArgumentException::class.java) { state.accept(id, plan(31, 11)) }
        assertThrows(IllegalArgumentException::class.java) { state.accept(id, plan(30, 13)) }
        assertEquals(before, bytes(state))
        assertEquals(0, state.accept(id, plan(30, 11)).intents.size)
    }

    @Test fun vanishedOrOlderServerPreservesLocalCopyForExplicitChoice() {
        val local = known(30)
        for (remote in listOf(null, plan(10, 9))) {
            val conflict = local.refreshed(remote)
            assertTrue(conflict.conflicted); assertEquals(30, conflict.projected()!!.completed)
            assertThrows(IllegalArgumentException::class.java) { conflict.useServer(remote, local.version) }
            val resolved = conflict.useServer(remote, conflict.version)
            assertFalse(resolved.conflicted); assertEquals(remote, resolved.acknowledged)
        }
    }

    @Test fun keepDeviceRequiresRestartConsentAndHandlesMissingServer() {
        var pending = known().append("save", draft(30), known().version, day)
        pending = pending.append("pause", null, pending.version, day)
        val server = plan(604, 11, "finished")
        val conflict = pending.conflict(server); val before = bytes(conflict)
        assertThrows(IllegalArgumentException::class.java) { conflict.keepDevice(server, conflict.version, day) }
        assertEquals(before, bytes(conflict))
        val replacement = conflict.keepDevice(server, conflict.version, day, allowRestart = true)
        assertEquals(listOf("restart", "save", "pause"), replacement.intents.map { it.kind })
        assertEquals(30, replacement.projected()!!.completed); assertEquals("paused", replacement.projected()!!.status)
        val missing = pending.conflict(null)
        val recreated = missing.keepDevice(null, missing.version, day)
        assertEquals(listOf("save", "pause"), recreated.intents.map { it.kind })
        assertEquals(0, recreated.prepare().intents[0].expectedRevision)
    }

    @Test fun rejectedBytesRemainUntilAnExplicitValidRepairCanReplaceThem() {
        val pending = fresh().append("save", draft(30), 0, day).prepare().reject(422)
        val before = bytes(pending)
        assertEquals(pending, KhatmahJournalCodec.decode(before, 1, namespace))
        assertThrows(IllegalArgumentException::class.java) { pending.append("save", draft(40), pending.version, day) }
        assertThrows(IllegalArgumentException::class.java) { pending.keepDevice(null, pending.version, day.plusDays(100)) }
        assertEquals(before, bytes(pending))
        val repaired = pending.keepDevice(null, pending.version, day.plusDays(100), KhatmahDraft.from(null, day.plusDays(100)).copy(pages = "40"))
        assertNull(repaired.rejected); assertEquals(40, repaired.projected()!!.completed)
        assertEquals(before, bytes(pending))
    }

    @Test fun codecRejectsForeignCoercedDuplicateOrOversizedState() {
        val state = known().append("save", draft(20), known().version, day).prepare()
        val raw = bytes(state)
        assertThrows(IllegalArgumentException::class.java) { KhatmahJournalCodec.decode(raw, 2, namespace) }
        assertThrows(IllegalArgumentException::class.java) { KhatmahJournalCodec.decode(raw, 1, "fedcba654321") }
        val root = JsonParser.parseString(raw).asJsonObject; root.addProperty("version", 1.5)
        assertThrows(IllegalArgumentException::class.java) { KhatmahJournalCodec.decode(root.toString(), 1, namespace) }
        assertThrows(IllegalArgumentException::class.java) { state.copy(intents = state.intents + state.intents).checked() }
        assertThrows(IllegalArgumentException::class.java) { KhatmahJournalCodec.decode("x".repeat(KhatmahJournalCodec.MAX_BYTES + 1), 1, namespace) }
    }

    @Test fun queueLimitAndStaleReviewNeverDiscardIntents() {
        var state = fresh().append("save", draft(10), 0, day)
        repeat(63) { state = state.append(if (state.projected()!!.status == "active") "pause" else "resume", null, state.version, day) }
        assertEquals(64, state.intents.size)
        val before = bytes(state)
        assertThrows(IllegalArgumentException::class.java) { state.append("resume", null, state.version, day) }
        assertThrows(IllegalArgumentException::class.java) { state.useServer(null, state.version - 1) }
        assertThrows(IllegalArgumentException::class.java) { state.useServer(null, state.version) }
        assertEquals(before, bytes(state))
    }

    @Test fun deliberateExportValidatesJournalOwnerWithoutBreakingOlderExportCopies() {
        val device = com.google.gson.JsonObject().apply {
            addProperty("owner_id", 1); addProperty("namespace", namespace)
            org.athar.app.core.data.AccountExportPolicy.deviceSections.forEach { key -> add(key, if (key == "device_reading_position") com.google.gson.JsonNull.INSTANCE else com.google.gson.JsonArray()) }
        }
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        val journal = known().append("save", draft(30), known().version, day)
        device.add("device_khatmah_state", com.google.gson.JsonArray().apply { add(JsonParser.parseString(bytes(journal))) })
        org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device)
        device.getAsJsonArray("device_khatmah_state")[0].asJsonObject.addProperty("owner", 2)
        assertThrows(IllegalArgumentException::class.java) { org.athar.app.core.data.AccountExportPolicy.requireDevice(1, namespace, device) }
    }

    @Test fun refreshCannotReplacePendingIntentOrInvalidateUntypedClockContext() {
        val local = known().append("save", draft(20), known().version, day).prepare()
        assertEquals(local, local.refreshed(plan(100, 20)))
        assertEquals(local, KhatmahJournalCodec.decode(bytes(local), 1, namespace))
        val metadataOnly = known().refreshed(plan(10, 11))
        assertEquals(known().version, metadataOnly.version)
    }
}
