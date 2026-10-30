package org.athar.app.khatmah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException
import java.time.LocalDate
import java.time.ZoneOffset

internal data class KhatmahRead(val journal: KhatmahJournal, val offline: Boolean = false)
internal interface KhatmahGateway {
    fun requireActive()
    suspend fun load(): KhatmahRead
    fun changes(): Flow<ProtectedRead<KhatmahJournal>> = emptyFlow()
    suspend fun write(draft: KhatmahDraft, expected: Long, action: String?): KhatmahJournal
    suspend fun useServer(expected: Long): KhatmahJournal
    suspend fun keepDevice(expected: Long, draft: KhatmahDraft): KhatmahJournal
}

internal enum class KhatmahFeedback { SAVED, SAVED_WITH_DRAFT, LOCAL_SAVED, LOCAL_WITH_DRAFT, INVALID, CONFLICT, UNAVAILABLE, UNCONFIRMED }
internal data class KhatmahUiState(
    val today: LocalDate,
    val ready: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val unreadable: Boolean = false,
    val journal: KhatmahJournal? = null,
    val plan: KhatmahPlan? = null,
    val baseline: KhatmahPlan? = null,
    val baselineVersion: Long = 0,
    val draft: KhatmahDraft = KhatmahDraft.from(null, today),
    val feedback: KhatmahFeedback? = null,
) {
    val dirty: Boolean get() = draft != KhatmahDraft.from(baseline, today)
    val blocked: Boolean get() = journal?.blocked == true
}

