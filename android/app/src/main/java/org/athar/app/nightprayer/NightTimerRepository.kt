package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.athar.app.auth.*
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.time.*

/** Captured-generation CAS and atomic measured recording; no wall-time recovery or auto-recording. */
internal class NightTimerRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val nights: NightPrayerRepository,
    private val now: () -> Instant = Instant::now,
) {
    private suspend fun inside(owner: Long): NightTimerFrame = database.dao().cache(owner, NightTimerCodec.KEY)?.let { row ->
        require(row.userId == owner && row.cacheKey == NightTimerCodec.KEY)
        NightTimerCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
    } ?: NightTimerFrame(owner, namespace)

    suspend fun read(lease: SessionLease): NightTimerFrame = withContext(Dispatchers.IO) {
        session.requireLease(lease); val value = inside(lease.owner); session.requireLease(lease); value
    }
    fun observe(lease: SessionLease) = database.dao().observeCache(lease.owner, NightTimerCodec.KEY).map { row ->
        session.requireLease(lease)
        val value = row?.let {
            require(it.userId == lease.owner && it.cacheKey == NightTimerCodec.KEY)
            NightTimerCodec.decode(cipher.open(it.payloadCipher), lease.owner, namespace)
        } ?: NightTimerFrame(lease.owner, namespace)
        session.requireLease(lease); value
    }.protectedReads()

    private suspend fun persist(lease: SessionLease, before: NightTimerFrame, timer: NightMeasuredTimer?): NightTimerFrame {
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
        val after = before.replace(timer)
        database.dao().cache(CachedDocument(lease.owner, NightTimerCodec.KEY, cipher.seal(NightTimerCodec.encode(after)), System.currentTimeMillis()))
        session.requireLease(lease); database.dao().requireWritable(lease.owner); return after
    }
    private suspend fun change(lease: SessionLease, expected: Long, operation: (NightTimerFrame) -> NightMeasuredTimer?): NightTimerFrame = withContext(Dispatchers.IO) {
        database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(lease.owner)
            val before = inside(lease.owner)
            if (before.version != expected) throw ApiException(409, "night_timer_changed", "")
            persist(lease, before, operation(before))
        }
    }
    suspend fun start(lease: SessionLease, expected: Long, identity: NightIdentity, method: String) = change(lease, expected) { before ->
        if (before.timer != null) throw ApiException(409, "night_timer_changed", "")
        NightMeasuredTimer.start(identity, method, now())
    }
    /** Cold restoration is conservative: a running checkpoint becomes paused without adding any gap. */
    suspend fun restore(lease: SessionLease): NightTimerFrame = withContext(Dispatchers.IO) {
        database.withTransaction {
            session.requireLease(lease)
            val before = inside(lease.owner)
            val timer = before.timer
            if (timer?.phase != "running") { session.requireLease(lease); return@withTransaction before }
            database.dao().requireWritable(lease.owner)
            persist(lease, before, timer.recover())
        }
    }
    private fun selected(before: NightTimerFrame, reviewed: NightMeasuredTimer): NightMeasuredTimer {
        if (before.timer != reviewed) throw ApiException(409, "night_timer_changed", "")
        return reviewed
    }
    suspend fun resume(lease: SessionLease, expected: Long, reviewed: NightMeasuredTimer) = change(lease, expected) {
        selected(it, reviewed).resume(now())
    }
    suspend fun checkpoint(lease: SessionLease, expected: Long, reviewed: NightMeasuredTimer, sampled: NightMeasuredTimer) = change(lease, expected) {
        val current = selected(it, reviewed)
        require(sampled == current.checkpoint(sampled.millis, Instant.parse(sampled.activeAt), sampled.reason))
        if (sampled.reason in setOf("clock", "recovered")) require(sampled.millis == current.millis && sampled.activeAt == current.activeAt)
        else require(Instant.parse(sampled.activeAt) <= now().plusSeconds(2))
        sampled
    }
    suspend fun finish(lease: SessionLease, expected: Long, reviewed: NightMeasuredTimer) = change(lease, expected) {
        selected(it, reviewed).finish()
    }
    suspend fun clear(lease: SessionLease, expected: Long) = change(lease, expected) { null }

    /** One timer ID is one recording operation, even across retries, edits/deletes and later timers. */
    suspend fun record(lease: SessionLease, expected: Long, reviewed: NightMeasuredTimer, entry: JsonObject): NightTimerFrame = withContext(Dispatchers.IO) {
        val body = reviewed.body(entry.deepCopy())
        val receipt = NightTimerCodec.receipt(lease.owner, namespace, reviewed, body)
        database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(lease.owner)
            val key = NightTimerCodec.RECORD_PREFIX + reviewed.id
            val existing = database.dao().cache(lease.owner, key)
            if (existing != null) {
                if (decodeNightTimerRecord(existing, lease.owner, namespace, cipher) != receipt) throw ApiException(409, "night_timer_record_changed", "")
                val current = inside(lease.owner); session.requireLease(lease); return@withTransaction current
            }
            val before = inside(lease.owner)
            if (before.version != expected) throw ApiException(409, "night_timer_changed", "")
            selected(before, reviewed)
            require(LocalDate.parse(reviewed.date) <= now().atZone(ZoneId.of(reviewed.zone)).toLocalDate())
            require(Instant.parse(reviewed.ended) <= now().plusSeconds(300))
            if (database.dao().night(lease.owner, reviewed.id) != null) throw ApiException(409, "night_timer_record_changed", "")
            nights.save(body, reviewed.id, lease.owner)
            session.requireLease(lease)
            database.dao().cache(CachedDocument(lease.owner, key, cipher.seal(NightTimerCodec.encode(receipt)), System.currentTimeMillis()))
            persist(lease, before, null)
        }
    }
}

internal fun decodeNightTimerRecord(row: CachedDocument, owner: Long, namespace: String, cipher: PrivateCipher): NightTimerReceipt {
    require(row.userId == owner && row.cacheKey.startsWith(NightTimerCodec.RECORD_PREFIX))
    val receipt = NightTimerCodec.decodeReceipt(cipher.open(row.payloadCipher), owner, namespace)
    require(row.cacheKey == NightTimerCodec.RECORD_PREFIX + receipt.id); return receipt
}

internal suspend fun retainedNightTimers(owner: Long, namespace: String, dao: AtharDao, cipher: PrivateCipher): JsonObject = JsonObject().apply {
    add("device_night_timer", JsonArray().apply {
        dao.cache(owner, NightTimerCodec.KEY)?.let { row ->
            require(row.userId == owner && row.cacheKey == NightTimerCodec.KEY)
            add(JsonParser.parseString(NightTimerCodec.encode(NightTimerCodec.decode(cipher.open(row.payloadCipher), owner, namespace))))
        }
    })
    add("device_night_timer_records", JsonArray().apply {
        dao.caches(owner, NightTimerCodec.RECORD_PREFIX + "%").forEach { row -> add(JsonParser.parseString(NightTimerCodec.encode(decodeNightTimerRecord(row, owner, namespace, cipher)))) }
    })
}
