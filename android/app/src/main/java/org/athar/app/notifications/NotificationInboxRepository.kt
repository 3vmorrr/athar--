package org.athar.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher

data class NotificationInboxLocalState(
    val items: List<NotificationInboxItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val unreadCount: Int = 0,
    val pendingReads: Set<Int> = emptySet(),
    val pendingDeletes: Set<Int> = emptySet(),
    val updatedAtMillis: Long = 0,
) {
    val pendingCount: Int get() = pendingReads.size + pendingDeletes.size

    fun validated(): NotificationInboxLocalState {
        require(items.size <= MAX_ITEMS)
        require(items.map { it.id }.toSet().size == items.size)
        require(items.zipWithNext().all { (a, b) -> a.id > b.id })
        require(unreadCount >= 0)
        require(pendingReads.size <= MAX_PENDING && pendingDeletes.size <= MAX_PENDING)
        require(pendingReads.all { it > 0 } && pendingDeletes.all { it > 0 })
        require(pendingReads.intersect(pendingDeletes).isEmpty())
        require(nextCursor == null || nextCursor.length in 1..180)
        require(updatedAtMillis >= 0)
        return this
    }

    fun visibleAt(nowMillis: Long): NotificationInboxLocalState {
        val expired = items.filter {
            Instant.parse(it.expiresAt).toEpochMilli() <= nowMillis
        }
        if (expired.isEmpty()) return this
        val expiredIds = expired.map { it.id }.toSet()
        val unreadExpired = expired.count { !it.read }
        return copy(
            items = items.filterNot { it.id in expiredIds },
            unreadCount = (unreadCount - unreadExpired).coerceAtLeast(0),
            pendingReads = pendingReads - expiredIds,
        ).validated()
    }

    companion object {
        const val MAX_ITEMS = 200
        const val MAX_PENDING = 200
    }
}

object NotificationInboxLocalMerge {
    fun serverPage(
        current: NotificationInboxLocalState,
        page: NotificationInboxPage,
        reset: Boolean,
        nowMillis: Long,
    ): NotificationInboxLocalState {
        require(current.pendingCount == 0)
        val server = page.items
        val retainedOlder = if (reset) {
            if (server.isEmpty()) {
                if (page.hasMore) current.items else emptyList()
            } else {
                val floor = server.last().id
                current.items.filter { it.id < floor }
            }
        } else {
            current.items
        }
        val merged = (server + retainedOlder)
            .associateBy { it.id }
            .values
            .sortedByDescending { it.id }
            .take(NotificationInboxLocalState.MAX_ITEMS)
        return NotificationInboxLocalState(
            items = merged,
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
            unreadCount = page.unreadCount,
            updatedAtMillis = nowMillis,
        ).visibleAt(nowMillis).validated()
    }

    fun markRead(
        current: NotificationInboxLocalState,
        id: Int,
        nowMillis: Long,
    ): NotificationInboxLocalState {
        require(id > 0)
        val item = current.items.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("inbox_item_missing")
        if (item.read) return current
        val updated = item.copy(
            read = true,
            readAt = Instant.ofEpochMilli(nowMillis).toString(),
        )
        return current.copy(
            items = current.items.map { if (it.id == id) updated else it },
            unreadCount = (current.unreadCount - 1).coerceAtLeast(0),
            pendingReads = current.pendingReads + id,
            updatedAtMillis = nowMillis,
        ).validated()
    }

    fun delete(
        current: NotificationInboxLocalState,
        id: Int,
        nowMillis: Long,
    ): NotificationInboxLocalState {
        require(id > 0)
        val item = current.items.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("inbox_item_missing")
        return current.copy(
            items = current.items.filterNot { it.id == id },
            unreadCount = (
                current.unreadCount - if (item.read) 0 else 1
            ).coerceAtLeast(0),
            pendingReads = current.pendingReads - id,
            pendingDeletes = current.pendingDeletes + id,
            updatedAtMillis = nowMillis,
        ).validated()
    }
}

