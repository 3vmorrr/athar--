package org.athar.app.nightprayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.core.network.ApiException
import org.athar.app.quran.*

internal enum class NightReaderError { UNREADABLE, SOURCE_CHANGED, RANGE_CHANGED, DATA_UNAVAILABLE }
internal enum class NightReaderFeedback { SAVED, CLEARED, CHANGED, UNCONFIRMED, INVALID }
internal data class NightReaderUiState(
    val scope: NightReadingScope? = null,
    val loading: Boolean = true,
    val range: QuranRange? = null,
    val verses: List<AyahText> = emptyList(),
    val snapshot: NightReadingSnapshot? = null,
    val review: NightReadingPosition? = null,
    val reviewRange: QuranRange? = null,
    val error: NightReaderError? = null,
    val busy: Boolean = false,
    val feedback: NightReaderFeedback? = null,
    val scrollIndex: Int? = null,
    val scrollRequest: Long = 0,
) { val ready: Boolean get() = !loading && error == null && snapshot != null && verses.isNotEmpty() }

internal interface NightReaderGateway {
    fun requireActive()
    fun changes(): Flow<NightReadingSnapshot>
    suspend fun data(): QuranData
    suspend fun read(): NightReadingSnapshot
    suspend fun save(index: Int, expected: Long): NightReadingSnapshot
    suspend fun clear(expected: Long): NightReadingSnapshot
    suspend fun reset(expected: Long): NightReadingSnapshot
}

/** Public Quran assets render inside a private, captured range; marker changes never record worship. */
internal class NightReaderViewModel(private val scope: NightReadingScope, private val gateway: NightReaderGateway) : ViewModel() {
    private val mutable = MutableStateFlow(NightReaderUiState(scope))
    val state = mutable.asStateFlow()
    private var data: QuranData? = null
    private var dataFailure = false
    private var positioned = false
    private var readEpoch = 0L
    private var watching: Job? = null
    private var loading: Job? = null
    init { retry() }

