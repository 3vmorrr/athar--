package org.athar.app

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.athar.app.khatmah.*
import org.athar.app.core.network.ApiException
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class KhatmahViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.of(2026, 10, 29)
    private fun plan(revision: Int = 1, pages: Int = 10) = KhatmahPlan(day, day.plusDays(30), pages, "active", revision)
    private fun journal(revision: Int = 1, pages: Int = 10) = KhatmahJournal(1, "123456abcdef", revision.toLong(), true, plan(revision, pages)).checked()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private inner class Gateway : KhatmahGateway {
        var active = true
        var writes = 0
        var loadBlock: suspend () -> KhatmahRead = { KhatmahRead(journal()) }
        var writeBlock: suspend (KhatmahDraft, Long) -> KhatmahJournal = { draft, rev -> journal(rev.toInt() + 1, draft.pages.toInt()) }
        override suspend fun useServer(expected: Long) = journal(expected.toInt() + 1)
        override suspend fun keepDevice(expected: Long, draft: KhatmahDraft) = write(draft, expected, null)
        override fun requireActive() { check(active) }
        override suspend fun load() = loadBlock()
        override suspend fun write(draft: KhatmahDraft, expected: Long, action: String?): KhatmahJournal { writes++; return writeBlock(draft, expected) }
    }
    @Test fun backgroundRefreshAndLateAckNeverOverwriteNewerDraft() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20"))
        gateway.loadBlock = { KhatmahRead(journal(2, 15)) }; vm.refresh(); runCurrent()
        assertEquals("20", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        val reply = CompletableDeferred<KhatmahJournal>(); gateway.writeBlock = { draft, rev -> assertEquals("20", draft.pages); assertEquals(1L, rev); reply.await() }
        vm.save(); runCurrent(); vm.edit(vm.state.value.draft.copy(pages = "30"))
        reply.complete(journal(2, 20)); runCurrent()
        assertEquals("30", vm.state.value.draft.pages); assertEquals(2, vm.state.value.baseline!!.revision)
        assertEquals(KhatmahFeedback.SAVED_WITH_DRAFT, vm.state.value.feedback)
    }
    @Test fun conflictAndFailedDiscardRetainDraftAndExpectedRevision() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20")); gateway.writeBlock = { _, _ -> throw ApiException(409, "conflict", "") }
        vm.save(); runCurrent(); assertEquals(KhatmahFeedback.CONFLICT, vm.state.value.feedback)
        gateway.loadBlock = { throw IOException("fixture offline") }; vm.refresh(discard = true); runCurrent()
        assertEquals("20", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        gateway.loadBlock = { KhatmahRead(journal(3, 25)) }; vm.refresh(discard = true); runCurrent()
        assertEquals("25", vm.state.value.draft.pages); assertEquals(3, vm.state.value.baseline!!.revision)
    }
    @Test fun newerInputDuringExplicitReloadIsRetainedAndOfflineSavesDurably() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        val loading = CompletableDeferred<KhatmahRead>()
        gateway.loadBlock = { loading.await() }; vm.refresh(discard = true); runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "40")); loading.complete(KhatmahRead(journal(2), true)); runCurrent()
        assertEquals("40", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        gateway.writeBlock = { draft, expected -> journal(expected.toInt()).append("save", draft, expected, day) }
        vm.save(); runCurrent(); assertEquals(1, gateway.writes)
        assertEquals(KhatmahFeedback.LOCAL_SAVED, vm.state.value.feedback)
        assertEquals("40", vm.state.value.journal!!.projected()!!.completed.toString())
    }
    @Test fun actorLossBeforeDispatchOrLateReplyClearsPrivateView() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20"))
        val reply = CompletableDeferred<KhatmahJournal>(); gateway.writeBlock = { _, _ -> reply.await() }
        vm.save(); runCurrent(); gateway.active = false; reply.complete(journal(2, 20)); runCurrent()
        assertFalse(vm.state.value.ready); assertEquals("0", vm.state.value.draft.pages)
        vm.save(); runCurrent(); assertEquals(1, gateway.writes)
    }
    @Test fun confirmationForAnOlderLocalVersionCannotDispatch() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        val reviewed = vm.state.value.baselineVersion
        gateway.loadBlock = { KhatmahRead(journal(2)) }; vm.refresh(); runCurrent()
        vm.action("pause", reviewedVersion = reviewed); runCurrent()
        assertEquals(0, gateway.writes); assertEquals(KhatmahFeedback.CONFLICT, vm.state.value.feedback)
    }
    @Test fun acknowledgedQueueUpdatesMetadataWithoutReplacingNewerTyping() = runTest(dispatcher) {
        val events = kotlinx.coroutines.flow.MutableSharedFlow<org.athar.app.core.data.ProtectedRead<KhatmahJournal>>()
        val gateway = object : KhatmahGateway {
            override fun requireActive() = Unit
            override fun changes() = events
            override suspend fun load() = KhatmahRead(journal())
            override suspend fun write(draft: KhatmahDraft, expected: Long, action: String?) = journal().append("save", draft, expected, day)
            override suspend fun useServer(expected: Long) = error("unused")
            override suspend fun keepDevice(expected: Long, draft: KhatmahDraft) = error("unused")
        }
        val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20")); vm.save(); runCurrent()
        val sent = vm.state.value.journal!!.prepare()
        vm.edit(vm.state.value.draft.copy(pages = "30"))
        events.emit(org.athar.app.core.data.ProtectedRead.Ready(sent.accept(sent.intents.first().id, plan(2, 20)))); runCurrent()
        assertEquals("30", vm.state.value.draft.pages); assertTrue(vm.state.value.journal!!.intents.isEmpty())
        assertEquals(2, vm.state.value.baseline!!.revision)
        assertEquals(20, vm.state.value.baseline!!.completed)
        events.emit(org.athar.app.core.data.ProtectedRead.Unreadable); runCurrent()
        vm.save(); runCurrent(); assertTrue(vm.state.value.unreadable); assertEquals("30", vm.state.value.draft.pages)
        vm.clearPrivate()
    }
    @Test fun utcDayUpdateDoesNotReinitializeDraft() = runTest(dispatcher) {
        var now = day; val vm = KhatmahViewModel(Gateway()) { now }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "60")); now = day.plusDays(1); vm.dayChanged()
        assertEquals(now, vm.state.value.today); assertEquals("60", vm.state.value.draft.pages)
    }
    @Test fun workerAckBeforeEnqueueReturnsCannotRestoreAnAlreadyAcknowledgedQueue() = runTest(dispatcher) {
        val events = kotlinx.coroutines.flow.MutableSharedFlow<org.athar.app.core.data.ProtectedRead<KhatmahJournal>>()
        val reply = CompletableDeferred<KhatmahJournal>()
        val gateway = object : KhatmahGateway {
            override fun requireActive() = Unit
            override fun changes() = events
            override suspend fun load() = KhatmahRead(journal())
            override suspend fun write(draft: KhatmahDraft, expected: Long, action: String?) = reply.await()
            override suspend fun useServer(expected: Long) = error("unused")
            override suspend fun keepDevice(expected: Long, draft: KhatmahDraft) = error("unused")
        }
        val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20")); vm.save(); runCurrent()
        val queued = journal().append("save", vm.state.value.draft, 1, day).prepare()
        val ack = queued.accept(queued.intents.first().id, plan(2, 20))
        events.emit(org.athar.app.core.data.ProtectedRead.Ready(ack)); runCurrent()
        reply.complete(queued); runCurrent()
        assertTrue(vm.state.value.journal!!.intents.isEmpty())
        assertEquals(KhatmahFeedback.SAVED, vm.state.value.feedback)
        assertEquals(2, vm.state.value.baseline!!.revision)
        vm.clearPrivate()
    }
}
