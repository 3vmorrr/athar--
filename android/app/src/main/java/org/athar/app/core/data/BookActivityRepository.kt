package org.athar.app.core.data

import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import org.athar.app.auth.SessionRepository
import org.athar.app.books.BookFiles
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val BOOK_ACTIVITY_PREFIX = "activity-completion:"
internal class BookActivityUnreadable : IllegalStateException("book_activity_unreadable")

internal fun decodeBookActivity(namespace: String, owner: Long, request: String, payload: String, cipher: PrivateCipher): BookCompletionEvent =
    try { BookCompletionEvent.parse(JsonParser.parseString(cipher.open(payload)), namespace, owner, request) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { throw BookActivityUnreadable() }

/** Used inside the account's existing Room export snapshot, never by locale-cache eviction. */
internal suspend fun retainedBookActivity(namespace: String, owner: Long, dao: AtharDao, cipher: PrivateCipher): JsonArray = JsonArray().apply {
    dao.caches(owner, "$BOOK_ACTIVITY_PREFIX%").forEach { row ->
        val request = row.cacheKey.removePrefix(BOOK_ACTIVITY_PREFIX)
        add(decodeBookActivity(namespace, owner, request, row.payloadCipher, cipher).json())
    }
}

/** Trusted scoped file receipts may queue A after A->B; only a current A lease can send them. */
internal class BookActivityRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val files: (Long) -> BookFiles,
    private val requestSync: (Long) -> Unit = {},
    private val onAcknowledged: suspend (Long) -> Unit = {},
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private fun lock(owner: Long) = locks.computeIfAbsent(owner) { require(it > 0); Mutex() }
    private suspend fun captureLocked(owner: Long) {
        val store = files(owner)
        require(store.scope.namespace == namespace && store.scope.owner == owner)
        for ((request, payload) in store.completionReceipts()) {
            val event = decodeBookActivity(namespace, owner, request, payload, cipher)
            database.withTransaction {
                val dao = database.dao(); dao.requireWritable(owner)
                val key = BOOK_ACTIVITY_PREFIX + request
                val previous = dao.cache(owner, key)
                if (previous == null) dao.cache(CachedDocument(owner, key, payload, System.currentTimeMillis()))
                else if (decodeBookActivity(namespace, owner, request, previous.payloadCipher, cipher) != event) throw BookActivityUnreadable()
            }
            // The original ciphertext is durable before either filesystem recovery source is cleared.
            store.acknowledgeCompletion(event.book, request)
        }
    }
    suspend fun capture(owner: Long) = withContext(Dispatchers.IO) {
        try { lock(owner).withLock { captureLocked(owner) } } finally { requestSync(owner) }
    }
    fun observePending(owner: Long): Flow<Int> = database.dao().observeCaches(owner, "$BOOK_ACTIVITY_PREFIX%").map { rows ->
        session.requireVerifiedOwner(owner)
        rows.forEach { decodeBookActivity(namespace, owner, it.cacheKey.removePrefix(BOOK_ACTIVITY_PREFIX), it.payloadCipher, cipher) }
        rows.size
    }.flowOn(Dispatchers.IO)
    suspend fun hasPending(owner: Long): Boolean = withContext(Dispatchers.IO) {
        lock(owner).withLock {
            val lease = session.lease(owner)
            captureLocked(owner)
            val pending = retainedBookActivity(namespace, owner, database.dao(), cipher).size() > 0
            session.requireLease(lease); pending
        }
    }
    suspend fun sync(owner: Long) = withContext(Dispatchers.IO) {
        lock(owner).withLock {
            val lease = session.lease(owner)
            captureLocked(owner)
            val dao = database.dao()
            val pending = dao.caches(owner, "$BOOK_ACTIVITY_PREFIX%").map { row ->
                row to decodeBookActivity(namespace, owner, row.cacheKey.removePrefix(BOOK_ACTIVITY_PREFIX), row.payloadCipher, cipher)
            }
            for ((row, event) in pending) {
                session.requireLease(lease)
                val response = session.request("POST", "me/activity", mapOf("event_id" to event.eventId, "activity_type" to "book_download_completed", "activity_date" to event.date.toString()), expectedUser = owner)
                val recorded = response.asJsonObject["recorded"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
                if (recorded != true) throw IOException("book_activity_receipt_unavailable")
                session.requireLease(lease)
                database.withTransaction {
                    session.requireLease(lease); dao.requireWritable(owner)
                    if (dao.cache(owner, row.cacheKey)?.payloadCipher == row.payloadCipher) dao.removeCache(owner, row.cacheKey)
                }
                onAcknowledged(owner)
            }
        }
    }
}