/** Repository owns durable intents; this holder owns only the not-yet-submitted form. */
internal class KhatmahViewModel(
    private val gateway: KhatmahGateway,
    private val now: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) : ViewModel() {
    private val mutable = MutableStateFlow(KhatmahUiState(now()))
    val state = mutable.asStateFlow()
    private var editVersion = 0L
    private var observation: Job? = null
    init { watch(); refresh() }

    private fun watch() {
        observation?.cancel()
        observation = viewModelScope.launch {
            gateway.changes().collect { read ->
                try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return@collect }
                when (read) {
                    ProtectedRead.Loading -> Unit
                    ProtectedRead.Unreadable -> mutable.value = mutable.value.copy(unreadable = true, loading = false, feedback = KhatmahFeedback.UNAVAILABLE)
                    is ProtectedRead.Ready -> {
                        val journal = read.value
                        if (!mutable.value.ready && !journal.serverKnown && journal.intents.isEmpty()) return@collect
                        merge(journal)
                    }
                }
            }
        }
    }
    private fun merge(journal: KhatmahJournal, reset: Boolean = false, preserveBaseline: Boolean = false) {
        val current = mutable.value
        val plan = journal.projected()
        val replace = !current.ready || reset || (!preserveBaseline && !current.dirty && !current.busy)
        val sameVersion = current.baselineVersion == journal.version
        val acknowledged = current.journal?.intents?.isNotEmpty() == true && journal.intents.isEmpty() && !journal.blocked
        val feedback = if (acknowledged) when (current.feedback) {
            KhatmahFeedback.LOCAL_SAVED -> KhatmahFeedback.SAVED
            KhatmahFeedback.LOCAL_WITH_DRAFT -> KhatmahFeedback.SAVED_WITH_DRAFT
            else -> current.feedback
        } else current.feedback
        mutable.value = current.copy(
            today = now(), ready = true, unreadable = false, journal = journal, plan = plan,
            baseline = if (replace || sameVersion) plan else current.baseline,
            baselineVersion = if (replace) journal.version else current.baselineVersion,
            draft = if (replace) KhatmahDraft.from(plan, now()) else current.draft,
            offline = if (acknowledged) false else current.offline, feedback = feedback,
        )
    }
    fun dayChanged() { mutable.value = mutable.value.copy(today = now()) }
    fun edit(value: KhatmahDraft) {
        if (!mutable.value.ready || mutable.value.unreadable) return
        try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return }
        editVersion++; mutable.value = mutable.value.copy(draft = value, feedback = null)
    }
    fun clearPrivate() { editVersion++; mutable.value = KhatmahUiState(now(), loading = false) }
    fun refresh(discard: Boolean = false) {
        if (mutable.value.busy || (mutable.value.loading && mutable.value.ready)) return
        if (observation?.isActive != true) watch()
        val version = editVersion
        mutable.value = mutable.value.copy(loading = true, feedback = null)
        viewModelScope.launch {
            try {
                gateway.requireActive(); val result = gateway.load(); gateway.requireActive()
                merge(result.journal, reset = discard && version == editVersion, preserveBaseline = discard && version != editVersion)
                mutable.value = mutable.value.copy(loading = false, offline = result.offline)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return@launch }
                mutable.value = mutable.value.copy(loading = false, feedback = KhatmahFeedback.UNAVAILABLE)
            }
        }
    }
    fun save() = submit(null, null, null)
    fun action(action: String, restart: KhatmahDraft? = null, reviewedVersion: Long? = null) = submit(action, restart, reviewedVersion)

    private fun submit(action: String?, restart: KhatmahDraft?, reviewedVersion: Long?) {
        val snapshot = mutable.value
        if (!snapshot.ready || snapshot.busy || snapshot.loading || snapshot.unreadable || snapshot.blocked) return
        if (reviewedVersion != null && reviewedVersion != snapshot.baselineVersion) {
            mutable.value = snapshot.copy(feedback = KhatmahFeedback.CONFLICT); return
        }
        val draft = restart ?: snapshot.draft
        try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return }
        try {
            if (action == null || action == "restart") draft.values(now(), if (action == "restart") null else snapshot.baseline)
            require(action == null || action in setOf("pause", "resume", "finish", "restart"))
        } catch (_: Exception) { mutable.value = snapshot.copy(feedback = KhatmahFeedback.INVALID); return }
        val version = editVersion
        mutable.value = snapshot.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = gateway.write(draft, snapshot.baselineVersion, action); gateway.requireActive()
                accepted(saved, version)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    private fun accepted(saved: KhatmahJournal, version: Long) {
        val current = mutable.value
        val observed = current.journal
        if (observed != null && observed.version > saved.version) {
            mutable.value = current.copy(busy = false, feedback = KhatmahFeedback.CONFLICT)
            return
        }
        // Room may deliver the worker's ACK before the enqueue caller resumes.
        val latest = if (observed != null && observed.version == saved.version &&
            KhatmahJournal.sameValues(observed.projected(), saved.projected()) &&
            (observed.blocked || (observed.intents.size < saved.intents.size &&
                (observed.acknowledged?.revision ?: 0) >= (saved.acknowledged?.revision ?: 0)))) observed else saved
        val newerDraft = version != editVersion
        val pending = latest.intents.isNotEmpty()
        mutable.value = current.copy(
            ready = true, journal = latest, plan = latest.projected(), baseline = latest.projected(), baselineVersion = latest.version,
            busy = false, unreadable = false,
            feedback = when {
                latest.blocked -> KhatmahFeedback.CONFLICT
                pending && newerDraft -> KhatmahFeedback.LOCAL_WITH_DRAFT
                pending -> KhatmahFeedback.LOCAL_SAVED
                newerDraft -> KhatmahFeedback.SAVED_WITH_DRAFT
                else -> KhatmahFeedback.SAVED
            },
            draft = if (newerDraft) current.draft else KhatmahDraft.from(latest.projected(), now()),
        )
    }
    private fun failed(failure: Exception) {
        try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return }
        val feedback = when {
            failure is ApiException && failure.status == 409 -> KhatmahFeedback.CONFLICT
            failure is ApiException && failure.status == 422 -> KhatmahFeedback.INVALID
            failure is IllegalArgumentException -> KhatmahFeedback.INVALID
            else -> KhatmahFeedback.UNCONFIRMED
        }
        mutable.value = mutable.value.copy(busy = false, feedback = feedback)
    }
    fun useServer(reviewedVersion: Long) {
        val snapshot = mutable.value
        if (snapshot.busy || snapshot.loading || snapshot.unreadable) return
        if (snapshot.journal?.version != reviewedVersion) { failed(ApiException(409, "review_changed", "")); return }
        val version = editVersion
        mutable.value = snapshot.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = gateway.useServer(reviewedVersion); gateway.requireActive()
                merge(saved, reset = version == editVersion, preserveBaseline = version != editVersion)
                mutable.value = mutable.value.copy(busy = false, offline = false, feedback = if (version == editVersion) KhatmahFeedback.SAVED else KhatmahFeedback.CONFLICT)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
    fun keepDevice(reviewedVersion: Long) {
        val snapshot = mutable.value
        if (snapshot.busy || snapshot.loading || snapshot.unreadable) return
        if (snapshot.journal?.version != reviewedVersion) { failed(ApiException(409, "review_changed", "")); return }
        val version = editVersion
        mutable.value = snapshot.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = gateway.keepDevice(reviewedVersion, snapshot.draft); gateway.requireActive(); accepted(saved, version)
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { failed(failure) }
        }
    }
}
