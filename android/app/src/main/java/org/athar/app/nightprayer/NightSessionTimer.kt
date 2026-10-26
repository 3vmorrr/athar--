package org.athar.app.nightprayer

import android.os.SystemClock
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.security.PrivateCipher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Keep
data class NightTimerSnapshot(
    val nightDate: String,
    val timezone: String,
    val calculationMethod: String,
    val maghribAt: String,
    val fajrAt: String,
    val startedAt: String,
    val lastActiveAt: String,
    val endedAt: String? = null,
    val accumulatedMillis: Long = 0L,
    val phase: String = "paused",
    val pauseReason: String? = null,
    val version: Long = 1L,
)

data class NightTimerUiState(
    val loading: Boolean = true,
    val unreadable: Boolean = false,
    val snapshot: NightTimerSnapshot? = null,
    val displayedMillis: Long = 0L,
    val busy: Boolean = false,
    val error: String? = null,
)

internal class NightTimerStore(
    private val dao: AtharDao,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
) {
    private val gson = Gson()
    private val key = "night-session-timer-v1"

    suspend fun load(owner: Long): NightTimerSnapshot? =
        withContext(Dispatchers.IO) {
            session.requireVerifiedOwner(owner)
            dao.cache(owner, key)?.let(::decode).also {
                session.requireVerifiedOwner(owner)
            }
        }

    suspend fun write(owner: Long, value: NightTimerSnapshot) =
        withContext(Dispatchers.IO) {
            session.requireVerifiedOwner(owner)
            validate(value)
            dao.cache(
                CachedDocument(
                    userId = owner,
                    cacheKey = key,
                    payloadCipher = cipher.seal(gson.toJson(value)),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            session.requireVerifiedOwner(owner)
        }

    suspend fun clear(owner: Long) = withContext(Dispatchers.IO) {
        session.requireVerifiedOwner(owner)
        dao.removeCache(owner, key)
        session.requireVerifiedOwner(owner)
    }

    private fun decode(row: CachedDocument): NightTimerSnapshot {
        require(row.cacheKey == key)
        val value = requireNotNull(
            gson.fromJson(
                cipher.open(row.payloadCipher),
                NightTimerSnapshot::class.java,
            ),
        )
        validate(value)
        return value
    }

    private fun validate(value: NightTimerSnapshot) {
        require(value.phase in phases)
        require(
            value.pauseReason == null ||
                value.pauseReason in pauseReasons,
        )
        val night = LocalDate.parse(value.nightDate)
        val zone = ZoneId.of(value.timezone)
        val maghrib = Instant.parse(value.maghribAt)
        val fajr = Instant.parse(value.fajrAt)
        val started = Instant.parse(value.startedAt)
        val active = Instant.parse(value.lastActiveAt)
        val ended = value.endedAt?.let(Instant::parse)

        require(fajr > maghrib)
        require(maghrib.atZone(zone).toLocalDate() == night)
        require(started >= maghrib && started <= fajr)
        require(active >= started && active <= fajr)
        require(ended == null || (ended >= started && ended <= fajr))
        require(value.accumulatedMillis in 0L..86_400_000L)
        require(value.version > 0L)
        if (value.phase == "finished") {
            require(ended != null)
            require(value.accumulatedMillis >= 1_000L)
        }
    }

    companion object {
        private val phases = setOf("running", "paused", "finished")
        private val pauseReasons = setOf(
            "user",
            "background",
            "recovered",
            "boundary",
        )
    }
}

class NightTimerViewModel(
    private val graph: AppGraph,
    private val owner: Long,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallNow: () -> Instant = Instant::now,
) : ViewModel() {
    private val store = NightTimerStore(
        dao = graph.database.dao(),
        cipher = graph.cipher,
        session = graph.session,
    )
    private val lock = Mutex()
    private val mutable = MutableStateFlow(NightTimerUiState())
    val state = mutable.asStateFlow()

    private var ticker: Job? = null
    private var runningBaseMillis = 0L
    private var runningSinceElapsed: Long? = null
    private var lastCheckpointElapsed = 0L

    init {
        restore()
    }

    fun restore() {
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.value = NightTimerUiState(
                loading = true,
                busy = true,
            )
            try {
                graph.session.requireVerifiedOwner(owner)
                val retained = store.load(owner)
                val recovered = if (retained?.phase == "running") {
                    retained.copy(
                        phase = "paused",
                        pauseReason = "recovered",
                        version = retained.version + 1,
                    ).also { store.write(owner, it) }
                } else {
                    retained
                }
                mutable.value = NightTimerUiState(
                    loading = false,
                    snapshot = recovered,
                    displayedMillis = recovered?.accumulatedMillis ?: 0L,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = NightTimerUiState(
                    loading = false,
                    unreadable = true,
                )
            }
        }
    }

    fun start(
        identity: NightIdentity,
        calculationMethod: String,
    ) {
        if (!identity.inProgress || mutable.value.busy) return
        viewModelScope.launch {
            lock.withLock {
                val now = wallNow()
                if (!identity.context.contains(now)) {
                    mutable.value = mutable.value.copy(error = "outside_night")
                    return@withLock
                }
                try {
                    mutable.value = mutable.value.copy(busy = true, error = null)
                    graph.session.requireVerifiedOwner(owner)
                    val snapshot = NightTimerSnapshot(
                        nightDate = identity.nightDate.toString(),
                        timezone = NightRules.serverTimezone(identity.context.zone),
                        calculationMethod = calculationMethod,
                        maghribAt = identity.context.maghrib.toString(),
                        fajrAt = identity.context.fajr.toString(),
                        startedAt = now.toString(),
                        lastActiveAt = now.toString(),
                        accumulatedMillis = 0L,
                        phase = "running",
                        pauseReason = null,
                    )
                    store.write(owner, snapshot)
                    runningBaseMillis = 0L
                    runningSinceElapsed = elapsedRealtime()
                    lastCheckpointElapsed = runningSinceElapsed ?: 0L
                    mutable.value = NightTimerUiState(
                        loading = false,
                        snapshot = snapshot,
                        displayedMillis = 0L,
                    )
                    launchTicker()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        error = "storage",
                    )
                }
            }
        }
    }

    fun pauseByUser() = pause("user")
    fun pauseForBackground() = pause("background")

    private fun pause(reason: String) {
        val current = mutable.value.snapshot ?: return
        if (current.phase != "running") return
        viewModelScope.launch {
            lock.withLock {
                persistPause(reason, useCurrentElapsed = true)
            }
        }
    }

    fun resume() {
        val current = mutable.value.snapshot ?: return
        if (current.phase != "paused" || mutable.value.busy) return
        viewModelScope.launch {
            lock.withLock {
                try {
                    val now = wallNow()
                    val fajr = Instant.parse(current.fajrAt)
                    if (now >= fajr) {
                        mutable.value = mutable.value.copy(error = "outside_night")
                        return@withLock
                    }
                    mutable.value = mutable.value.copy(busy = true, error = null)
                    graph.session.requireVerifiedOwner(owner)
                    val next = current.copy(
                        phase = "running",
                        pauseReason = null,
                        lastActiveAt = now.toString(),
                        version = current.version + 1,
                    )
                    store.write(owner, next)
                    runningBaseMillis = current.accumulatedMillis
                    runningSinceElapsed = elapsedRealtime()
                    lastCheckpointElapsed = runningSinceElapsed ?: 0L
                    mutable.value = mutable.value.copy(
                        snapshot = next,
                        displayedMillis = current.accumulatedMillis,
                        busy = false,
                    )
                    launchTicker()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        error = "storage",
                    )
                }
            }
        }
    }

    fun finish() {
        val current = mutable.value.snapshot ?: return
        if (current.phase == "finished" || mutable.value.busy) return
        viewModelScope.launch {
            lock.withLock {
                try {
                    mutable.value = mutable.value.copy(busy = true, error = null)
                    graph.session.requireVerifiedOwner(owner)
                    val now = wallNow()
                    val fajr = Instant.parse(current.fajrAt)
                    val total = if (current.phase == "running") {
                        currentElapsed()
                    } else {
                        current.accumulatedMillis
                    }
                    if (total < 1_000L) {
                        mutable.value = mutable.value.copy(
                            busy = false,
                            error = "too_short",
                        )
                        return@withLock
                    }

                    val end = if (current.phase == "running") {
                        if (now > fajr) {
                            mutable.value = mutable.value.copy(
                                busy = false,
                                error = "outside_night",
                            )
                            return@withLock
                        }
                        now
                    } else {
                        Instant.parse(current.lastActiveAt)
                    }

                    ticker?.cancel()
                    ticker = null
                    runningSinceElapsed = null
                    val next = current.copy(
                        accumulatedMillis = total,
                        lastActiveAt = end.toString(),
                        endedAt = end.toString(),
                        phase = "finished",
                        pauseReason = current.pauseReason,
                        version = current.version + 1,
                    )
                    store.write(owner, next)
                    mutable.value = NightTimerUiState(
                        loading = false,
                        snapshot = next,
                        displayedMillis = total,
                    )
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        error = "storage",
                    )
                }
            }
        }
    }

    fun clear() {
        if (mutable.value.busy) return
        viewModelScope.launch {
            lock.withLock {
                try {
                    mutable.value = mutable.value.copy(busy = true, error = null)
                    graph.session.requireVerifiedOwner(owner)
                    ticker?.cancel()
                    ticker = null
                    runningSinceElapsed = null
                    store.clear(owner)
                    mutable.value = NightTimerUiState(loading = false)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        busy = false,
                        error = "storage",
                    )
                }
            }
        }
    }

    fun clearUnreadable() {
        if (!mutable.value.unreadable || mutable.value.busy) return
        viewModelScope.launch {
            try {
                mutable.value = mutable.value.copy(busy = true)
                store.clear(owner)
                mutable.value = NightTimerUiState(loading = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    busy = false,
                    error = "storage",
                )
            }
        }
    }

    fun markRecorded() = clear()

    private fun launchTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val current = mutable.value.snapshot ?: break
                if (current.phase != "running") break

                val now = wallNow()
                if (now >= Instant.parse(current.fajrAt)) {
                    lock.withLock {
                        persistPause(
                            reason = "boundary",
                            useCurrentElapsed = false,
                            activeAt = Instant.parse(current.fajrAt),
                        )
                    }
                    break
                }

                val total = currentElapsed()
                mutable.value = mutable.value.copy(
                    displayedMillis = total,
                )
                val elapsed = elapsedRealtime()
                if (elapsed - lastCheckpointElapsed >= 5_000L) {
                    checkpoint(total, now)
                    lastCheckpointElapsed = elapsed
                }
            }
        }
    }

    private suspend fun checkpoint(total: Long, activeAt: Instant) {
        val current = mutable.value.snapshot ?: return
        if (current.phase != "running") return
        try {
            graph.session.requireVerifiedOwner(owner)
            val next = current.copy(
                accumulatedMillis = total,
                lastActiveAt = activeAt.toString(),
                version = current.version + 1,
            )
            store.write(owner, next)
            mutable.value = mutable.value.copy(snapshot = next)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // The previous encrypted checkpoint remains durable.
        }
    }

    private suspend fun persistPause(
        reason: String,
        useCurrentElapsed: Boolean,
        activeAt: Instant = wallNow(),
    ) {
        val current = mutable.value.snapshot ?: return
        if (current.phase != "running") return
        try {
            mutable.value = mutable.value.copy(busy = true, error = null)
            graph.session.requireVerifiedOwner(owner)
            val total = if (useCurrentElapsed) {
                currentElapsed()
            } else {
                mutable.value.displayedMillis
            }
            ticker?.cancel()
            ticker = null
            runningSinceElapsed = null
            val next = current.copy(
                accumulatedMillis = total,
                lastActiveAt = activeAt.toString(),
                phase = "paused",
                pauseReason = reason,
                version = current.version + 1,
            )
            store.write(owner, next)
            mutable.value = NightTimerUiState(
                loading = false,
                snapshot = next,
                displayedMillis = total,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(
                busy = false,
                error = "storage",
            )
        }
    }

    private fun currentElapsed(): Long {
        val start = runningSinceElapsed ?: return runningBaseMillis
        return (
            runningBaseMillis +
                (elapsedRealtime() - start).coerceAtLeast(0L)
            ).coerceAtMost(86_400_000L)
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}
