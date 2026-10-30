package org.athar.app.nightprayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException
import java.time.Instant

internal enum class NightTimerFeedback { CHANGED, UNCONFIRMED, INVALID, CLOCK, OUTSIDE, TOO_SHORT, RECORDED, CLEARED }
internal data class NightTimerUiState(
    val read: ProtectedRead<NightTimerFrame> = ProtectedRead.Loading,
    val displayedMillis: Long = 0,
    val busy: Boolean = false,
    val needsRecovery: Boolean = false,
    val feedback: NightTimerFeedback? = null,
    val recording: NightTimerFrame? = null,
    val frozenEntry: JsonObject? = null,
) { val frame: NightTimerFrame? get() = (read as? ProtectedRead.Ready)?.value }

internal interface NightTimerGateway {
    fun requireActive()
    fun changes(): Flow<ProtectedRead<NightTimerFrame>>
    suspend fun read(): NightTimerFrame
    suspend fun restore(): NightTimerFrame
    suspend fun start(expected: Long, identity: NightIdentity, method: String): NightTimerFrame
    suspend fun resume(expected: Long, selected: NightMeasuredTimer): NightTimerFrame
    suspend fun checkpoint(expected: Long, selected: NightMeasuredTimer, sampled: NightMeasuredTimer): NightTimerFrame
    suspend fun finish(expected: Long, selected: NightMeasuredTimer): NightTimerFrame
    suspend fun clear(expected: Long): NightTimerFrame
    suspend fun record(expected: Long, selected: NightMeasuredTimer, entry: JsonObject): NightTimerFrame
}

