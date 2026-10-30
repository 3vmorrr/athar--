package org.athar.app.nightprayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.core.network.ApiException

internal data class NightPreferenceDraft(val city: String = "", val timezone: String = "", val latitude: String = "", val longitude: String = "",
    val method: String = "", val madhab: String = "", val highLatitude: String = "") {
    fun values(): NightPreferenceValues = NightPreferenceValues(city.trim(),
        NightRules.serverTimezone(java.time.ZoneId.of(timezone.trim())), numeric(latitude).toDouble(), numeric(longitude).toDouble(), method, madhab, highLatitude).validatedForSave()
    companion object {
        fun from(values: NightPreferenceValues) = NightPreferenceDraft(values.city.orEmpty(), values.timezone.orEmpty(), values.latitude?.toString().orEmpty(),
            values.longitude?.toString().orEmpty(), values.calculationMethod.orEmpty(), values.madhab.orEmpty(), values.highLatitudeRule.orEmpty())
        fun numeric(value: String) = value.trim().map { if (it == '\u066b') '.' else Character.digit(it, 10).takeIf { digit -> digit >= 0 }?.let { digit -> ('0'.code + digit).toChar() } ?: it }.joinToString("")
    }
}
internal enum class NightPreferenceError { UNAVAILABLE, UNREADABLE }
internal enum class NightPreferenceFeedback { SAVED, SAVED_WITH_DRAFT, CHANGED, INVALID, UNCONFIRMED, DRAFT_KEPT, RESTORED }
internal data class NightPreferenceSaveIntent(val reviewed: NightPreferencesEnvelope, val wanted: NightPreferenceValues, val formVersion: Long)
internal data class NightPreferencesUiState(
    val load: NightPreferenceLoad? = null,
    val loading: Boolean = true,
    val error: NightPreferenceError? = null,
    val draft: NightPreferenceDraft = NightPreferenceDraft(),
    val expected: NightPreferencesEnvelope? = null,
    val formVersion: Long = 0,
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val pending: NightPreferenceSaveIntent? = null,
    val feedback: NightPreferenceFeedback? = null,
    val publicDefaults: NightPublicDefaults? = null,
    val publicLoading: Boolean = false,
    val publicFailed: Boolean = false,
) {
    val canSave: Boolean get() = load != null && !loading && error == null && !busy && pending == null && load.repair == null && expected == load.snapshot.account && dirty
}
internal interface NightPreferencesGateway {
    fun requireActive()
    suspend fun load(): NightPreferenceLoad
    suspend fun save(reviewed: NightPreferencesEnvelope, wanted: NightPreferenceValues): NightPreferenceLoad
    suspend fun repair(reviewed: NightPreferenceRepair): NightPreferenceLoad
    suspend fun publicDefaults(): NightPublicDefaults
}