object NotificationInboxLocalCodec {
    fun encode(state: NotificationInboxLocalState): String {
        state.validated()
        return JsonObject().apply {
            addProperty("version", 1)
            addProperty("next_cursor", state.nextCursor)
            addProperty("has_more", state.hasMore)
            addProperty("unread_count", state.unreadCount)
            addProperty("updated_at_ms", state.updatedAtMillis)
            add("pending_reads", JsonArray().apply { state.pendingReads.sorted().forEach(::add) })
            add("pending_deletes", JsonArray().apply { state.pendingDeletes.sorted().forEach(::add) })
            add("items", JsonArray().apply {
                state.items.forEach { add(encodeItem(it)) }
            })
        }.toString()
    }

    fun decode(raw: String): NotificationInboxLocalState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val items = root.getAsJsonArray("items").map { NotificationInboxContract.item(it) }
            .sortedByDescending { it.id }
        return NotificationInboxLocalState(
            items = items,
            nextCursor = root["next_cursor"]?.takeUnless { it.isJsonNull }?.asString,
            hasMore = root["has_more"].asBoolean,
            unreadCount = root["unread_count"].asInt,
            pendingReads = root.getAsJsonArray("pending_reads").map { it.asInt }.toSet(),
            pendingDeletes = root.getAsJsonArray("pending_deletes").map { it.asInt }.toSet(),
            updatedAtMillis = root["updated_at_ms"].asLong,
        ).validated()
    }

    private fun encodeItem(item: NotificationInboxItem): JsonObject = JsonObject().apply {
        addProperty("id", item.id)
        addProperty("category", item.category)
        addProperty("source_type", item.sourceType)
        if (item.sourceId == null) add("source_id", null) else addProperty("source_id", item.sourceId)
        addProperty("title", item.title)
        addProperty("body", item.body)
        addProperty("destination", item.destinationToken)
        addProperty("read", item.read)
        if (item.readAt == null) add("read_at", null) else addProperty("read_at", item.readAt)
        addProperty("expires_at", item.expiresAt)
        addProperty("created_at", item.createdAt)
    }
}