/** One captured actor, one runtime anchor. Reentry never converts a persisted uptime into elapsed time. */
internal class NightTimerViewModel(
    private val owner: Long,
    private val namespace: String,
    private val gateway: NightTimerGateway,
    private val elapsed: () -> Long,
    private val wall: () -> Instant,
) : ViewModel() {
    private val mutable = MutableStateFlow(NightTimerUiState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    private var ticker: Job? = null
    private var watching: Job? = null
    private var anchor: NightTimerAnchor? = null
    private var foreground = true
    private var epoch = 0L
    init { retry() }
    private fun active(): Boolean = try { gateway.requireActive(); true } catch (_: Exception) { clearPrivate(); false }
    private fun stopClock() { ticker?.cancel(); ticker = null; anchor = null }
    fun clearPrivate() {
        epoch++; stopClock(); watching?.cancel()
        mutable.value = NightTimerUiState(read = ProtectedRead.Loading)
    }
    private fun checked(frame: NightTimerFrame): NightTimerFrame {
        require(frame.owner == owner && frame.namespace == namespace); gateway.requireActive(); return frame
    }
    private fun blocked() {
        epoch++; stopClock()
        mutable.value = mutable.value.copy(read = ProtectedRead.Unreadable, displayedMillis = 0, busy = false, needsRecovery = false)
    }
    private fun accept(frame: NightTimerFrame, own: Boolean = false): Boolean {
        checked(frame)
        val before = mutable.value.frame
        if (before != null && before.version > frame.version) return false
        require(before == null || before.version != frame.version || before == frame)
        if (!own && before == frame) return true
        if (!own && !mutable.value.busy) stopClock()
        val recovery = frame.timer?.phase == "running" && anchor == null
        mutable.value = mutable.value.copy(read = ProtectedRead.Ready(frame),
            displayedMillis = if (mutable.value.busy && frame.timer?.phase == "running") mutable.value.displayedMillis else frame.timer?.millis ?: 0,
            needsRecovery = recovery,
            feedback = if (!own && before != null && before != frame && !mutable.value.busy) NightTimerFeedback.CHANGED else mutable.value.feedback)
        if (frame.timer?.phase != "running") stopClock()
        return true
    }
    private fun watch() {
        watching?.cancel()
        watching = viewModelScope.launch {
            try {
                gateway.changes().collect { value ->
                    gateway.requireActive()
                    when (value) {
                        is ProtectedRead.Ready -> accept(value.value)
                        ProtectedRead.Unreadable -> blocked()
                        ProtectedRead.Loading -> Unit
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active()) blocked() }
        }
    }
    fun retry() {
        if (mutable.value.busy || !active()) return
        stopClock(); watching?.cancel()
        operation(clock = false, after = { watch() }) { gateway.restore() }
    }
    fun foreground(value: Boolean) {
        foreground = value
        if (value || !active()) return
        val stoppedElapsed = elapsed(); val stoppedWall = wall()
        val stoppedId = anchor?.base?.id ?: mutable.value.frame?.timer?.id
        ticker?.cancel(); ticker = null
        viewModelScope.launch {
            lock.withLock {
                if (!active()) return@withLock
                val frame = mutable.value.frame ?: return@withLock
                val timer = frame.timer?.takeIf { it.phase == "running" } ?: return@withLock
                val currentAnchor = anchor ?: return@withLock
                if ((stoppedId != null && timer.id != stoppedId) || currentAnchor.base != timer) return@withLock
                val sampled = currentAnchor.sample(stoppedElapsed, stoppedWall)
                val paused = if (sampled.phase == "running") sampled.copy(phase = "paused", reason = "background") else sampled
                execute(clock = false) { gateway.checkpoint(frame.version, timer, paused) }
            }
        }
    }
    private fun actionable(version: Long): NightTimerFrame? {
        if (mutable.value.busy || mutable.value.needsRecovery || !active()) return null
        val frame = mutable.value.frame ?: return null
        if (frame.version != version) { mutable.value = mutable.value.copy(feedback = NightTimerFeedback.CHANGED); return null }
        return frame
    }
    fun start(version: Long, identity: NightIdentity, method: String) {
        val frame = actionable(version) ?: return
        if (!foreground || frame.timer != null || mutable.value.recording != null) return
        operation(clock = true, invalid = NightTimerFeedback.OUTSIDE) { gateway.start(version, identity, method) }
    }
    fun resume(version: Long) {
        val frame = actionable(version) ?: return
        val selected = frame.timer?.takeIf { it.phase == "paused" } ?: return
        if (!foreground || mutable.value.recording != null) return
        operation(clock = true, invalid = NightTimerFeedback.OUTSIDE) { gateway.resume(version, selected) }
    }
    fun pause(version: Long) {
        val frame = actionable(version) ?: return
        val selected = frame.timer?.takeIf { it.phase == "running" } ?: return
        val sampled = anchor?.sample(elapsed(), wall()) ?: return
        val paused = if (sampled.phase == "running") sampled.copy(phase = "paused", reason = "user") else sampled
        operation(clock = false) { gateway.checkpoint(version, selected, paused) }
    }
    fun finish(version: Long) {
        val frame = actionable(version) ?: return
        val selected = frame.timer?.takeIf { it.phase != "finished" } ?: return
        val sampled = if (selected.phase == "running") anchor?.sample(elapsed(), wall()) ?: return else selected
        if (sampled.millis < 1000) { mutable.value = mutable.value.copy(feedback = NightTimerFeedback.TOO_SHORT); return }
        operation(clock = false) {
            val checked = if (selected.phase == "running") gateway.checkpoint(version, selected, sampled) else frame
            gateway.finish(checked.version, requireNotNull(checked.timer))
        }
    }
    fun clear(version: Long) {
        actionable(version) ?: return
        if (mutable.value.recording != null) return
        operation(clock = false, feedback = NightTimerFeedback.CLEARED) { gateway.clear(version) }
    }
    fun openRecord(version: Long) {
        val frame = actionable(version) ?: return
        if (frame.timer?.phase == "finished") mutable.value = mutable.value.copy(recording = frame, frozenEntry = null, feedback = null)
    }
    fun closeRecord() { if (!mutable.value.busy) mutable.value = mutable.value.copy(recording = null, frozenEntry = null) }
    fun record(entry: JsonObject? = null) {
        if (mutable.value.busy || !active()) return
        val reviewed = mutable.value.recording ?: return
        val timer = reviewed.timer ?: return
        val intent = mutable.value.frozenEntry?.deepCopy() ?: entry?.deepCopy() ?: return
        try { timer.body(intent) } catch (_: Exception) { mutable.value = mutable.value.copy(feedback = NightTimerFeedback.INVALID); return }
        mutable.value = mutable.value.copy(frozenEntry = intent.deepCopy())
        operation(clock = false, feedback = NightTimerFeedback.RECORDED, invalid = NightTimerFeedback.CLOCK, after = { closeRecord() }) {
            gateway.record(reviewed.version, timer, intent.deepCopy())
        }
    }
    private fun operation(clock: Boolean, feedback: NightTimerFeedback? = null, invalid: NightTimerFeedback = NightTimerFeedback.INVALID, after: () -> Unit = {}, block: suspend () -> NightTimerFrame) {
        if (mutable.value.busy || !active()) return
        mutable.value = mutable.value.copy(busy = true, feedback = null)
        viewModelScope.launch { lock.withLock { execute(clock, feedback, invalid, after, block) } }
    }
    private suspend fun execute(clock: Boolean, feedback: NightTimerFeedback? = null, invalid: NightTimerFeedback = NightTimerFeedback.INVALID, after: () -> Unit = {}, block: suspend () -> NightTimerFrame) {
        if (!active()) return
        val captured = epoch
        mutable.value = mutable.value.copy(busy = true)
        try {
            val result = checked(block())
            if (captured != epoch) return
            val accepted = accept(result, own = true)
            stopClock()
            if (accepted && clock && result.timer?.phase == "running") {
                anchor = NightTimerAnchor(result.timer, elapsed(), wall())
                mutable.value = mutable.value.copy(needsRecovery = false)
                if (foreground) tick()
            }
            mutable.value = mutable.value.copy(busy = false, feedback = if (accepted) feedback else NightTimerFeedback.CHANGED,
                displayedMillis = if (anchor == null) mutable.value.frame?.timer?.millis ?: 0 else mutable.value.displayedMillis,
                needsRecovery = mutable.value.frame?.timer?.phase == "running" && anchor == null)
            after()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (!active() || captured != epoch) return
            stopClock()
            val message = when {
                failure is ApiException && failure.status == 409 -> NightTimerFeedback.CHANGED
                failure is IllegalArgumentException -> invalid
                else -> NightTimerFeedback.UNCONFIRMED
            }
            try {
                // A stale or uncertain callback may inspect, but must not pause a newer timer.
                val fresh = checked(gateway.read())
                if (captured != epoch) return
                accept(fresh, own = true)
                mutable.value = mutable.value.copy(busy = false, feedback = message, needsRecovery = mutable.value.frame?.timer?.phase == "running",
                    displayedMillis = mutable.value.frame?.timer?.millis ?: 0,
                    frozenEntry = if (failure is IllegalArgumentException && invalid == NightTimerFeedback.INVALID) null else mutable.value.frozenEntry)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (active() && captured == epoch) blocked() }
        }
    }
    private fun tick() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (foreground && active()) {
                delay(1000)
                if (mutable.value.busy) continue
                val frame = mutable.value.frame ?: break
                val timer = frame.timer?.takeIf { it.phase == "running" } ?: break
                val currentAnchor = anchor ?: break
                val sampled = currentAnchor.sample(elapsed(), wall())
                mutable.value = mutable.value.copy(displayedMillis = sampled.millis)
                if (sampled.phase != "running" || elapsed() - currentAnchor.elapsed >= 5000) {
                    operation(clock = sampled.phase == "running") { gateway.checkpoint(frame.version, timer, sampled) }
                    if (sampled.phase != "running") break
                }
            }
        }
    }
}
