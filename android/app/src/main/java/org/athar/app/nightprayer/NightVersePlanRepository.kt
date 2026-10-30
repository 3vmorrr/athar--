package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionLease
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import org.athar.app.quran.QuranIndex
import org.athar.app.quran.QuranRange
import java.time.*

/** Actor-generation/deletion-fenced local planning; no save/clear/copy fabricates worship history. */
internal class NightVersePlanRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val nights: NightPrayerRepository,
    private val index: suspend () -> QuranIndex,
    private val now: () -> Instant = Instant::now,
) {
    private suspend fun readInside(owner: Long): NightPlanState = database.dao().cache(owner, NightPlanCodec.KEY)?.let {
        require(it.userId == owner && it.cacheKey == NightPlanCodec.KEY)
        NightPlanCodec.decode(cipher.open(it.payloadCipher), owner, namespace)
    } ?: NightPlanState(owner, namespace)

    fun observe(lease: SessionLease) = database.dao().observeCache(lease.owner, NightPlanCodec.KEY).map { row ->
        session.requireLease(lease)
        val state = row?.let {
            require(it.userId == lease.owner && it.cacheKey == NightPlanCodec.KEY)
            NightPlanCodec.decode(cipher.open(it.payloadCipher), lease.owner, namespace)
        } ?: NightPlanState(lease.owner, namespace)
        session.requireLease(lease); state
    }.protectedReads()

    suspend fun read(lease: SessionLease): NightPlanState = withContext(Dispatchers.IO) {
        session.requireLease(lease); val value = readInside(lease.owner); session.requireLease(lease); value
    }
    private suspend fun change(lease: SessionLease, expected: Long, transform: (NightPlanState) -> NightPlanState): NightPlanState = withContext(Dispatchers.IO) {
        database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(lease.owner)
            val before = readInside(lease.owner)
            if (before.version != expected) throw ApiException(409, "night_plan_changed", "")
            val after = transform(before)
            session.requireLease(lease)
            if (after != before) database.dao().cache(CachedDocument(lease.owner, NightPlanCodec.KEY, cipher.seal(NightPlanCodec.encode(after)), System.currentTimeMillis()))
            session.requireLease(lease); after
        }
    }
    suspend fun save(lease: SessionLease, range: QuranRange, expected: Long): NightPlanState {
        session.requireLease(lease)
        // Cold asset loading stays outside the write transaction; its captured lease is rechecked.
        val canonical = withContext(Dispatchers.IO) { index() }
        session.requireLease(lease)
        require(canonical.global(range.start.surah, range.start.ayah) == range.start.globalIndex)
        require(canonical.global(range.end.surah, range.end.ayah) == range.end.globalIndex)
        return change(lease, expected) { it.save(range.start.globalIndex, range.end.globalIndex, expected, now()) }
    }
    suspend fun clear(lease: SessionLease, expected: Long) = change(lease, expected) { it.clear(expected) }

    /** Row, outbox and immutable operation receipt commit together, or all roll back. */
    suspend fun record(lease: SessionLease, expected: Long, selected: NightVersePlan, operation: String, date: LocalDate, zone: ZoneId, note: String) = withContext(Dispatchers.IO) {
        val record = NightPlanRecord.create(lease.owner, namespace, operation, selected, date, zone, note)
        database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(lease.owner)
            val key = NightPlanCodec.RECORD_PREFIX + operation
            val receipt = database.dao().cache(lease.owner, key)
            if (receipt != null) {
                val committed = decodeNightPlanRecord(receipt, lease.owner, namespace, cipher)
                if (committed != record.receipt()) throw ApiException(409, "night_record_changed", "")
                // Even if history was subsequently edited/deleted, an old retry cannot rewrite it.
                session.requireLease(lease); return@withTransaction
            }
            val current = readInside(lease.owner)
            if (current.version != expected || current.plan != selected) throw ApiException(409, "night_plan_changed", "")
            require(date <= now().atZone(zone).toLocalDate())
            if (database.dao().night(lease.owner, operation) != null) throw ApiException(409, "night_record_changed", "")
            nights.save(record.body.deepCopy(), operation, lease.owner)
            session.requireLease(lease)
            database.dao().cache(CachedDocument(lease.owner, key, cipher.seal(NightPlanCodec.encode(record.receipt())), System.currentTimeMillis()))
            session.requireLease(lease)
        }
    }
}

internal fun decodeNightPlanRecord(row: CachedDocument, owner: Long, namespace: String, cipher: PrivateCipher): NightPlanReceipt {
    require(row.userId == owner && row.cacheKey.startsWith(NightPlanCodec.RECORD_PREFIX))
    val record = NightPlanCodec.decodeReceipt(cipher.open(row.payloadCipher), owner, namespace)
    require(row.cacheKey == NightPlanCodec.RECORD_PREFIX + record.id)
    return record
}

/** Included in the existing owned export transaction; unreadable state aborts the copy. */
internal suspend fun retainedNightPlans(owner: Long, namespace: String, dao: AtharDao, cipher: PrivateCipher): JsonObject = JsonObject().apply {
    add("device_night_plan", JsonArray().apply {
        dao.cache(owner, NightPlanCodec.KEY)?.let { row ->
            require(row.userId == owner && row.cacheKey == NightPlanCodec.KEY)
            val state = NightPlanCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
            add(JsonParser.parseString(NightPlanCodec.encode(state)))
        }
    })
    add("device_night_plan_records", JsonArray().apply {
        dao.caches(owner, NightPlanCodec.RECORD_PREFIX + "%").forEach { row -> add(JsonParser.parseString(NightPlanCodec.encode(decodeNightPlanRecord(row, owner, namespace, cipher)))) }
    })
}
