package org.athar.app.nightprayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException
import org.athar.app.quran.*
import java.time.*
import java.util.UUID

internal data class NightPlanDraft(val startSurah: String = "1", val startAyah: String = "1", val byTarget: Boolean = true, val count: String = "100", val endSurah: String = "1", val endAyah: String = "7") {
    fun range(index: QuranIndex): QuranRange {
        fun number(value: String): Int { require(value.matches(Regex("[0-9]{1,5}"))); return value.toInt() }
        return if (byTarget) index.target(number(startSurah), number(startAyah), number(count))
        else index.range(number(startSurah), number(startAyah), number(endSurah), number(endAyah))
    }
    companion object {
        fun from(range: QuranRange?) = range?.let { NightPlanDraft(it.start.surah.toString(), it.start.ayah.toString(), false, it.verseCount.toString(), it.end.surah.toString(), it.end.ayah.toString()) } ?: NightPlanDraft()
        fun numberInput(value: String) = value.take(16).map { when (it) { in '٠'..'٩' -> '0' + (it - '٠'); in '۰'..'۹' -> '0' + (it - '۰'); else -> it } }.joinToString("")
    }
}

internal enum class NightPlanFeedback { SAVED, SAVED_WITH_DRAFT, CLEARED, RECORDED_LOCALLY, INVALID, CHANGED, UNAVAILABLE, UNCONFIRMED }
internal data class NightRecordDialog(val selected: NightPlanState, val range: QuranRange, val operation: String = UUID.randomUUID().toString(), val date: String = "", val zone: String = NightRules.serverTimezone(ZoneId.systemDefault()), val note: String = "", val frozen: Boolean = false)
internal data class NightPlanUiState(
    val read: ProtectedRead<NightPlanState> = ProtectedRead.Loading,
    val indexReady: Boolean = false,
    val savedRange: QuranRange? = null,
    val draft: NightPlanDraft = NightPlanDraft(),
    val preview: QuranRange? = null,
    val expectedVersion: Long = 0,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val feedback: NightPlanFeedback? = null,
    val record: NightRecordDialog? = null,
) { val ready: Boolean get() = indexReady && read is ProtectedRead.Ready }

internal interface NightPlanGateway {
    fun requireActive()
    fun changes(): Flow<ProtectedRead<NightPlanState>>
    suspend fun index(): QuranIndex
    suspend fun read(): NightPlanState
    suspend fun save(range: QuranRange, expected: Long): NightPlanState
    suspend fun clear(expected: Long): NightPlanState
    suspend fun record(record: NightPlanRecord, expected: Long)
}

