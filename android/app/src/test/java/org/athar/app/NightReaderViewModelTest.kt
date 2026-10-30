package org.athar.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.athar.app.core.network.ApiException
import org.athar.app.nightprayer.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NightReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = NightReadingScope(1, "123456abcdef", "plan", "11111111-1111-4111-8111-111111111111", 7, 8)
    private val now = Instant.parse("2026-10-30T04:00:00Z")
    private fun snapshot(version: Long = 0, index: Int? = null) = NightReadingSnapshot(scope,
        NightReadingPosition(scope, version, index, if (version == 0L) null else now.toString()), 7)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private inner class Gateway : NightReaderGateway {
        var active = true; var writes = 0; var resets = 0
        val events = MutableSharedFlow<Result<NightReadingSnapshot>>(extraBufferCapacity = 8)
        var current = snapshot()
        var failure: Exception? = null
        var dataFailure = false
        var write: suspend () -> NightReadingSnapshot = { snapshot(1, 8) }
        override fun requireActive() { check(active) }
        override fun changes() = flow { failure?.let { throw it }; emit(current); emitAll(events.map { it.getOrThrow() }) }
        override suspend fun data() = if (dataFailure) throw IOException("fixture assets") else QuranJvmFixture.data
        override suspend fun read() = current
        override suspend fun save(index: Int, expected: Long): NightReadingSnapshot { writes++; return write() }
        override suspend fun clear(expected: Long) = snapshot(expected + 1)
        override suspend fun reset(expected: Long): NightReadingSnapshot { resets++; failure = null; current = snapshot(expected + 1); return current }
        suspend fun emit(value: NightReadingSnapshot) { current = value; events.emit(Result.success(value)) }
    }
    @Test fun exactCrossSurahSliceUsesOriginalTextAndStartsAtIndependentMarker() = runTest(dispatcher) {
        val g = Gateway(); g.current = snapshot(1, 8); val vm = NightReaderViewModel(scope, g); runCurrent()
        assertTrue(vm.state.value.ready)
        assertEquals(listOf(7, 8), vm.state.value.verses.map { it.position.globalIndex })
        assertSame(QuranJvmFixture.data.verses[7], vm.state.value.verses[1])
        assertNotNull(vm.state.value.verses[1].bismillah)
        assertEquals(8, vm.state.value.scrollIndex); vm.clearPrivate()
    }
    @Test fun backgroundMarkerUpdateDoesNotMoveReadingAndExplicitControlsDo() = runTest(dispatcher) {
        val g = Gateway(); val vm = NightReaderViewModel(scope, g); runCurrent()
        val request = vm.state.value.scrollRequest
        g.emit(snapshot(1, 8)); runCurrent()
        assertEquals(request, vm.state.value.scrollRequest); assertEquals(7, vm.state.value.scrollIndex)
        vm.resume(); assertEquals(8, vm.state.value.scrollIndex)
        vm.start(); assertEquals(7, vm.state.value.scrollIndex); vm.clearPrivate()
    }
    @Test fun staleReviewedVersionAndOutOfRangeNeverWrite() = runTest(dispatcher) {
        val g = Gateway(); g.current = snapshot(1, 7); val vm = NightReaderViewModel(scope, g); runCurrent()
        vm.save(8, 0); assertEquals(NightReaderFeedback.CHANGED, vm.state.value.feedback)
        vm.save(9, 1); assertEquals(NightReaderFeedback.INVALID, vm.state.value.feedback)
        assertEquals(0, g.writes); vm.clearPrivate()
    }
    @Test fun lateAcknowledgementCannotReplaceNewerMarker() = runTest(dispatcher) {
        val g = Gateway(); val vm = NightReaderViewModel(scope, g); runCurrent()
        val reply = CompletableDeferred<NightReadingSnapshot>(); g.write = { reply.await() }
        vm.save(8, 0); runCurrent(); g.emit(snapshot(2, 7)); runCurrent()
        reply.complete(snapshot(1, 8)); runCurrent()
        assertEquals(2L, vm.state.value.snapshot!!.position.version)
        assertEquals(NightReaderFeedback.CHANGED, vm.state.value.feedback); vm.clearPrivate()
    }
    @Test fun sourceChangeWhileSavingCannotBeResurrectedByLateAcknowledgement() = runTest(dispatcher) {
        val g = Gateway(); val vm = NightReaderViewModel(scope, g); runCurrent()
        val reply = CompletableDeferred<NightReadingSnapshot>(); g.write = { reply.await() }
        vm.save(8, 0); runCurrent()
        g.failure = ApiException(404, "night_source_missing", "")
        g.events.emit(Result.failure(g.failure!!)); runCurrent()
        assertEquals(NightReaderError.SOURCE_CHANGED, vm.state.value.error)
        assertFalse(vm.state.value.ready)
        reply.complete(snapshot(1, 8)); runCurrent()
        assertFalse(vm.state.value.ready); assertNull(vm.state.value.snapshot); vm.clearPrivate()
    }
    @Test fun changedRangeRequiresReviewedResetAndRestartsObservation() = runTest(dispatcher) {
        val g = Gateway(); val old = scope.copy(end = 9)
        g.failure = NightPositionRangeChanged(NightReadingPosition(old, 3, 9, now.toString()))
        val vm = NightReaderViewModel(scope, g); runCurrent()
        assertEquals(NightReaderError.RANGE_CHANGED, vm.state.value.error)
        vm.reset(2); runCurrent(); assertEquals(0, g.resets)
        vm.reset(3); runCurrent(); assertTrue(vm.state.value.ready); assertEquals(4L, vm.state.value.snapshot!!.position.version)
        g.emit(snapshot(5, 8)); runCurrent(); assertEquals(5L, vm.state.value.snapshot!!.position.version); vm.clearPrivate()
    }
    @Test fun unreadableStorageNeverAppearsAsAnEmptyMarker() = runTest(dispatcher) {
        val g = Gateway(); g.failure = IOException("fixture unreadable")
        val vm = NightReaderViewModel(scope, g); runCurrent()
        assertEquals(NightReaderError.UNREADABLE, vm.state.value.error); assertNull(vm.state.value.snapshot)
        vm.save(8, 0); runCurrent(); assertEquals(0, g.writes); vm.clearPrivate()
    }
    @Test fun failedAssetsCanBeRetriedWithoutReplacingSourceValidation() = runTest(dispatcher) {
        val g = Gateway(); g.dataFailure = true; val vm = NightReaderViewModel(scope, g); runCurrent()
        assertEquals(NightReaderError.DATA_UNAVAILABLE, vm.state.value.error)
        g.dataFailure = false; vm.retry(); runCurrent(); assertTrue(vm.state.value.ready); vm.clearPrivate()
    }
    @Test fun actorLossClearsPrivatePresentationBeforeLateCompletion() = runTest(dispatcher) {
        val g = Gateway(); val vm = NightReaderViewModel(scope, g); runCurrent()
        val reply = CompletableDeferred<NightReadingSnapshot>(); g.write = { reply.await() }
        vm.save(8, 0); runCurrent(); g.active = false; reply.complete(snapshot(1, 8)); runCurrent()
        assertNull(vm.state.value.scope); assertTrue(vm.state.value.verses.isEmpty()); assertNull(vm.state.value.snapshot)
    }
    @Test fun assetFailureDoesNotHideARequiredRangeReview() = runTest(dispatcher) {
        val g = Gateway(); g.dataFailure = true
        g.failure = NightPositionRangeChanged(NightReadingPosition(scope.copy(end = 9), 3, 9, now.toString()))
        val vm = NightReaderViewModel(scope, g); runCurrent()
        assertEquals(NightReaderError.RANGE_CHANGED, vm.state.value.error)
        assertEquals(3L, vm.state.value.review!!.version)
        g.dataFailure = false; vm.retry(); runCurrent()
        assertEquals(NightReaderError.RANGE_CHANGED, vm.state.value.error); assertNotNull(vm.state.value.reviewRange)
        vm.clearPrivate()
    }
    @Test fun conflictRefreshDoesNotOverwriteANewerObservedMarker() = runTest(dispatcher) {
        val g = Gateway(); val vm = NightReaderViewModel(scope, g); runCurrent()
        val reply = CompletableDeferred<NightReadingSnapshot>(); g.write = { reply.await() }
        vm.save(8, 0); runCurrent(); g.emit(snapshot(2, 7)); runCurrent()
        reply.completeExceptionally(ApiException(409, "night_position_changed", "")); g.current = snapshot(1, 8); runCurrent()
        assertEquals(2L, vm.state.value.snapshot!!.position.version)
        assertEquals(NightReaderFeedback.CHANGED, vm.state.value.feedback); vm.clearPrivate()
    }
    @Test fun retryCannotRegressTheLastValidatedMarkerRevision() = runTest(dispatcher) {
        val g = Gateway(); g.current = snapshot(2, 8)
        val vm = NightReaderViewModel(scope, g); runCurrent()
        g.current = snapshot(1, 7); vm.retry(); runCurrent()
        assertEquals(2L, vm.state.value.snapshot!!.position.version)
        assertTrue(vm.state.value.ready); vm.clearPrivate()
    }
}
