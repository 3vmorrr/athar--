package org.athar.app.khatmah

import androidx.room.withTransaction
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.auth.SessionLease
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Private durable intents, not disposable HTTP documents. No foreground network is needed to save. */
internal class KhatmahRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val requestSync: (Long) -> Unit = {},
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private fun remote(owner: Long) = locks.getOrPut(owner) { Mutex() }
    private suspend fun readInside(owner: Long): KhatmahJournal {
        val row = database.dao().cache(owner, KhatmahJournalCodec.KEY) ?: return KhatmahJournal(owner, namespace).checked()
        return decodeRetainedKhatmah(namespace, owner, row, cipher)
    }

    suspend fun read(owner: Long): KhatmahJournal = withContext(Dispatchers.IO) {
        val lease = session.lease(owner)
        val journal = readInside(owner)
        session.requireLease(lease); journal
    }

    fun observe(owner: Long, lease: SessionLease = session.lease(owner)): Flow<ProtectedRead<KhatmahJournal>> {
        require(lease.owner == owner)
        return database.dao().observeCache(owner, KhatmahJournalCodec.KEY).map { row ->
            session.requireLease(lease)
            val value = row?.let { decodeRetainedKhatmah(namespace, owner, it, cipher) } ?: KhatmahJournal(owner, namespace).checked()
            session.requireLease(lease); value
        }.protectedReads()
    }

    private suspend fun change(owner: Long, lease: SessionLease = session.lease(owner), transform: (KhatmahJournal) -> KhatmahJournal): KhatmahJournal = withContext(Dispatchers.IO) {
        require(lease.owner == owner)
        database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(owner)
            val before = readInside(owner)
            val after = transform(before).checked()
            require(after.owner == owner && after.namespace == namespace)
            session.requireLease(lease)
            if (before != after) database.dao().cache(CachedDocument(owner, KhatmahJournalCodec.KEY, cipher.seal(KhatmahJournalCodec.encode(after)), System.currentTimeMillis()))
            session.requireLease(lease); after
        }
    }

    suspend fun enqueue(owner: Long, kind: String, draft: KhatmahDraft?, expectedVersion: Long, expectedLease: SessionLease = session.lease(owner)): KhatmahJournal {
        val result = change(owner, expectedLease) { current ->
            if (current.version != expectedVersion || current.blocked) throw ApiException(409, "khatmah_local_changed", "")
            current.append(kind, draft, expectedVersion, today())
        }
        requestSync(owner); return result
    }

    suspend fun hasPending(owner: Long): Boolean = read(owner).let { it.intents.isNotEmpty() && !it.blocked }

    private suspend fun fetch(lease: SessionLease): KhatmahPlan? {
        val response = session.requestLeased(lease, "GET", "me/khatmah")
        return try { KhatmahContract.parse(response) }
        catch (_: Exception) { throw IOException("khatmah_response_unavailable") }
    }

    suspend fun refresh(owner: Long, lease: SessionLease = session.lease(owner)): KhatmahJournal = remote(owner).withLock {
        session.requireLease(lease)
        val snapshot = read(owner)
        if (snapshot.intents.isNotEmpty() || snapshot.blocked) { requestSync(owner); return@withLock snapshot }
        val server = fetch(lease)
        session.requireLease(lease)
        // Enqueue may have happened while GET was in flight; refreshed() keeps those bytes.
        change(owner, lease) { it.refreshed(server) }
    }

    /** Bounded worker slice; prepared expected revisions are durable before the first network byte. */
    suspend fun sync(owner: Long) = remote(owner).withLock {
        val lease = session.lease(owner)
        repeat(8) {
            session.requireLease(lease)
            val sent = change(owner, lease) { current ->
                if (current.blocked) throw ApiException(current.rejected ?: 409, "khatmah_action_required", "")
                current.prepare()
            }
            val head = sent.intents.firstOrNull() ?: return@withLock
            val body = buildMap<String, Any?> {
                put("expected_revision", requireNotNull(head.expectedRevision))
                put("expected_plan_hash", KhatmahContract.fingerprint(owner, sent.acknowledged))
                if (head.kind == "save") {
                    val draft = requireNotNull(head.draft)
                    put("start_date", draft.start); put("target_date", draft.target); put("completed_pages", draft.pages.toInt())
                } else {
                    put("action", head.kind)
                    if (head.kind == "restart") { put("start_date", head.draft!!.start); put("target_date", head.draft.target) }
                }
            }
            try {
                val data = session.requestLeased(lease, if (head.kind == "save") "PUT" else "POST", if (head.kind == "save") "me/khatmah" else "me/khatmah/action", body)
                session.requireLease(lease)
                val response = try { requireNotNull(KhatmahContract.parse(data)) } catch (_: Exception) { throw IOException("khatmah_response_unavailable") }
                change(owner, lease) { current ->
                    try { current.accept(head.id, response) } catch (_: IllegalArgumentException) { throw ApiException(409, "khatmah_acknowledgement_conflict", "") }
                }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: ApiException) {
                session.requireLease(lease)
                if (read(owner).intents.firstOrNull()?.id != head.id) return@repeat
                if (failure.status in setOf(409, 404)) {
                    val server = fetch(lease); session.requireLease(lease)
                    change(owner, lease) { if (it.intents.firstOrNull()?.id == head.id) it.conflict(server) else it }
                    throw ApiException(409, "khatmah_conflict", "")
                }
                if (failure.status in setOf(400, 405, 410, 422)) {
                    change(owner, lease) { if (it.intents.firstOrNull()?.id == head.id) it.reject(failure.status) else it }
                }
                throw failure
            }
        }
        if (hasPending(owner)) requestSync(owner)
    }

    suspend fun useServer(owner: Long, expectedVersion: Long, lease: SessionLease = session.lease(owner)): KhatmahJournal = remote(owner).withLock {
        session.requireLease(lease)
        val server = fetch(lease); session.requireLease(lease)
        change(owner, lease) { current ->
            if (current.version != expectedVersion) throw ApiException(409, "khatmah_local_changed", "")
            if (current.intents.isNotEmpty() && !current.blocked) throw ApiException(409, "khatmah_sync_unsettled", "")
            current.useServer(server, expectedVersion)
        }
    }

    suspend fun keepDevice(owner: Long, expectedVersion: Long, correction: KhatmahDraft? = null, allowRestart: Boolean = false, lease: SessionLease = session.lease(owner)): KhatmahJournal = remote(owner).withLock {
        session.requireLease(lease)
        val server = fetch(lease); session.requireLease(lease)
        val next = change(owner, lease) { current ->
            if (current.version != expectedVersion) throw ApiException(409, "khatmah_local_changed", "")
            val reviewed = if (current.conflicted) current.serverConflict else current.acknowledged
            if (reviewed != server) return@change current.conflict(server)
            current.keepDevice(server, expectedVersion, today(), correction, allowRestart)
        }
        if (next.conflicted) throw ApiException(409, "khatmah_server_changed_again", "")
        requestSync(owner); next
    }
}

internal fun decodeRetainedKhatmah(namespace: String, owner: Long, row: CachedDocument, cipher: PrivateCipher): KhatmahJournal {
    require(row.userId == owner && row.cacheKey == KhatmahJournalCodec.KEY)
    return KhatmahJournalCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
}

internal suspend fun retainedKhatmah(namespace: String, owner: Long, dao: AtharDao, cipher: PrivateCipher): com.google.gson.JsonArray = com.google.gson.JsonArray().apply {
    dao.cache(owner, KhatmahJournalCodec.KEY)?.let { row ->
        val journal = decodeRetainedKhatmah(namespace, owner, row, cipher)
        add(com.google.gson.JsonParser.parseString(KhatmahJournalCodec.encode(journal)))
    }
}