class NotificationInboxRepository(
    context: Context,
    private val partition: String,
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cacheKey = "private:notification-inbox:v1"
    private val mutex = Mutex()
    private val work = WorkManager.getInstance(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        require(partition.matches(Regex("[a-f0-9]{12}")))
        scope.launch {
            var previousOwner: Long? = null
            session.state.collect { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (owner == null) {
                    previousOwner?.let(::cancelSync)
                    previousOwner = null
                    return@collect
                }
                if (previousOwner != null && previousOwner != owner) {
                    cancelSync(requireNotNull(previousOwner))
                }
                previousOwner = owner
                try {
                    when (val local = read(owner)) {
                        is ProtectedRead.Ready -> if (local.value.pendingCount > 0) scheduleSync(owner)
                        ProtectedRead.Loading, ProtectedRead.Unreadable -> Unit
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) { }
            }
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<NotificationInboxLocalState>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                val value = row?.let {
                    NotificationInboxLocalCodec.decode(cipher.open(it.payloadCipher))
                } ?: NotificationInboxLocalState()
                value.visibleAt(now())
            },
        )
    }.protectedReads()

    suspend fun read(owner: Long): ProtectedRead<NotificationInboxLocalState> {
        return try {
            session.requireVerifiedOwner(owner)
            val value = loadLocked(owner).visibleAt(now())
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(value)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            ProtectedRead.Unreadable
        }
    }

    suspend fun refresh(owner: Long, reset: Boolean): NotificationInboxLocalState = mutex.withLock {
        session.requireVerifiedOwner(owner)
        var current = loadLocked(owner).visibleAt(now())
        current = syncPendingLocked(owner, current)
        val cursor = if (reset) null else current.nextCursor
        val query = buildMap<String, String> {
            put("per_page", "20")
            cursor?.let { put("cursor", it) }
        }
        val page = NotificationInboxContract.page(
            session.request(
                "GET",
                "me/notification-inbox",
                query = query,
                expectedUser = owner,
            ),
        )
        session.requireVerifiedOwner(owner)
        val merged = NotificationInboxLocalMerge.serverPage(current, page, reset, now())
        persistLocked(owner, merged)
        merged
    }

    suspend fun markRead(owner: Long, id: Int): NotificationInboxLocalState = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = loadLocked(owner).visibleAt(now())
        val updated = NotificationInboxLocalMerge.markRead(current, id, now())
        persistLocked(owner, updated)
        scheduleSync(owner)
        updated
    }

    suspend fun delete(owner: Long, id: Int): NotificationInboxLocalState = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = loadLocked(owner).visibleAt(now())
        val updated = NotificationInboxLocalMerge.delete(current, id, now())
        persistLocked(owner, updated)
        scheduleSync(owner)
        updated
    }

    suspend fun syncPending(owner: Long): NotificationInboxLocalState = mutex.withLock {
        session.requireVerifiedOwner(owner)
        syncPendingLocked(owner, loadLocked(owner).visibleAt(now()))
    }

    private suspend fun syncPendingLocked(
        owner: Long,
        initial: NotificationInboxLocalState,
    ): NotificationInboxLocalState {
        var state = initial

        for (id in state.pendingDeletes.sorted()) {
            try {
                session.request(
                    "DELETE",
                    "me/notification-inbox/" + id,
                    expectedUser = owner,
                )
            } catch (failure: ApiException) {
                if (failure.status != 404) throw failure
            }
            session.requireVerifiedOwner(owner)
            state = state.copy(
                pendingDeletes = state.pendingDeletes - id,
                updatedAtMillis = now(),
            ).validated()
            persistLocked(owner, state)
        }

        for (id in state.pendingReads.sorted()) {
            var remote: NotificationInboxItem? = null
            var missing = false
            try {
                remote = NotificationInboxContract.item(
                    session.request(
                        "PUT",
                        "me/notification-inbox/" + id + "/read",
                        expectedUser = owner,
                    ),
                )
            } catch (failure: ApiException) {
                if (failure.status == 404) missing = true else throw failure
            }
            session.requireVerifiedOwner(owner)
            state = state.copy(
                items = if (missing) {
                    state.items.filterNot { it.id == id }
                } else {
                    state.items.map { item ->
                        if (item.id == id && remote != null) remote else item
                    }
                },
                pendingReads = state.pendingReads - id,
                updatedAtMillis = now(),
            ).validated()
            persistLocked(owner, state)
        }
        return state
    }

    private suspend fun loadLocked(owner: Long): NotificationInboxLocalState {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return NotificationInboxLocalState()
        val value = NotificationInboxLocalCodec.decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persistLocked(owner: Long, state: NotificationInboxLocalState) {
        session.requireVerifiedOwner(owner)
        dao.cache(
            CachedDocument(
                owner,
                cacheKey,
                cipher.seal(NotificationInboxLocalCodec.encode(state)),
                now(),
            ),
        )
        session.requireVerifiedOwner(owner)
    }

    private fun scheduleSync(owner: Long) {
        require(owner > 0)
        val request = OneTimeWorkRequestBuilder<NotificationInboxSyncWorker>()
            .setInputData(
                workDataOf(
                    NotificationInboxSyncWorker.ACTOR to owner,
                    NotificationInboxSyncWorker.ENVIRONMENT to partition,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(syncTag(owner))
            .build()
        work.enqueueUniqueWork(
            "notification-inbox-sync-" + partition + "-" + owner,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelSync(owner: Long) {
        if (owner > 0) work.cancelAllWorkByTag(syncTag(owner))
    }

    private fun syncTag(owner: Long) = "notification-inbox-sync-" + partition + "-" + owner
}

class NotificationInboxSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getLong(ACTOR, -1)
        val environment = inputData.getString(ENVIRONMENT)
        if (owner <= 0 || environment == null || !environment.matches(Regex("[a-f0-9]{12}"))) {
            return Result.failure(workDataOf("reason" to "invalid_input"))
        }
        val graph = (applicationContext as AtharApplication).graph
        if (environment != graph.partition) {
            return Result.failure(workDataOf("reason" to "stale_environment"))
        }
        return try {
            if (graph.session.state.value is AuthState.Restoring) graph.session.restore()
            graph.session.requireVerifiedOwner(owner)
            graph.notificationInbox.syncPending(owner)
            Result.success()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ApiException) {
            if (
                runAttemptCount < 5 &&
                (failure.status == 408 || failure.status == 425 || failure.status == 429 || failure.status >= 500)
            ) Result.retry()
            else Result.failure(workDataOf("reason" to failure.code))
        } catch (_: IOException) {
            if (runAttemptCount < 5) Result.retry()
            else Result.failure(workDataOf("reason" to "network_unavailable"))
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to "sync_failed"))
        }
    }

    companion object {
        const val ACTOR = "actor_id"
        const val ENVIRONMENT = "environment"
    }
}
