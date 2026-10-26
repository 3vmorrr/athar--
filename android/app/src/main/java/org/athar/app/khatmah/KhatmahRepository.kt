package org.athar.app.khatmah

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Keep
data class KhatmahServerVersion(
    val startDate: String,
    val targetDate: String,
    val completedPages: Int,
    val status: String,
    val revision: Int,
    val updatedAt: String? = null,
)

@Keep
data class KhatmahPlanState(
    val startDate: String,
    val targetDate: String,
    val completedPages: Int,
    val status: String,
    val revision: Int = 0,
    val localVersion: Long = 1,
    val pending: Boolean = false,
    val conflict: Boolean = false,
    val updatedAt: String? = null,
    val serverConflict: KhatmahServerVersion? = null,
)

data class KhatmahMetrics(
    val totalPages: Int = 604,
    val remainingPages: Int,
    val totalDays: Int,
    val elapsedDays: Int,
    val daysRemaining: Int,
    val startsInDays: Int,
    val dailyPages: Int,
    val plannedDailyPages: Int,
    val expectedPagesToday: Int,
    val pagesBehind: Int,
    val pagesAhead: Int,
    val overdue: Boolean,
    val progressPercent: Int,
)

class KhatmahRepository(
    private val dao: AtharDao,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) {
    private val gson = Gson()
    private val local = Mutex()
    private val remote = Mutex()
    private val key = "khatmah-plan-v2"

    fun observe(owner: Long): Flow<KhatmahPlanState?> =
        dao.observeCache(owner, key)
            .map { row -> row?.let(::decode) }
            .flowOn(Dispatchers.Default)

    suspend fun saved(owner: Long): KhatmahPlanState? =
        withContext(Dispatchers.IO) {
            dao.cache(owner, key)?.let(::decode)
        }

    suspend fun refresh(expectedOwner: Long? = session.userId): KhatmahPlanState? =
        remote.withLock {
            withContext(Dispatchers.IO) {
                val owner = session.requireVerifiedOwner(expectedOwner)
                val current = local.withLock { saved(owner) }
                if (current?.pending == true || current?.conflict == true) {
                    return@withContext current
                }

                val server = fetch(owner)
                session.requireVerifiedOwner(owner)
                local.withLock {
                    session.requireVerifiedOwner(owner)
                    if (server == null) {
                        dao.removeCache(owner, key)
                        null
                    } else {
                        val next = server.toLocal(
                            localVersion = (current?.localVersion ?: 0L) + 1L,
                        )
                        write(owner, next)
                        next
                    }
                }
            }
        }

    suspend fun save(
        startDate: String,
        targetDate: String,
        completedPages: Int,
        expectedOwner: Long? = session.userId,
    ): KhatmahPlanState = withContext(Dispatchers.IO) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        validateEditableDates(startDate, targetDate)
        require(completedPages in 0..604)

        ensureInitializedForEdit(owner)
        val next = local.withLock {
            session.requireVerifiedOwner(owner)
            val current = saved(owner)
            check(current?.conflict != true)
            if (current?.status == "finished") {
                check(
                    startDate == current.startDate &&
                        targetDate == current.targetDate &&
                        completedPages == current.completedPages,
                ) {
                    "Finished plan requires explicit restart"
                }
            }
            val status = current?.status?.takeIf { it in statuses } ?: "active"
            val value = KhatmahPlanState(
                startDate = startDate,
                targetDate = targetDate,
                completedPages = completedPages,
                status = status,
                revision = current?.revision ?: 0,
                localVersion = (current?.localVersion ?: 0L) + 1L,
                pending = true,
                conflict = false,
                updatedAt = current?.updatedAt,
            )
            write(owner, value)
            value
        }
        syncBestEffort(owner)
        saved(owner) ?: next
    }

    suspend fun action(
        action: String,
        restartTargetDate: String? = null,
        expectedOwner: Long? = session.userId,
    ): KhatmahPlanState = withContext(Dispatchers.IO) {
        require(action in setOf("pause", "resume", "restart", "finish"))
        val owner = session.requireVerifiedOwner(expectedOwner)
        ensureInitializedForEdit(owner)

        val next = local.withLock {
            session.requireVerifiedOwner(owner)
            val current = requireNotNull(saved(owner))
            check(!current.conflict)
            val now = today()

            val value = when (action) {
                "pause" -> {
                    check(current.status != "finished")
                    current.copy(
                        status = "paused",
                        localVersion = current.localVersion + 1,
                        pending = true,
                        conflict = false,
                        serverConflict = null,
                    )
                }
                "resume" -> {
                    check(current.status != "finished")
                    check(LocalDate.parse(current.targetDate) >= now)
                    current.copy(
                        status = "active",
                        localVersion = current.localVersion + 1,
                        pending = true,
                        conflict = false,
                        serverConflict = null,
                    )
                }
                "finish" -> {
                    check(current.completedPages == 604)
                    current.copy(
                        status = "finished",
                        localVersion = current.localVersion + 1,
                        pending = true,
                        conflict = false,
                        serverConflict = null,
                    )
                }
                else -> {
                    val target = requireNotNull(restartTargetDate)
                    val start = now.toString()
                    validateEditableDates(start, target)
                    KhatmahPlanState(
                        startDate = start,
                        targetDate = target,
                        completedPages = 0,
                        status = "active",
                        revision = current.revision,
                        localVersion = current.localVersion + 1,
                        pending = true,
                        conflict = false,
                        updatedAt = current.updatedAt,
                    )
                }
            }
            write(owner, value)
            value
        }

        syncBestEffort(owner)
        saved(owner) ?: next
    }

    suspend fun sync(expectedOwner: Long? = session.userId): KhatmahPlanState? =
        remote.withLock {
            withContext(Dispatchers.IO) {
                val owner = session.requireVerifiedOwner(expectedOwner)
                val snapshot = local.withLock { saved(owner) } ?: return@withContext null
                if (!snapshot.pending || snapshot.conflict) return@withContext snapshot

                try {
                    val response = session.request(
                        method = "PUT",
                        path = "me/khatmah",
                        body = mapOf(
                            "start_date" to snapshot.startDate,
                            "target_date" to snapshot.targetDate,
                            "completed_pages" to snapshot.completedPages,
                            "status" to snapshot.status,
                            "expected_revision" to snapshot.revision,
                        ),
                        expectedUser = owner,
                    )
                    val server = parseServer(response)
                        ?: throw IOException("Missing Khatmah plan")
                    session.requireVerifiedOwner(owner)

                    local.withLock {
                        session.requireVerifiedOwner(owner)
                        val latest = saved(owner) ?: return@withLock
                        val next = if (latest.localVersion == snapshot.localVersion) {
                            server.toLocal(localVersion = latest.localVersion + 1)
                        } else {
                            latest.copy(
                                revision = server.revision,
                                localVersion = latest.localVersion + 1,
                                pending = true,
                                conflict = false,
                                updatedAt = server.updatedAt,
                                serverConflict = null,
                            )
                        }
                        write(owner, next)
                    }
                } catch (failure: ApiException) {
                    if (failure.status != 409) throw failure
                    val server = fetch(owner)
                    session.requireVerifiedOwner(owner)
                    local.withLock {
                        session.requireVerifiedOwner(owner)
                        val latest = saved(owner) ?: return@withLock
                        write(
                            owner,
                            latest.copy(
                                conflict = true,
                                pending = true,
                                localVersion = latest.localVersion + 1,
                                serverConflict = server,
                            ),
                        )
                    }
                }
                saved(owner)
            }
        }

    suspend fun useServer(
        expectedOwner: Long? = session.userId,
    ): KhatmahPlanState? = remote.withLock {
        withContext(Dispatchers.IO) {
            val owner = session.requireVerifiedOwner(expectedOwner)
            val server = fetch(owner)
            session.requireVerifiedOwner(owner)
            local.withLock {
                session.requireVerifiedOwner(owner)
                val current = saved(owner)
                if (server == null) {
                    dao.removeCache(owner, key)
                    null
                } else {
                    val next = server.toLocal(
                        localVersion = (current?.localVersion ?: 0L) + 1L,
                    )
                    write(owner, next)
                    next
                }
            }
        }
    }

    suspend fun keepDevice(
        expectedOwner: Long? = session.userId,
    ): KhatmahPlanState? = withContext(Dispatchers.IO) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val server = remote.withLock { fetch(owner) }
        session.requireVerifiedOwner(owner)

        val next = local.withLock {
            session.requireVerifiedOwner(owner)
            val current = saved(owner) ?: return@withLock null
            val value = current.copy(
                revision = server?.revision ?: 0,
                localVersion = current.localVersion + 1,
                pending = true,
                conflict = false,
                updatedAt = server?.updatedAt,
                serverConflict = null,
            )
            write(owner, value)
            value
        }
        syncBestEffort(owner)
        saved(owner) ?: next
    }

    fun metrics(plan: KhatmahPlanState): KhatmahMetrics =
        calculateMetrics(plan, today())

    private suspend fun ensureInitializedForEdit(owner: Long) {
        if (saved(owner) != null) return
        try {
            val server = remote.withLock { fetch(owner) }
            session.requireVerifiedOwner(owner)
            if (server != null) {
                local.withLock {
                    session.requireVerifiedOwner(owner)
                    if (saved(owner) == null) {
                        write(owner, server.toLocal())
                    }
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            if (failure is ApiException && failure.status in listOf(401, 403, 422)) {
                throw failure
            }
            // Offline create/edit is allowed; revision zero represents no known server plan.
        }
    }

    private suspend fun syncBestEffort(owner: Long) {
        try {
            sync(owner)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            if (failure is ApiException && failure.status in listOf(401, 403, 422)) {
                throw failure
            }
            // Encrypted pending state remains for explicit retry or next screen load.
        }
    }

    private suspend fun fetch(owner: Long): KhatmahServerVersion? {
        val data = session.request(
            "GET",
            "me/khatmah",
            expectedUser = owner,
        )
        return parseServer(data)
    }

    private fun parseServer(data: JsonElement): KhatmahServerVersion? {
        if (data.isJsonNull) return null
        val row = data.asJsonObject
        val start = row["start_date"]?.asString ?: throw IOException("Invalid start")
        val target = row["target_date"]?.asString ?: throw IOException("Invalid target")
        validateStoredDates(start, target)
        val total = row["total_pages"]?.asInt ?: throw IOException("Invalid total")
        val completed = row["completed_pages"]?.asInt ?: throw IOException("Invalid completed")
        val status = row["status"]?.asString ?: throw IOException("Invalid status")
        val revision = row["revision"]?.asInt ?: throw IOException("Invalid revision")
        require(total == 604)
        require(completed in 0..604)
        require(status in statuses)
        require(revision > 0)
        return KhatmahServerVersion(
            startDate = start,
            targetDate = target,
            completedPages = completed,
            status = status,
            revision = revision,
            updatedAt = row["updated_at"]?.takeUnless { it.isJsonNull }?.asString,
        )
    }

    private fun decode(row: CachedDocument): KhatmahPlanState {
        val value = requireNotNull(
            gson.fromJson(cipher.open(row.payloadCipher), KhatmahPlanState::class.java),
        )
        require(row.cacheKey == key)
        validateStoredDates(value.startDate, value.targetDate)
        require(value.completedPages in 0..604)
        require(value.status in statuses)
        require(value.revision >= 0)
        require(value.localVersion > 0)
        if (value.conflict) require(value.serverConflict != null)
        value.serverConflict?.let {
            validateStoredDates(it.startDate, it.targetDate)
            require(it.completedPages in 0..604)
            require(it.status in statuses)
            require(it.revision > 0)
        }
        return value
    }

    private suspend fun write(owner: Long, value: KhatmahPlanState) {
        dao.cache(
            CachedDocument(
                userId = owner,
                cacheKey = key,
                payloadCipher = cipher.seal(gson.toJson(value)),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun validateStoredDates(start: String, target: String) {
        val startDate = LocalDate.parse(start)
        val targetDate = LocalDate.parse(target)
        require(!targetDate.isBefore(startDate))
        require(ChronoUnit.DAYS.between(startDate, targetDate) <= 5L * 366L)
    }

    private fun validateEditableDates(start: String, target: String) {
        validateStoredDates(start, target)
        val startDate = LocalDate.parse(start)
        val targetDate = LocalDate.parse(target)
        val now = today()
        require(!startDate.isBefore(now.minusYears(5)))
        require(!startDate.isAfter(now.plusYears(1)))
        require(!targetDate.isBefore(now))
    }

    companion object {
        private val statuses = setOf("active", "paused", "finished")
    }
}

fun calculateMetrics(
    plan: KhatmahPlanState,
    today: LocalDate,
): KhatmahMetrics {
    val total = 604
    val completed = plan.completedPages.coerceIn(0, total)
    val remaining = (total - completed).coerceAtLeast(0)
    val start = LocalDate.parse(plan.startDate)
    val target = LocalDate.parse(plan.targetDate)
    require(!target.isBefore(start))

    val totalDays = ChronoUnit.DAYS.between(start, target).toInt() + 1
    val elapsed: Int
    val remainingDays: Int
    val startsIn: Int

    when {
        today.isBefore(start) -> {
            elapsed = 0
            remainingDays = totalDays
            startsIn = ChronoUnit.DAYS.between(today, start).toInt()
        }
        today.isAfter(target) -> {
            elapsed = totalDays
            remainingDays = 0
            startsIn = 0
        }
        else -> {
            elapsed = ChronoUnit.DAYS.between(start, today).toInt() + 1
            remainingDays = ChronoUnit.DAYS.between(today, target).toInt() + 1
            startsIn = 0
        }
    }

    val expected = if (elapsed <= 0) {
        0
    } else {
        ((total.toLong() * elapsed.toLong()) / totalDays.coerceAtLeast(1))
            .toInt()
            .coerceIn(0, total)
    }
    val daily = if (
        plan.status == "active" &&
        remaining > 0 &&
        remainingDays > 0
    ) {
        ((remaining + remainingDays - 1) / remainingDays)
    } else {
        0
    }

    return KhatmahMetrics(
        remainingPages = remaining,
        totalDays = totalDays,
        elapsedDays = elapsed,
        daysRemaining = remainingDays,
        startsInDays = startsIn,
        dailyPages = daily,
        plannedDailyPages = (total + totalDays - 1) / totalDays.coerceAtLeast(1),
        expectedPagesToday = expected,
        pagesBehind = (expected - completed).coerceAtLeast(0),
        pagesAhead = (completed - expected).coerceAtLeast(0),
        overdue = today.isAfter(target) &&
            remaining > 0 &&
            plan.status != "finished",
        progressPercent = ((completed * 100L) / total).toInt(),
    )
}

private fun KhatmahServerVersion.toLocal(
    localVersion: Long = 1L,
): KhatmahPlanState = KhatmahPlanState(
    startDate = startDate,
    targetDate = targetDate,
    completedPages = completedPages,
    status = status,
    revision = revision,
    localVersion = localVersion,
    pending = false,
    conflict = false,
    updatedAt = updatedAt,
)