/** Durable plan/outbox belongs to repositories; only unsubmitted forms and dialogs live here. */
internal class NightPlanViewModel(private val gateway: NightPlanGateway) : ViewModel() {
    private val mutable = MutableStateFlow(NightPlanUiState())
    val state = mutable.asStateFlow()
    private var canonical: QuranIndex? = null
    private var edits = 0L
    private var observation: Job? = null
    private var loadingIndex: Job? = null
    private var frozenRecord: NightPlanRecord? = null
    init { retry() }
    fun retry() {
        if (mutable.value.busy) return
        observation?.cancel()
        observation = viewModelScope.launch {
            gateway.changes().collect { read ->
                try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return@collect }
                if (read is ProtectedRead.Ready) merge(read.value)
                else mutable.value = mutable.value.copy(read = read, savedRange = null)
            }
        }
        if (canonical == null) {
            loadingIndex?.cancel()
            loadingIndex = viewModelScope.launch {
                try {
                    gateway.requireActive(); val index = gateway.index(); gateway.requireActive(); canonical = index
                    mutable.value = mutable.value.copy(indexReady = true)
                    (mutable.value.read as? ProtectedRead.Ready)?.value?.let { merge(it) }
                } catch (failure: CancellationException) { throw failure }
                catch (_: Exception) { fail(NightPlanFeedback.UNAVAILABLE) }
            }
        }
    }
    private fun merge(value: NightPlanState, force: Boolean = false) {
        val current = mutable.value
        val observed = (current.read as? ProtectedRead.Ready)?.value
        if (!force && observed != null && observed.version > value.version) return
        val range = canonical?.let { value.plan?.range(it) }
        mutable.value = current.copy(read = ProtectedRead.Ready(value), savedRange = range,
            expectedVersion = if (!current.dirty && !current.busy) value.version else current.expectedVersion)
    }
    fun clearPrivate() {
        edits++; observation?.cancel(); loadingIndex?.cancel(); frozenRecord = null
        mutable.value = NightPlanUiState()
    }
    private fun active(): Boolean = try { gateway.requireActive(); true } catch (_: Exception) { clearPrivate(); false }
    fun edit(draft: NightPlanDraft) {
        if (!mutable.value.ready || !active()) return
        edits++; mutable.value = mutable.value.copy(draft = draft, dirty = true, preview = null, feedback = null)
    }
    fun calculate() {
        if (!mutable.value.ready || mutable.value.busy || !active()) return
        try { mutable.value = mutable.value.copy(preview = mutable.value.draft.range(requireNotNull(canonical)), dirty = true, feedback = null) }
        catch (_: Exception) { fail(NightPlanFeedback.INVALID) }
    }
    fun save() {
        val snapshot = mutable.value
        if (!snapshot.ready || snapshot.busy || snapshot.preview == null || !active()) return
        val version = edits
        mutable.value = snapshot.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = gateway.save(snapshot.preview, snapshot.expectedVersion); gateway.requireActive()
                val newer = version != edits
                merge(saved)
                val latest = (mutable.value.read as? ProtectedRead.Ready)?.value
                mutable.value = mutable.value.copy(busy = false,
                    expectedVersion = saved.version, dirty = newer || (latest?.version ?: 0) > saved.version,
                    feedback = when { (latest?.version ?: 0) > saved.version -> NightPlanFeedback.CHANGED; newer -> NightPlanFeedback.SAVED_WITH_DRAFT; else -> NightPlanFeedback.SAVED })
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    /** Deliberate discard only; a failed read or typing during it never replaces the form. */
    fun reloadForm() {
        if (mutable.value.busy || !active()) return
        val version = edits; mutable.value = mutable.value.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val latest = gateway.read(); gateway.requireActive(); merge(latest, force = true)
                if (version == edits) {
                    mutable.value = mutable.value.copy(draft = NightPlanDraft.from(mutable.value.savedRange), preview = null,
                        expectedVersion = latest.version, dirty = false, feedback = null)
                } else mutable.value = mutable.value.copy(feedback = NightPlanFeedback.CHANGED)
                mutable.value = mutable.value.copy(busy = false)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    fun clear(reviewedVersion: Long) {
        val current = mutable.value
        if (!current.ready || current.busy || !active()) return
        if ((current.read as ProtectedRead.Ready).value.version != reviewedVersion) { fail(NightPlanFeedback.CHANGED); return }
        mutable.value = current.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try { val cleared = gateway.clear(reviewedVersion); gateway.requireActive(); merge(cleared); mutable.value = mutable.value.copy(busy = false, expectedVersion = if (mutable.value.dirty) mutable.value.expectedVersion else cleared.version, feedback = NightPlanFeedback.CLEARED) }
            catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    fun openRecord() {
        val value = mutable.value
        if (!value.ready || value.busy || !active()) return
        val selected = (value.read as ProtectedRead.Ready).value
        if (selected.plan == null) return
        frozenRecord = null; mutable.value = value.copy(record = NightRecordDialog(selected, requireNotNull(value.savedRange)), feedback = null)
    }
    fun editRecord(date: String, zone: String, note: String) {
        val dialog = mutable.value.record ?: return
        if (dialog.frozen || mutable.value.busy || !active()) return
        mutable.value = mutable.value.copy(record = dialog.copy(date = date, zone = zone, note = note), feedback = null)
    }
    fun closeRecord() { if (!mutable.value.busy) { frozenRecord = null; mutable.value = mutable.value.copy(record = null) } }
    fun record() {
        val snapshot = mutable.value; val dialog = snapshot.record ?: return
        if (snapshot.busy || !snapshot.ready || !active()) return
        val request = try { frozenRecord ?: NightPlanRecord.create(dialog.selected.owner, dialog.selected.namespace, dialog.operation,
            requireNotNull(dialog.selected.plan), LocalDate.parse(dialog.date), ZoneId.of(dialog.zone), dialog.note) }
        catch (_: Exception) { fail(NightPlanFeedback.INVALID); return }
        frozenRecord = request
        mutable.value = snapshot.copy(busy = true, record = dialog.copy(frozen = true), feedback = null)
        viewModelScope.launch {
            try {
                gateway.record(request, dialog.selected.version); gateway.requireActive()
                frozenRecord = null; mutable.value = mutable.value.copy(busy = false, record = null, feedback = NightPlanFeedback.RECORDED_LOCALLY)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    private suspend fun failed(failure: Exception) {
        if (!active()) return
        val feedback = when { failure is ApiException && failure.status == 409 -> NightPlanFeedback.CHANGED; failure is IllegalArgumentException || failure is DateTimeException -> NightPlanFeedback.INVALID; else -> NightPlanFeedback.UNCONFIRMED }
        if (feedback == NightPlanFeedback.CHANGED) {
            try { val latest = gateway.read(); gateway.requireActive(); merge(latest, force = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!active()) return }
        }
        if (feedback == NightPlanFeedback.INVALID) { frozenRecord = null; mutable.value = mutable.value.copy(record = mutable.value.record?.copy(frozen = false)) }
        fail(feedback)
    }
    private fun fail(feedback: NightPlanFeedback) { if (active()) mutable.value = mutable.value.copy(busy = false, feedback = feedback) }
}