/** Private drafts/intents stay in memory. Account responses never overwrite newer user typing. */
internal class NightPreferencesViewModel(private val owner: Long, private val namespace: String, private val gateway: NightPreferencesGateway) : ViewModel() {
    private val mutable = MutableStateFlow(NightPreferencesUiState())
    val state = mutable.asStateFlow()
    private var reading: Job? = null
    private var publicReading: Job? = null
    private var writing: Job? = null
    private var epoch = 0L
    private var readSequence = 0L
    init { refresh() }
    private fun active(): Boolean = try { gateway.requireActive(); true } catch (_: Exception) { clearPrivate(); false }
    fun clearPrivate() {
        epoch++; readSequence++; reading?.cancel(); publicReading?.cancel(); writing?.cancel()
        mutable.value = NightPreferencesUiState(loading = false)
    }
    private fun checked(value: NightPreferenceLoad): NightPreferenceLoad {
        gateway.requireActive(); require(value.snapshot.owner == owner && value.snapshot.namespace == namespace)
        value.repair?.let { require(it.owner == owner && it.namespace == namespace) }
        return value
    }
    fun edit(draft: NightPreferenceDraft) {
        if (!active() || mutable.value.load == null || mutable.value.load?.repair != null) return
        val current = mutable.value
        mutable.value = current.copy(draft = draft, formVersion = current.formVersion + 1,
            dirty = current.expected == null || draft != NightPreferenceDraft.from(current.expected.values), feedback = null)
    }
    fun refresh() = read(force = false, reviewedVersion = null)
    fun reloadForm(reviewedVersion: Long) = read(force = true, reviewedVersion = reviewedVersion)
    private fun read(force: Boolean, reviewedVersion: Long?) {
        if (mutable.value.busy || !active()) return
        if (force && mutable.value.formVersion != reviewedVersion) { mutable.value = mutable.value.copy(feedback = NightPreferenceFeedback.CHANGED); return }
        val sequence = ++readSequence; val captured = epoch; val version = mutable.value.formVersion
        reading?.cancel(); mutable.value = mutable.value.copy(loading = true, error = null)
        reading = viewModelScope.launch {
            try {
                val result = checked(gateway.load())
                if (captured != epoch || sequence != readSequence) return@launch
                val pending = mutable.value.pending
                if (!force && pending != null && matches(result, pending)) { saved(result, pending); return@launch }
                val current = mutable.value
                val replace = if (force) current.formVersion == version else current.expected == null || !current.dirty
                mutable.value = current.copy(load = result, loading = false, error = null,
                    draft = if (replace) NightPreferenceDraft.from(result.snapshot.account.values) else current.draft,
                    expected = if (replace) result.snapshot.account else current.expected,
                    dirty = if (replace) false else current.dirty,
                    pending = if (replace && force) null else current.pending,
                    feedback = when {
                        force && !replace -> NightPreferenceFeedback.DRAFT_KEPT
                        !replace && current.expected != result.snapshot.account -> NightPreferenceFeedback.CHANGED
                        else -> current.feedback
                    })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (active() && captured == epoch && sequence == readSequence) mutable.value = mutable.value.copy(loading = false,
                    error = if (failure is IllegalArgumentException || failure is IllegalStateException) NightPreferenceError.UNREADABLE else NightPreferenceError.UNAVAILABLE)
            }
        }
    }
    fun loadDefaults() {
        if (mutable.value.publicLoading || !active()) return
        val captured = epoch
        mutable.value = mutable.value.copy(publicLoading = true, publicFailed = false, publicDefaults = null)
        publicReading = viewModelScope.launch {
            try {
                val defaults = gateway.publicDefaults(); gateway.requireActive()
                if (captured == epoch) mutable.value = mutable.value.copy(publicLoading = false, publicDefaults = defaults)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active() && captured == epoch) mutable.value = mutable.value.copy(publicLoading = false, publicFailed = true) }
        }
    }
    fun applyDefaults() {
        val defaults = mutable.value.publicDefaults ?: return
        if (!defaults.enabled || mutable.value.publicFailed || !active()) return
        edit(mutable.value.draft.copy(method = defaults.method, madhab = defaults.madhab, highLatitude = defaults.highLatitudeRule))
    }
    fun save() {
        val current = mutable.value
        if (!current.canSave || !active()) return
        val wanted = try { current.draft.values() }
        catch (_: Exception) { mutable.value = current.copy(feedback = NightPreferenceFeedback.INVALID); return }
        val intent = NightPreferenceSaveIntent(requireNotNull(current.expected), wanted, current.formVersion)
        submit(intent)
    }
    fun retrySave() {
        if (mutable.value.busy || mutable.value.loading || !active()) return
        mutable.value.pending?.let(::submit)
    }
    private fun matches(load: NightPreferenceLoad, intent: NightPreferenceSaveIntent) = load.repair == null &&
        load.snapshot.account.revision > intent.reviewed.revision && load.snapshot.account.values == intent.wanted
    private fun saved(result: NightPreferenceLoad, intent: NightPreferenceSaveIntent) {
        val current = mutable.value
        val newerTyping = current.formVersion != intent.formVersion
        mutable.value = current.copy(load = result, loading = false, error = null, busy = false, pending = null,
            expected = result.snapshot.account,
            draft = if (newerTyping) current.draft else NightPreferenceDraft.from(result.snapshot.account.values),
            dirty = newerTyping && current.draft != NightPreferenceDraft.from(result.snapshot.account.values),
            feedback = if (newerTyping) NightPreferenceFeedback.SAVED_WITH_DRAFT else NightPreferenceFeedback.SAVED)
    }
    private fun submit(intent: NightPreferenceSaveIntent) {
        val captured = epoch
        mutable.value = mutable.value.copy(busy = true, pending = intent, feedback = null, error = null)
        writing = viewModelScope.launch {
            try {
                val result = checked(gateway.save(intent.reviewed, intent.wanted))
                if (captured != epoch) return@launch
                if (matches(result, intent)) saved(result, intent)
                else mutable.value = mutable.value.copy(load = result, busy = false, loading = false, pending = null, feedback = NightPreferenceFeedback.CHANGED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (!active() || captured != epoch) return@launch
                try {
                    val result = checked(gateway.load())
                    if (captured != epoch) return@launch
                    if (matches(result, intent)) { saved(result, intent); return@launch }
                    val conflict = failure is ApiException && failure.status == 409
                    val invalid = failure is IllegalArgumentException || failure is ApiException && failure.status == 422
                    val advanced = result.snapshot.account.revision > intent.reviewed.revision && result.repair == null
                    mutable.value = mutable.value.copy(load = result, loading = false, error = null, busy = false,
                        pending = if (conflict || invalid || advanced) null else intent,
                        feedback = when { conflict || advanced -> NightPreferenceFeedback.CHANGED; invalid -> NightPreferenceFeedback.INVALID; else -> NightPreferenceFeedback.UNCONFIRMED })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (unavailable: Exception) {
                    if (active() && captured == epoch) mutable.value = mutable.value.copy(busy = false, loading = false,
                        feedback = NightPreferenceFeedback.UNCONFIRMED, error = if (unavailable is IllegalArgumentException || unavailable is IllegalStateException) NightPreferenceError.UNREADABLE else NightPreferenceError.UNAVAILABLE)
                }
            }
        }
    }
    fun useAccountCopy(reviewed: NightPreferenceRepair) {
        if (mutable.value.busy || mutable.value.loading || !active()) return
        if (mutable.value.load?.repair != reviewed) { mutable.value = mutable.value.copy(feedback = NightPreferenceFeedback.CHANGED); return }
        val captured = epoch
        mutable.value = mutable.value.copy(busy = true, feedback = null)
        writing = viewModelScope.launch {
            try {
                val result = checked(gateway.repair(reviewed))
                if (captured != epoch) return@launch
                val pending = mutable.value.pending
                if (pending != null && matches(result, pending)) { saved(result, pending); return@launch }
                val current = mutable.value; val replace = !current.dirty
                mutable.value = current.copy(load = result, loading = false, error = null, busy = false,
                    expected = if (replace) result.snapshot.account else current.expected,
                    draft = if (replace) NightPreferenceDraft.from(result.snapshot.account.values) else current.draft,
                    feedback = if (!replace && current.expected != result.snapshot.account) NightPreferenceFeedback.CHANGED else NightPreferenceFeedback.RESTORED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (active() && captured == epoch) { mutable.value = mutable.value.copy(busy = false, feedback = NightPreferenceFeedback.CHANGED); refresh() }
            }
        }
    }
}
