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
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private inner class Gateway : KhatmahGateway {
        var active = true
        var writes = 0
        var loadBlock: suspend () -> Pair<KhatmahPlan?, Boolean> = { plan() to false }
        var writeBlock: suspend (KhatmahDraft, Int) -> KhatmahPlan = { draft, rev -> plan(rev + 1, draft.pages.toInt()) }
        override fun requireActive() { check(active) }
        override suspend fun load() = loadBlock()
        override suspend fun write(draft: KhatmahDraft, expected: Int, action: String?): KhatmahPlan { writes++; return writeBlock(draft, expected) }
    }
    @Test fun backgroundRefreshAndLateAckNeverOverwriteNewerDraft() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20"))
        gateway.loadBlock = { plan(2, 15) to false }; vm.refresh(); runCurrent()
        assertEquals("20", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        val reply = CompletableDeferred<KhatmahPlan>(); gateway.writeBlock = { draft, rev -> assertEquals("20", draft.pages); assertEquals(1, rev); reply.await() }
        vm.save(); runCurrent(); vm.edit(vm.state.value.draft.copy(pages = "30"))
        reply.complete(plan(2, 20)); runCurrent()
        assertEquals("30", vm.state.value.draft.pages); assertEquals(2, vm.state.value.baseline!!.revision)
        assertEquals(KhatmahFeedback.SAVED_WITH_DRAFT, vm.state.value.feedback)
    }
    @Test fun conflictAndFailedDiscardRetainDraftAndExpectedRevision() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20")); gateway.writeBlock = { _, _ -> throw ApiException(409, "conflict", "") }
        vm.save(); runCurrent(); assertEquals(KhatmahFeedback.CONFLICT, vm.state.value.feedback)
        gateway.loadBlock = { throw IOException("fixture offline") }; vm.refresh(discard = true); runCurrent()
        assertEquals("20", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        gateway.loadBlock = { plan(3, 25) to false }; vm.refresh(discard = true); runCurrent()
        assertEquals("25", vm.state.value.draft.pages); assertEquals(3, vm.state.value.baseline!!.revision)
    }
    @Test fun newerInputDuringExplicitReloadIsRetainedAndOfflineCannotWrite() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        val loading = CompletableDeferred<Pair<KhatmahPlan?, Boolean>>()
        gateway.loadBlock = { loading.await() }; vm.refresh(discard = true); runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "40")); loading.complete(plan(2) to true); runCurrent()
        assertEquals("40", vm.state.value.draft.pages); assertEquals(1, vm.state.value.baseline!!.revision)
        vm.save(); runCurrent(); assertEquals(0, gateway.writes)
    }
    @Test fun actorLossBeforeDispatchOrLateReplyClearsPrivateView() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = KhatmahViewModel(gateway) { day }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "20"))
        val reply = CompletableDeferred<KhatmahPlan>(); gateway.writeBlock = { _, _ -> reply.await() }
        vm.save(); runCurrent(); gateway.active = false; reply.complete(plan(2, 20)); runCurrent()
        assertFalse(vm.state.value.ready); assertEquals("0", vm.state.value.draft.pages)
        vm.save(); runCurrent(); assertEquals(1, gateway.writes)
    }
    @Test fun utcDayUpdateDoesNotReinitializeDraft() = runTest(dispatcher) {
        var now = day; val vm = KhatmahViewModel(Gateway()) { now }; runCurrent()
        vm.edit(vm.state.value.draft.copy(pages = "60")); now = day.plusDays(1); vm.dayChanged()
        assertEquals(now, vm.state.value.today); assertEquals("60", vm.state.value.draft.pages)
    }
}