    private fun active(): Boolean = try { gateway.requireActive(); true } catch (_: Exception) { clearPrivate(); false }
    fun clearPrivate() {
        readEpoch++; watching?.cancel(); loading?.cancel(); positioned = false
        mutable.value = NightReaderUiState(loading = false)
    }
    fun retry() {
        if (mutable.value.busy || !active()) return
        readEpoch++
        mutable.value = mutable.value.copy(loading = true, error = null, feedback = null, review = null, reviewRange = null)
        watch()
        loadData()
    }
    private fun watch() {
        watching?.cancel()
        watching = viewModelScope.launch {
            try {
                gateway.changes().collect { value ->
                    gateway.requireActive(); merge(value)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { blocking(failure) }
        }
    }
    private fun loadData() {
        if (data == null) {
            loading?.cancel(); dataFailure = false
            loading = viewModelScope.launch {
                try {
                    val loaded = gateway.data(); gateway.requireActive()
                    val range = scope.range(loaded.index); val verses = loaded.range(range)
                    require(verses.map { it.position.globalIndex } == (scope.start..scope.end).toList())
                    require(verses.all { loaded.index.position(it.position.globalIndex) == it.position })
                    data = loaded; dataFailure = false
                    if (mutable.value.error == NightReaderError.DATA_UNAVAILABLE) mutable.value = mutable.value.copy(error = null)
                    publish()
                } catch (failure: CancellationException) { throw failure }
                catch (_: Exception) {
                    if (active()) {
                        dataFailure = true
                        if (mutable.value.error == null || mutable.value.error == NightReaderError.DATA_UNAVAILABLE)
                            mutable.value = mutable.value.copy(loading = false, error = NightReaderError.DATA_UNAVAILABLE, verses = emptyList())
                    }
                }
            }
        }
    }
    private fun merge(value: NightReadingSnapshot) {
        require(value.scope == scope && value.position.scope == scope)
        val previous = mutable.value.snapshot
        if (previous != null && previous.position.version > value.position.version) { publish(); return }
        mutable.value = mutable.value.copy(snapshot = value, error = null, review = null, reviewRange = null)
        publish()
    }
    private fun publish() {
        val current = mutable.value; val loaded = data
        val range = loaded?.let { scope.range(it.index) }
        val oldRange = current.review?.let { review -> loaded?.let { review.scope.range(it.index) } }
        if (current.error != null && current.error != NightReaderError.DATA_UNAVAILABLE) { mutable.value = current.copy(range = range, reviewRange = oldRange); return }
        if (dataFailure) { mutable.value = current.copy(loading = false, error = NightReaderError.DATA_UNAVAILABLE, verses = emptyList()); return }
        val snapshot = current.snapshot
        if (loaded == null || snapshot == null) { mutable.value = current.copy(loading = true, range = range); return }
        val first = !positioned
        positioned = true
        mutable.value = current.copy(loading = false, range = range, verses = loaded.range(requireNotNull(range)),
            scrollIndex = if (first) snapshot.initialIndex else current.scrollIndex,
            scrollRequest = current.scrollRequest + if (first) 1 else 0)
    }
    private fun blocking(failure: Exception) {
        if (!active()) return
        readEpoch++
        val review = (failure as? NightPositionRangeChanged)?.retained
        val validReview = review?.takeIf { it.scope.owner == scope.owner && it.scope.namespace == scope.namespace && it.scope.key == scope.key }
        val error = when {
            validReview != null -> NightReaderError.RANGE_CHANGED
            failure is ApiException && (failure.status == 404 || failure.code in setOf("night_source_changed", "night_source_missing")) -> NightReaderError.SOURCE_CHANGED
            else -> NightReaderError.UNREADABLE
        }
        mutable.value = mutable.value.copy(loading = false, busy = false, error = error, snapshot = null, verses = emptyList(), review = validReview, feedback = null)
        publish()
    }
    fun resume() { val value = mutable.value; if (value.ready && active()) value.snapshot?.position?.index?.let(::scroll) }
    fun start() { if (mutable.value.ready && active()) scroll(scope.start) }
    private fun scroll(index: Int) {
        require(index in scope.start..scope.end)
        mutable.value = mutable.value.copy(scrollIndex = index, scrollRequest = mutable.value.scrollRequest + 1)
    }
    fun save(index: Int, reviewedVersion: Long) {
        val value = mutable.value
        if (!value.ready || value.busy || !active()) return
        if (index !in scope.start..scope.end) { mutable.value = value.copy(feedback = NightReaderFeedback.INVALID); return }
        if (value.snapshot!!.position.version != reviewedVersion) { mutable.value = value.copy(feedback = NightReaderFeedback.CHANGED); return }
        mutate(reviewedVersion, NightReaderFeedback.SAVED) { gateway.save(index, reviewedVersion) }
    }
    fun clear(reviewedVersion: Long) {
        val value = mutable.value
        if (!value.ready || value.busy || !active()) return
        if (value.snapshot!!.position.version != reviewedVersion) { mutable.value = value.copy(feedback = NightReaderFeedback.CHANGED); return }
        mutate(reviewedVersion, NightReaderFeedback.CLEARED) { gateway.clear(reviewedVersion) }
    }
    fun reset(reviewedVersion: Long) {
        val value = mutable.value
        if (value.busy || value.error != NightReaderError.RANGE_CHANGED || value.review?.version != reviewedVersion || !active()) return
        mutate(reviewedVersion, NightReaderFeedback.CLEARED) { gateway.reset(reviewedVersion) }
    }
    private fun mutate(expected: Long, feedback: NightReaderFeedback, operation: suspend () -> NightReadingSnapshot) {
        val epoch = readEpoch
        mutable.value = mutable.value.copy(busy = true, feedback = null)
        viewModelScope.launch {
            try {
                val saved = operation(); gateway.requireActive()
                if (readEpoch != epoch) { mutable.value = mutable.value.copy(busy = false); return@launch }
                merge(saved)
                val newer = (mutable.value.snapshot?.position?.version ?: expected) > saved.position.version
                mutable.value = mutable.value.copy(busy = false, feedback = if (newer) NightReaderFeedback.CHANGED else feedback)
                if (watching?.isActive != true) watch()
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) {
                if (!active()) return@launch
                if (readEpoch != epoch) { mutable.value = mutable.value.copy(busy = false); return@launch }
                if (failure is NightPositionRangeChanged || (failure is ApiException && (failure.status == 404 || failure.code in setOf("night_source_changed", "night_source_missing")))) blocking(failure)
                else {
                    try {
                        val latest = gateway.read(); gateway.requireActive()
                        if (readEpoch != epoch) { mutable.value = mutable.value.copy(busy = false); return@launch }
                        merge(latest)
                        mutable.value = mutable.value.copy(busy = false, feedback = if (failure is ApiException && failure.status == 409) NightReaderFeedback.CHANGED else NightReaderFeedback.UNCONFIRMED)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (unavailable: Exception) { blocking(unavailable) }
                }
            }
        }
    }
}
