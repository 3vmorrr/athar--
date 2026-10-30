package org.athar.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException
import org.athar.app.nightprayer.*
import org.athar.app.quran.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class NightPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val index by lazy { QuranIndex.fromJson(File("src/main/assets/quran/index.json").readText()) }
    private val now = Instant.parse("2026-10-30T04:00:00Z")
    private fun fresh() = NightPlanState(1, "123456abcdef")
    private fun saved(count: Int = 100) = fresh().save(1, count, 0, now)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private inner class Gateway(initial: NightPlanState = fresh()) : NightPlanGateway {
        var active = true; var writes = 0; var clears = 0
        var current = initial
        val flow = MutableStateFlow<ProtectedRead<NightPlanState>>(ProtectedRead.Ready(initial))
        var readBlock: suspend () -> NightPlanState = { current }
        var writeBlock: suspend (QuranRange, Long) -> NightPlanState = { range, version ->
            if (current.version != version) throw ApiException(409, "changed", "")
            current.save(range.start.globalIndex, range.end.globalIndex, version, now).also { current = it }
        }
        val records = mutableListOf<NightPlanRecord>()
        var recordBlock: suspend (NightPlanRecord) -> Unit = {}
        override fun requireActive() { check(active) }
        override fun changes() = flow
        override suspend fun index() = this@NightPlanViewModelTest.index
        override suspend fun read() = readBlock()
        override suspend fun save(range: QuranRange, expected: Long): NightPlanState { writes++; return writeBlock(range, expected) }
        override suspend fun clear(expected: Long): NightPlanState { clears++; return current.clear(expected).also { current = it } }
        override suspend fun record(record: NightPlanRecord, expected: Long) { assertEquals(current.version, expected); records.add(record); recordBlock(record) }
        fun emit(value: NightPlanState) { current = value; flow.value = ProtectedRead.Ready(value) }
    }
    @Test fun previewLocksItsObservedVersionEvenBeforeTheFirstTypedEdit() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = NightPlanViewModel(gateway); runCurrent()
        vm.calculate(); gateway.emit(saved(20)); runCurrent()
        assertEquals(0L, vm.state.value.expectedVersion)
        vm.save(); runCurrent()
        assertEquals(NightPlanFeedback.CHANGED, vm.state.value.feedback)
        assertEquals(100, vm.state.value.preview!!.verseCount); assertEquals(20, vm.state.value.savedRange!!.verseCount)
        vm.reloadForm(); runCurrent()
        assertEquals(1L, vm.state.value.expectedVersion); assertNull(vm.state.value.preview)
        assertEquals("13", vm.state.value.draft.endAyah); vm.clearPrivate()
    }
    @Test fun lateSaveCannotReplaceTypingThatArrivedAfterSubmission() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = NightPlanViewModel(gateway); runCurrent(); vm.calculate()
        val response = CompletableDeferred<NightPlanState>(); gateway.writeBlock = { _, _ -> response.await() }
        vm.save(); runCurrent(); vm.edit(vm.state.value.draft.copy(count = "200"))
        response.complete(saved()); runCurrent()
        assertEquals("200", vm.state.value.draft.count); assertNull(vm.state.value.preview)
        assertEquals(NightPlanFeedback.SAVED_WITH_DRAFT, vm.state.value.feedback)
        assertEquals(1L, vm.state.value.expectedVersion); vm.clearPrivate()
    }
    @Test fun reloadFailureOrNewerTypingKeepsTheUnsavedFormAndOriginalVersion() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = NightPlanViewModel(gateway); runCurrent()
        vm.edit(vm.state.value.draft.copy(count = "45"))
        gateway.readBlock = { throw IOException("fixture offline") }; vm.reloadForm(); runCurrent()
        assertEquals("45", vm.state.value.draft.count); assertEquals(0L, vm.state.value.expectedVersion)
        val response = CompletableDeferred<NightPlanState>(); gateway.readBlock = { response.await() }
        vm.reloadForm(); runCurrent(); vm.edit(vm.state.value.draft.copy(count = "60")); response.complete(saved(30)); runCurrent()
        assertEquals("60", vm.state.value.draft.count); assertEquals(0L, vm.state.value.expectedVersion)
        assertEquals(NightPlanFeedback.CHANGED, vm.state.value.feedback); vm.clearPrivate()
    }
    @Test fun uncertainRecordRetryKeepsExactlyTheSameIntentAndDoesNotAcceptNewFields() = runTest(dispatcher) {
        val gateway = Gateway(saved()); val vm = NightPlanViewModel(gateway); runCurrent()
        vm.openRecord(); vm.editRecord("2026-10-29", "UTC", "original private note")
        gateway.recordBlock = { throw IOException("fixture uncertain return") }
        vm.record(); runCurrent(); assertTrue(vm.state.value.record!!.frozen)
        vm.editRecord("2026-10-28", "UTC", "different note")
        assertEquals("original private note", vm.state.value.record!!.note)
        gateway.recordBlock = {}; vm.record(); runCurrent()
        assertEquals(2, gateway.records.size); assertEquals(gateway.records[0].receipt(), gateway.records[1].receipt())
        assertNull(vm.state.value.record); assertEquals(NightPlanFeedback.RECORDED_LOCALLY, vm.state.value.feedback)
        vm.clearPrivate()
    }
    @Test fun knownValidationFailureAllowsRepairWithoutDroppingTheDialog() = runTest(dispatcher) {
        val gateway = Gateway(saved()); val vm = NightPlanViewModel(gateway); runCurrent(); vm.openRecord()
        vm.record(); runCurrent(); assertEquals(NightPlanFeedback.INVALID, vm.state.value.feedback)
        vm.editRecord("2026-10-29", "UTC", "")
        gateway.recordBlock = { throw IllegalArgumentException("fixture invalid field") }; vm.record(); runCurrent()
        assertFalse(vm.state.value.record!!.frozen)
        vm.editRecord("2026-10-28", "UTC", "repaired")
        assertEquals("repaired", vm.state.value.record!!.note); vm.clearPrivate()
    }
    @Test fun unreadableStorageDisablesMutationWithoutPretendingTheSavedPlanIsEmpty() = runTest(dispatcher) {
        val gateway = Gateway(saved()); val vm = NightPlanViewModel(gateway); runCurrent()
        vm.edit(vm.state.value.draft.copy(count = "45")); gateway.flow.value = ProtectedRead.Unreadable; runCurrent()
        vm.calculate(); vm.save(); vm.openRecord(); vm.clear(1); runCurrent()
        assertFalse(vm.state.value.ready); assertEquals(ProtectedRead.Unreadable, vm.state.value.read)
        assertEquals("45", vm.state.value.draft.count); assertEquals(0, gateway.writes); assertEquals(0, gateway.clears)
        vm.clearPrivate()
    }
    @Test fun actorLossDuringLateSaveClearsPrivateDraftAndDialog() = runTest(dispatcher) {
        val gateway = Gateway(); val vm = NightPlanViewModel(gateway); runCurrent(); vm.calculate()
        val response = CompletableDeferred<NightPlanState>(); gateway.writeBlock = { _, _ -> response.await() }
        vm.save(); runCurrent(); gateway.active = false; response.complete(saved()); runCurrent()
        assertFalse(vm.state.value.ready); assertNull(vm.state.value.savedRange); assertNull(vm.state.value.record)
        assertEquals(NightPlanDraft(), vm.state.value.draft)
    }
    @Test fun staleClearConfirmationAndAnOlderSaveReplyCannotReplaceANewerPlan() = runTest(dispatcher) {
        val gateway = Gateway(saved(20)); val vm = NightPlanViewModel(gateway); runCurrent()
        val old = gateway.current; val newer = old.save(1, 30, old.version, now)
        gateway.emit(newer); runCurrent(); vm.clear(old.version); runCurrent()
        assertEquals(0, gateway.clears); assertEquals(NightPlanFeedback.CHANGED, vm.state.value.feedback)
        vm.calculate(); val response = CompletableDeferred<NightPlanState>(); gateway.writeBlock = { _, _ -> response.await() }
        vm.save(); runCurrent(); val latest = newer.save(1, 40, newer.version, now); gateway.emit(latest); runCurrent()
        response.complete(newer); runCurrent()
        assertEquals(40, vm.state.value.savedRange!!.verseCount); assertEquals(NightPlanFeedback.CHANGED, vm.state.value.feedback)
        vm.clearPrivate()
    }
    @Test fun localizedNumericInputPreservesInvalidValuesForExplicitValidation() {
        assertEquals("12", NightPlanDraft.numberInput("١۲"))
        for (value in listOf("1.5", "-1", "6237")) assertEquals(value, NightPlanDraft.numberInput(value))
        assertThrows(IllegalArgumentException::class.java) { NightPlanDraft(count = "1.5").range(index) }
    }
}
