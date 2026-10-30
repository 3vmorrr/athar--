package org.athar.app

import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException
import org.athar.app.nightprayer.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
class NightTimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val namespace = "123456abcdef"
    private val start = Instant.parse("2026-10-29T23:59:50Z")
    private val context = NightContext(LocalDate.parse("2026-10-29"), ZoneOffset.UTC, Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-30T05:00:00Z"))
    private val identity = NightIdentity(context.date, true, context)
    private var elapsedNow = 1000L
    private var wallNow = start
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun entry(note: String = "original") = JsonParser.parseString("""{"entry_type":"manual","verse_count":10,"note":"$note"}""").asJsonObject
    private fun measured() = NightMeasuredTimer.start(identity, "EGYPTIAN", start)
    private inner class Gateway(initial: NightTimerFrame = NightTimerFrame(1, namespace)) : NightTimerGateway {
        var current = initial; var active = true; var starts = 0; var clears = 0; var checkpoints = 0
        var restores = 0
        val flow = MutableStateFlow<ProtectedRead<NightTimerFrame>>(ProtectedRead.Ready(initial))
        val recorded = mutableListOf<JsonObject>()
        var restoreFailure = false
        var checkpointFailure = false
        var checkpointBlock: (suspend (NightMeasuredTimer) -> NightTimerFrame)? = null
        var startBlock: (suspend () -> NightTimerFrame)? = null
        var recordBlock: (suspend () -> NightTimerFrame)? = null
        override fun requireActive() { check(active) }
        override fun changes() = flow
        override suspend fun read(): NightTimerFrame { if (restoreFailure) throw IOException("fixture read failure"); return current }
        fun emit(frame: NightTimerFrame) { current = frame; flow.value = ProtectedRead.Ready(frame) }
        private fun change(expected: Long, timer: NightMeasuredTimer?): NightTimerFrame {
            if (expected != current.version) throw ApiException(409, "night_timer_changed", "")
            return current.replace(timer).also(::emit)
        }
        override suspend fun restore(): NightTimerFrame {
            restores++
            if (restoreFailure) throw IOException("fixture read failure")
            if (current.timer?.phase == "running") emit(current.replace(current.timer!!.recover()))
            return current
        }
        override suspend fun start(expected: Long, identity: NightIdentity, method: String): NightTimerFrame {
            starts++; return startBlock?.invoke() ?: change(expected, NightMeasuredTimer.start(identity, method, wallNow))
        }
        override suspend fun resume(expected: Long, selected: NightMeasuredTimer) = change(expected, selected.resume(wallNow))
        override suspend fun checkpoint(expected: Long, selected: NightMeasuredTimer, sampled: NightMeasuredTimer): NightTimerFrame {
            checkpoints++; if (checkpointFailure) throw IOException("fixture write failure")
            checkpointBlock?.let { return it(sampled) }
            return change(expected, sampled)
        }
        override suspend fun finish(expected: Long, selected: NightMeasuredTimer) = change(expected, selected.finish())
        override suspend fun clear(expected: Long): NightTimerFrame { clears++; return change(expected, null) }
        override suspend fun record(expected: Long, selected: NightMeasuredTimer, entry: JsonObject): NightTimerFrame {
            recorded.add(entry.deepCopy()); return recordBlock?.invoke() ?: change(expected, null)
        }
    }
    private fun vm(gateway: Gateway) = NightTimerViewModel(1, namespace, gateway, { elapsedNow }, { wallNow })
    private fun TestScope.advance(ms: Long) { elapsedNow += ms; wallNow = wallNow.plusMillis(ms); advanceTimeBy(ms); runCurrent() }
    @Test fun rapidStartClicksCannotCreateTwoTimers() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent()
        vm.start(0, identity, "EGYPTIAN"); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        assertEquals(1, g.starts); assertEquals("running", vm.state.value.frame!!.timer!!.phase)
        vm.clearPrivate()
    }
    @Test fun foregroundCountingPausesOnBackgroundAndReentryDoesNotAutoResume() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        advance(5000); assertEquals(5000L, vm.state.value.displayedMillis)
        vm.foreground(false); runCurrent(); assertEquals("paused", vm.state.value.frame!!.timer!!.phase)
        advance(60000); vm.foreground(true); runCurrent()
        assertEquals(5000L, vm.state.value.displayedMillis); assertEquals("paused", vm.state.value.frame!!.timer!!.phase)
        vm.resume(vm.state.value.frame!!.version); runCurrent(); advance(2000)
        assertEquals(7000L, vm.state.value.displayedMillis); vm.clearPrivate()
    }
    @Test fun backgroundDuringDelayedStartCannotCountOffscreenTime() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent()
        val response = CompletableDeferred<NightTimerFrame>(); g.startBlock = { response.await() }
        vm.start(0, identity, "EGYPTIAN"); runCurrent(); vm.foreground(false)
        advance(10000); val started = NightTimerFrame(1, namespace, 1, measured()); g.emit(started); response.complete(started); runCurrent()
        assertEquals("paused", vm.state.value.frame!!.timer!!.phase)
        assertEquals(0L, vm.state.value.displayedMillis); vm.clearPrivate()
    }
    @Test fun coldRunningCheckpointIsPausedAndNeverUsesPersistedUptime() = runTest(dispatcher) {
        val saved = measured().checkpoint(5000, start.plusSeconds(5))
        wallNow = start.plusSeconds(600); elapsedNow = 100
        val g = Gateway(NightTimerFrame(1, namespace, 2, saved)); val vm = vm(g); runCurrent()
        assertEquals("recovered", vm.state.value.frame!!.timer!!.reason)
        advance(60000); assertEquals(5000L, vm.state.value.displayedMillis); vm.clearPrivate()
    }
    @Test fun checkpointFailureStopsCountingAndExposesConservativePausedRecovery() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        g.checkpointFailure = true; advance(5000)
        assertEquals(NightTimerFeedback.UNCONFIRMED, vm.state.value.feedback)
        assertEquals("running", vm.state.value.frame!!.timer!!.phase); assertTrue(vm.state.value.needsRecovery)
        assertEquals(0L, vm.state.value.displayedMillis)
        advance(10000); assertEquals(0L, vm.state.value.displayedMillis)
        vm.retry(); runCurrent(); assertEquals("paused", vm.state.value.frame!!.timer!!.phase); vm.clearPrivate()
    }
    @Test fun unreadableStorageNeverEnablesAnEmptyStartOrClear() = runTest(dispatcher) {
        val g = Gateway(); g.restoreFailure = true; val vm = vm(g); runCurrent()
        assertEquals(ProtectedRead.Unreadable, vm.state.value.read)
        vm.start(0, identity, "EGYPTIAN"); vm.clear(0); runCurrent()
        assertEquals(0, g.starts); assertEquals(0, g.clears); vm.clearPrivate()
    }
    @Test fun aStaleConfirmationAndLateStartCannotReplaceANewerClearedFrame() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent()
        val response = CompletableDeferred<NightTimerFrame>(); g.startBlock = { response.await() }
        vm.start(0, identity, "EGYPTIAN"); runCurrent()
        g.emit(NightTimerFrame(1, namespace, 2)); runCurrent()
        response.complete(NightTimerFrame(1, namespace, 1, measured())); runCurrent()
        assertNull(vm.state.value.frame!!.timer); assertEquals(2L, vm.state.value.frame!!.version)
        vm.clear(1); runCurrent(); assertEquals(0, g.clears); vm.clearPrivate()
    }
    @Test fun uncertainRecordingRetryKeepsIdenticalIntentAndDoesNotClearALaterTimer() = runTest(dispatcher) {
        val finished = measured().checkpoint(5000, start.plusSeconds(5)).finish()
        wallNow = start.plusSeconds(5)
        val g = Gateway(NightTimerFrame(1, namespace, 3, finished)); val vm = vm(g); runCurrent()
        vm.openRecord(3); g.recordBlock = { g.emit(g.current.replace(null)); throw IOException("fixture lost return") }
        vm.record(entry()); runCurrent(); assertNotNull(vm.state.value.frozenEntry)
        val later = g.current.replace(NightMeasuredTimer.start(identity, "EGYPTIAN", wallNow)); g.emit(later); runCurrent()
        g.recordBlock = { later }; vm.record(entry("different")); runCurrent()
        assertEquals(2, g.recorded.size); assertEquals(g.recorded[0], g.recorded[1])
        assertEquals(later, vm.state.value.frame); assertNull(vm.state.value.recording)
        assertTrue(vm.state.value.needsRecovery); vm.clearPrivate()
    }
    @Test fun actorLossDuringLateStartClearsAllPrivatePresentation() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent()
        val response = CompletableDeferred<NightTimerFrame>(); g.startBlock = { response.await() }
        vm.start(0, identity, "EGYPTIAN"); runCurrent(); g.active = false
        response.complete(NightTimerFrame(1, namespace, 1, measured())); runCurrent()
        assertNull(vm.state.value.frame); assertNull(vm.state.value.recording); assertEquals(0L, vm.state.value.displayedMillis)
    }
    @Test fun finishingAndRecordingAreSeparateActionsAndTooShortDoesNotStopCounting() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        vm.finish(1); runCurrent(); assertEquals(NightTimerFeedback.TOO_SHORT, vm.state.value.feedback)
        advance(2000); vm.finish(vm.state.value.frame!!.version); runCurrent()
        assertEquals("finished", vm.state.value.frame!!.timer!!.phase); assertTrue(g.recorded.isEmpty())
        vm.openRecord(vm.state.value.frame!!.version); assertTrue(g.recorded.isEmpty())
        vm.record(entry()); runCurrent(); assertEquals(1, g.recorded.size)
        assertNull(vm.state.value.frame!!.timer); vm.clearPrivate()
    }
    @Test fun queuedBackgroundCallbackCannotPauseALaterTimerAfterADelayedWrite() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        val first = g.current; val response = CompletableDeferred<NightTimerFrame>()
        g.checkpointBlock = { response.await() }
        vm.pause(first.version); runCurrent(); vm.foreground(false)
        val later = NightTimerFrame(1, namespace, 3, measured()); g.emit(later); runCurrent()
        response.complete(first.replace(first.timer!!.copy(phase = "paused", reason = "user"))); runCurrent()
        assertEquals(later, vm.state.value.frame); assertEquals(1, g.checkpoints)
        assertTrue(vm.state.value.needsRecovery); vm.clearPrivate()
    }
    @Test fun clockValidationKeepsTheRecordingIntentForAnIdenticalRetry() = runTest(dispatcher) {
        val finished = measured().checkpoint(5000, start.plusSeconds(5)).finish()
        wallNow = start.plusSeconds(5)
        val g = Gateway(NightTimerFrame(1, namespace, 3, finished)); val vm = vm(g); runCurrent(); vm.openRecord(3)
        g.recordBlock = { throw IllegalArgumentException("fixture clock mismatch") }
        vm.record(entry()); runCurrent()
        assertEquals(NightTimerFeedback.CLOCK, vm.state.value.feedback); assertNotNull(vm.state.value.frozenEntry)
        g.recordBlock = null; vm.record(entry("different")); runCurrent()
        assertEquals(g.recorded[0], g.recorded[1]); assertNull(vm.state.value.recording); vm.clearPrivate()
    }
    @Test fun staleWriteFailureMayReadButCannotRecoverOrPauseANewerTimer() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.start(0, identity, "EGYPTIAN"); runCurrent()
        val response = CompletableDeferred<NightTimerFrame>(); g.checkpointBlock = { response.await() }
        vm.pause(g.current.version); runCurrent()
        val later = NightTimerFrame(1, namespace, 3, measured()); g.emit(later); runCurrent()
        response.completeExceptionally(ApiException(409, "night_timer_changed", "")); runCurrent()
        assertEquals(1, g.restores); assertEquals(later, g.current); assertEquals(later, vm.state.value.frame)
        assertTrue(vm.state.value.needsRecovery); vm.clearPrivate()
    }
}
