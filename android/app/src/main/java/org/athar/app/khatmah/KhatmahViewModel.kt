package org.athar.app.khatmah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.athar.app.core.network.ApiException
import java.time.LocalDate
import java.time.ZoneOffset

internal interface KhatmahGateway {
    fun requireActive()
    suspend fun load(): Pair<KhatmahPlan?, Boolean>
    suspend fun write(draft: KhatmahDraft, expected: Int, action: String?): KhatmahPlan
}

internal enum class KhatmahFeedback { SAVED, SAVED_WITH_DRAFT, INVALID, CONFLICT, UNAVAILABLE, UNCONFIRMED }
internal data class KhatmahUiState(
    val today: LocalDate,
    val ready: Boolean = false,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val plan: KhatmahPlan? = null,
    val baseline: KhatmahPlan? = null,
    val draft: KhatmahDraft = KhatmahDraft.from(null, today),
    val feedback: KhatmahFeedback? = null,
) {
    val dirty: Boolean get() = draft != KhatmahDraft.from(baseline, today)
}

/** Draft identity follows deliberate editing, never a refreshed server revision. */
internal class KhatmahViewModel(
    private val gateway: KhatmahGateway,
    private val now: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) : ViewModel() {
    private val mutable = MutableStateFlow(KhatmahUiState(now()))
    val state = mutable.asStateFlow()
    private var editVersion = 0L
    init { refresh() }

    fun dayChanged() { mutable.value = mutable.value.copy(today = now()) }
    fun edit(value: KhatmahDraft) {
        if (!mutable.value.ready) return
        try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return }
        editVersion++
        mutable.value = mutable.value.copy(draft = value, feedback = null)
    }
    fun clearPrivate() { editVersion++; mutable.value = KhatmahUiState(now(), loading = false) }
    fun refresh(discard: Boolean = false) {
        if (mutable.value.busy || (mutable.value.loading && mutable.value.ready)) return
        val version = editVersion
        viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, feedback = null)
            try {
                gateway.requireActive()
                val (plan, offline) = gateway.load()
                gateway.requireActive()
                val current = mutable.value
                val replace = !current.ready || (discard && version == editVersion)
                mutable.value = current.copy(
                    today = now(), ready = true, loading = false, plan = plan, offline = offline,
                    baseline = if (replace) plan else current.baseline,
                    draft = if (replace) KhatmahDraft.from(plan, now()) else current.draft,
                )
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return@launch }
                mutable.value = mutable.value.copy(loading = false, feedback = KhatmahFeedback.UNAVAILABLE)
            }
        }
    }
    fun save() = submit(null, null)
    fun action(action: String, restart: KhatmahDraft? = null) = submit(action, restart)

    private fun submit(action: String?, restart: KhatmahDraft?) {
        val snapshot = mutable.value
        if (!snapshot.ready || snapshot.busy || snapshot.loading || snapshot.offline) return
        val draft = restart ?: snapshot.draft
        try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return }
        try {
            if (action == null || action == "restart") draft.values(now(), if (action == "restart") null else snapshot.baseline)
            require(action == null || action in setOf("pause", "resume", "finish", "restart"))
        } catch (_: Exception) {
            mutable.value = snapshot.copy(feedback = KhatmahFeedback.INVALID); return
        }
        val version = editVersion
        mutable.value = snapshot.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = gateway.write(draft, snapshot.baseline?.revision ?: 0, action)
                gateway.requireActive()
                val current = mutable.value
                mutable.value = current.copy(
                    plan = saved, baseline = saved, busy = false,
                    feedback = if (version == editVersion) KhatmahFeedback.SAVED else KhatmahFeedback.SAVED_WITH_DRAFT,
                    draft = if (version == editVersion) KhatmahDraft.from(saved, now()) else current.draft,
                )
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                try { gateway.requireActive() } catch (_: Exception) { clearPrivate(); return@launch }
                val feedback = if (failure is ApiException && failure.status == 409) KhatmahFeedback.CONFLICT
                    else if (failure is ApiException && failure.status == 422) KhatmahFeedback.INVALID else KhatmahFeedback.UNCONFIRMED
                mutable.value = mutable.value.copy(busy = false, feedback = feedback)
            }
        }
    }
}
