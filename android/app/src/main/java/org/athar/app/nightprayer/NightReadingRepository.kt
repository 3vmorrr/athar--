package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.athar.app.auth.*
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.time.Instant

internal data class NightReadingSnapshot(val scope: NightReadingScope, val position: NightReadingPosition, val firstUncovered: Int?) {
    init { require(position.scope == scope && (firstUncovered == null || firstUncovered in scope.start..scope.end)) }
    val initialIndex: Int get() = position.index ?: firstUncovered ?: scope.start
    val recordedComplete: Boolean get() = scope.kind == "plan" && firstUncovered == null
}
internal class NightPositionRangeChanged(val retained: NightReadingPosition) : IOException("night_position_range_changed")

/** Explicit, owned prayer-reading markers. This repository has no global Quran/Khatmah writer. */
internal class NightReadingRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val nights: NightPrayerRepository,
    private val now: () -> Instant = Instant::now,
) {
    private fun active(lease: SessionLease, scope: NightReadingScope) {
        require(scope.owner == lease.owner && scope.namespace == namespace); session.requireLease(lease)
    }
    private suspend fun source(lease: SessionLease, scope: NightReadingScope) {
        active(lease, scope)
        val range = if (scope.kind == "plan") {
            val row = database.dao().cache(lease.owner, NightPlanCodec.KEY) ?: throw ApiException(404, "night_source_missing", "")
            require(row.userId == lease.owner && row.cacheKey == NightPlanCodec.KEY)
            val plan = NightPlanCodec.decode(cipher.open(row.payloadCipher), lease.owner, namespace).plan
            if (plan == null || plan.id != scope.id) throw ApiException(409, "night_source_changed", "")
            plan.startIndex..plan.endIndex
        } else nights.readerRange(lease, scope.id) ?: throw ApiException(404, "night_source_missing", "")
        if (range.first != scope.start || range.last != scope.end) throw ApiException(409, "night_source_changed", "")
        active(lease, scope)
    }
    private suspend fun position(scope: NightReadingScope): NightReadingPosition {
        val row = database.dao().cache(scope.owner, scope.key) ?: return NightReadingPosition(scope)
        require(row.userId == scope.owner && row.cacheKey == scope.key)
        val value = NightReadingPositionCodec.decode(cipher.open(row.payloadCipher), scope.owner, namespace)
        require(value.scope.key == scope.key)
        return value
    }
    private suspend fun snapshot(lease: SessionLease, scope: NightReadingScope): NightReadingSnapshot {
        source(lease, scope)
        val position = position(scope)
        if (position.scope != scope) throw NightPositionRangeChanged(position)
        val next = if (scope.kind == "plan") {
            val ranges = mutableListOf<NightPlanCoverage>()
            for (row in database.dao().caches(lease.owner, NightPlanCodec.RECORD_PREFIX + "%")) {
                val receipt = decodeNightPlanRecord(row, lease.owner, namespace, cipher)
                if (receipt.planId != scope.id) continue
                val range = nights.readerRange(lease, receipt.id) ?: continue
                ranges.add(NightPlanCoverage(lease.owner, namespace, receipt.planId, receipt.id, range))
            }
            firstUncoveredNightIndex(scope, ranges)
        } else scope.start
        active(lease, scope); return NightReadingSnapshot(scope, position, next)
    }
    suspend fun read(lease: SessionLease, scope: NightReadingScope): NightReadingSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction { snapshot(lease, scope) }
    }
    fun observe(lease: SessionLease, scope: NightReadingScope): Flow<NightReadingSnapshot> {
        active(lease, scope)
        val dao = database.dao()
        // A source edit/delete or recorded-range change invalidates the old view immediately.
        return combine(dao.observeCache(lease.owner, scope.key), dao.observeCache(lease.owner, NightPlanCodec.KEY),
            dao.nights(lease.owner), dao.observeCaches(lease.owner, NightPlanCodec.RECORD_PREFIX + "%")) { _, _, _, _ -> read(lease, scope) }.distinctUntilChanged()
    }
    suspend fun save(lease: SessionLease, scope: NightReadingScope, position: Int, expected: Long): NightReadingSnapshot = change(lease, scope, expected, false) { it.save(position, expected, now()) }
    suspend fun clear(lease: SessionLease, scope: NightReadingScope, expected: Long): NightReadingSnapshot = change(lease, scope, expected, false) { it.clear(expected, now()) }
    /** A reviewed range edit may replace this marker, but never silently adopts the old marker. */
    suspend fun resetChangedRange(lease: SessionLease, scope: NightReadingScope, expected: Long): NightReadingSnapshot = change(lease, scope, expected, true) {
        NightReadingPosition(scope, it.version + 1, null, now().toString())
    }
    private suspend fun change(lease: SessionLease, scope: NightReadingScope, expected: Long, reset: Boolean, transform: (NightReadingPosition) -> NightReadingPosition): NightReadingSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            active(lease, scope); database.dao().requireWritable(lease.owner); source(lease, scope)
            val before = position(scope)
            if (before.version != expected) throw ApiException(409, "night_position_changed", "")
            if (before.scope != scope && !reset) throw NightPositionRangeChanged(before)
            val after = transform(before)
            active(lease, scope)
            if (before != after) database.dao().cache(CachedDocument(lease.owner, scope.key, cipher.seal(NightReadingPositionCodec.encode(after)), System.currentTimeMillis()))
            active(lease, scope); snapshot(lease, scope)
        }
    }
}

/** Plan replacement/clear owns its scoped marker too; malformed markers remain retained. */
internal suspend fun clearReplacedNightPlanPosition(before: NightPlanState, after: NightPlanState, dao: AtharDao, cipher: PrivateCipher) {
    val plan = before.plan ?: return
    if (plan.id == after.plan?.id) return
    val scope = NightReadingScope(before.owner, before.namespace, "plan", plan.id, plan.startIndex, plan.endIndex)
    val row = dao.cache(before.owner, scope.key) ?: return
    require(row.userId == before.owner && row.cacheKey == scope.key)
    val position = NightReadingPositionCodec.decode(cipher.open(row.payloadCipher), before.owner, before.namespace)
    require(position.scope == scope); dao.removeCache(before.owner, scope.key)
}

internal suspend fun retainedNightReading(owner: Long, namespace: String, dao: AtharDao, cipher: PrivateCipher): JsonArray = JsonArray().apply {
    dao.caches(owner, NightReadingPositionCodec.PREFIX + "%").forEach { row ->
        require(row.userId == owner)
        val value = NightReadingPositionCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
        require(row.cacheKey == value.scope.key)
        add(JsonParser.parseString(NightReadingPositionCodec.encode(value)))
    }
}
