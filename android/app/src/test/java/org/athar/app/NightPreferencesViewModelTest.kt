package org.athar.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.athar.app.core.network.ApiException
import org.athar.app.nightprayer.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NightPreferencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val namespace = "123456abcdef"
    private fun partial() = NightPreferencesEnvelope(1, NightPreferenceValues("Riyadh", "Asia/Riyadh"))
    private fun complete() = NightPreferenceValues("Cairo", "Africa/Cairo", 30.0444, 31.2357, "EGYPTIAN", "SHAFI", "MIDDLE_OF_THE_NIGHT")
    private fun loaded(account: NightPreferencesEnvelope, proof: NightPreferenceRepair? = null) = NightPreferenceLoad(
        NightPreferencesSnapshot(1, namespace, account, "2026-10-30T04:00:00Z"), false, proof)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private inner class Gateway : NightPreferencesGateway {
        var active = true; var current = partial(); var loads = 0; var repairs = 0
        var loadFailure = false
        var loadBlock: (suspend () -> NightPreferenceLoad)? = null
        var saveBlock: (suspend (NightPreferencesEnvelope, NightPreferenceValues) -> NightPreferenceLoad)? = null
        var repairBlock: (suspend (NightPreferenceRepair) -> NightPreferenceLoad)? = null
        var publicBlock: (suspend () -> NightPublicDefaults)? = null
        val saved = mutableListOf<Pair<NightPreferencesEnvelope, NightPreferenceValues>>()
        override fun requireActive() { check(active) }
        override suspend fun load(): NightPreferenceLoad {
            loads++; if (loadFailure) throw IOException("fixture unavailable")
            return loadBlock?.invoke() ?: loaded(current)
        }
        override suspend fun save(reviewed: NightPreferencesEnvelope, wanted: NightPreferenceValues): NightPreferenceLoad {
            saved.add(reviewed to wanted)
            saveBlock?.let { return it(reviewed, wanted) }
            if (reviewed != current) throw ApiException(409, "conflict", "")
            current = NightPreferencesEnvelope(current.revision + 1, wanted); return loaded(current)
        }
        override suspend fun repair(reviewed: NightPreferenceRepair): NightPreferenceLoad {
            repairs++; return repairBlock?.invoke(reviewed) ?: loaded(current)
        }
        override suspend fun publicDefaults() = requireNotNull(publicBlock).invoke()
    }
    private fun vm(g: Gateway) = NightPreferencesViewModel(1, namespace, g)
    @Test fun validPartialAccountNeverInventsLocationOrASupportedMethodChoice() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent()
        assertEquals("Riyadh", vm.state.value.draft.city); assertEquals("Asia/Riyadh", vm.state.value.draft.timezone)
        assertEquals("", vm.state.value.draft.latitude); assertEquals("", vm.state.value.draft.method)
        assertFalse(vm.state.value.canSave)
        vm.edit(NightPreferenceDraft.from(complete())); assertTrue(vm.state.value.canSave)
        vm.save(); runCurrent(); assertEquals(complete(), g.saved.single().second)
        assertEquals(2L, vm.state.value.expected!!.revision); assertFalse(vm.state.value.dirty); vm.clearPrivate()
    }
    @Test fun lateSavePreservesNewerTypingAndAdvancesOnlyItsKnownAcknowledgedBaseline() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        val response = CompletableDeferred<NightPreferenceLoad>(); g.saveBlock = { _, _ -> response.await() }
        vm.save(); runCurrent(); vm.edit(vm.state.value.draft.copy(city = "Newer typing"))
        g.current = NightPreferencesEnvelope(2, complete()); response.complete(loaded(g.current)); runCurrent()
        assertEquals("Newer typing", vm.state.value.draft.city); assertEquals(g.current, vm.state.value.expected)
        assertEquals(NightPreferenceFeedback.SAVED_WITH_DRAFT, vm.state.value.feedback); assertTrue(vm.state.value.canSave)
        vm.clearPrivate()
    }
    @Test fun reloadRefusesAnOldConfirmationAndKeepsTypingThatArrivesDuringTheRead() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        val version = vm.state.value.formVersion
        val response = CompletableDeferred<NightPreferenceLoad>(); g.loadBlock = { response.await() }
        vm.reloadForm(version); runCurrent(); vm.edit(vm.state.value.draft.copy(city = "Keep this"))
        g.current = NightPreferencesEnvelope(2, complete().copy(city = "Other device")); response.complete(loaded(g.current)); runCurrent()
        assertEquals("Keep this", vm.state.value.draft.city); assertEquals(1L, vm.state.value.expected!!.revision)
        assertEquals(NightPreferenceFeedback.DRAFT_KEPT, vm.state.value.feedback)
        val reads = g.loads; vm.reloadForm(version); runCurrent(); assertEquals(reads, g.loads)
        g.loadBlock = null; vm.reloadForm(vm.state.value.formVersion); runCurrent()
        assertEquals("Other device", vm.state.value.draft.city); assertEquals(g.current, vm.state.value.expected); vm.clearPrivate()
    }
    @Test fun lostSaveReplyIsResolvedByCurrentMatchingAccountWithoutASecondPut() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        g.saveBlock = { base, wanted -> g.current = NightPreferencesEnvelope(base.revision + 1, wanted); throw IOException("fixture lost return") }
        vm.save(); runCurrent()
        assertEquals(NightPreferenceFeedback.SAVED, vm.state.value.feedback); assertNull(vm.state.value.pending)
        vm.retrySave(); runCurrent(); assertEquals(1, g.saved.size); vm.clearPrivate()
    }
    @Test fun unknownOutcomeRetainsOriginalIntentForRetryWhileNewTypingIsKept() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        g.saveBlock = { _, _ -> g.loadFailure = true; throw IOException("fixture unknown outcome") }
        vm.save(); runCurrent(); assertNotNull(vm.state.value.pending)
        vm.edit(vm.state.value.draft.copy(city = "Later draft"))
        g.loadFailure = false; g.saveBlock = null; vm.retrySave(); runCurrent()
        assertEquals(2, g.saved.size); assertEquals(g.saved[0], g.saved[1])
        assertEquals("Later draft", vm.state.value.draft.city); assertEquals(NightPreferenceFeedback.SAVED_WITH_DRAFT, vm.state.value.feedback)
        vm.clearPrivate()
    }
    @Test fun conflictDoesNotAutomaticallyAdoptANewerBaselineForAnOldDraft() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        g.current = NightPreferencesEnvelope(2, complete().copy(city = "Other device"))
        vm.save(); runCurrent()
        assertEquals("Cairo", vm.state.value.draft.city); assertEquals(1L, vm.state.value.expected!!.revision)
        assertEquals(NightPreferenceFeedback.CHANGED, vm.state.value.feedback); assertFalse(vm.state.value.canSave)
        assertNull(vm.state.value.pending); vm.clearPrivate()
    }
    @Test fun fetchingPublicDefaultsNeverChangesPrivateFieldsUntilExplicitApply() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        val response = CompletableDeferred<NightPublicDefaults>(); g.publicBlock = { response.await() }
        vm.loadDefaults(); runCurrent(); vm.edit(vm.state.value.draft.copy(city = "Typed while loading"))
        response.complete(NightPublicDefaults(1, true, "MUSLIM_WORLD_LEAGUE", "HANAFI", "SEVENTH_OF_THE_NIGHT", "مصدر", "Source")); runCurrent()
        assertEquals("EGYPTIAN", vm.state.value.draft.method)
        val before = vm.state.value.draft; vm.applyDefaults()
        assertEquals(before.city, vm.state.value.draft.city); assertEquals(before.latitude, vm.state.value.draft.latitude)
        assertEquals(before.longitude, vm.state.value.draft.longitude); assertEquals(before.timezone, vm.state.value.draft.timezone)
        assertEquals("MUSLIM_WORLD_LEAGUE", vm.state.value.draft.method); assertTrue(g.saved.isEmpty())
        g.publicBlock = { NightPublicDefaults(2, false, "DUBAI", "SHAFI", "MIDDLE_OF_THE_NIGHT", "مصدر", "Source") }
        vm.loadDefaults(); runCurrent(); val preserved = vm.state.value.draft; vm.applyDefaults()
        assertEquals(preserved, vm.state.value.draft); assertTrue(g.saved.isEmpty()); vm.clearPrivate()
    }
    @Test fun unreadableCopyRequiresItsReviewedRepairBeforeAnUncertainGoalCanBeConfirmed() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        lateinit var proof: NightPreferenceRepair
        g.saveBlock = { base, wanted ->
            g.current = NightPreferencesEnvelope(base.revision + 1, wanted)
            proof = NightPreferenceRepair(1, namespace, "a".repeat(64), g.current)
            g.loadBlock = { loaded(g.current, proof) }
            throw IOException("fixture uncertain return with unreadable copy")
        }
        vm.save(); runCurrent(); assertNotNull(vm.state.value.pending); assertNotNull(vm.state.value.load!!.repair)
        vm.useAccountCopy(proof.copy(digest = "b".repeat(64))); runCurrent(); assertEquals(0, g.repairs)
        g.repairBlock = { g.loadBlock = null; loaded(g.current) }
        vm.useAccountCopy(proof); runCurrent()
        assertEquals(1, g.repairs); assertEquals(1, g.saved.size); assertNull(vm.state.value.pending)
        assertEquals(NightPreferenceFeedback.SAVED, vm.state.value.feedback); vm.clearPrivate()
    }
    @Test fun actorLossDuringSaveClearsDraftSourceAndPendingIntent() = runTest(dispatcher) {
        val g = Gateway(); val vm = vm(g); runCurrent(); vm.edit(NightPreferenceDraft.from(complete()))
        val response = CompletableDeferred<NightPreferenceLoad>(); g.saveBlock = { _, _ -> response.await() }
        vm.save(); runCurrent(); g.active = false; vm.clearPrivate()
        response.complete(loaded(NightPreferencesEnvelope(2, complete()))); runCurrent()
        assertNull(vm.state.value.load); assertNull(vm.state.value.pending); assertNull(vm.state.value.publicDefaults)
        assertEquals(NightPreferenceDraft(), vm.state.value.draft)
    }
    @Test fun localizedCoordinatesNormalizeWithoutDroppingInvalidSeparators() {
        assertEquals("30.25", NightPreferenceDraft.numeric("٣۰٫۲٥"))
        assertEquals("1,234", NightPreferenceDraft.numeric("1,234"))
        assertTrue(runCatching { NightPreferenceDraft.from(complete()).copy(latitude = "1,234").values() }.isFailure)
        assertEquals(complete(), NightPreferenceDraft.from(complete()).values())
    }
}
