package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext
import org.athar.app.auth.*
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.io.IOException
import java.time.Instant

internal data class NightPreferenceRepair(val owner: Long, val namespace: String, val digest: String, val account: NightPreferencesEnvelope) {
    init { require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}")) && digest.matches(Regex("[a-f0-9]{64}"))) }
}
internal data class NightPreferenceLoad(val snapshot: NightPreferencesSnapshot, val offline: Boolean,
    val repair: NightPreferenceRepair? = null, val newerCachedRevision: Boolean = false) {
    fun calculationSettings(): NightSavedSettings? = if (repair == null || newerCachedRevision) snapshot.account.values.calculationSettings() else null
}

/** Confirmed owned offline copies; edits require the captured generation and the account's revision. */
internal class NightPreferencesRepository(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val onChanged: suspend (SessionLease) -> Unit = {},
    private val now: () -> Instant = Instant::now,
) {
    private fun decode(row: CachedDocument, owner: Long): NightPreferencesSnapshot {
        require(row.userId == owner && row.cacheKey == NightPreferencesCodec.KEY)
        return NightPreferencesCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
    }
    private fun digest(row: CachedDocument): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest("${row.userId}:${row.cacheKey}:${row.updatedAt}:${row.payloadCipher}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    suspend fun cached(lease: SessionLease): NightPreferencesSnapshot? = withContext(Dispatchers.IO) {
        session.requireLease(lease)
        val snapshot = database.dao().cache(lease.owner, NightPreferencesCodec.KEY)?.let { decode(it, lease.owner) }
        session.requireLease(lease); snapshot
    }
    fun observe(lease: SessionLease) = database.dao().observeCache(lease.owner, NightPreferencesCodec.KEY).map { row ->
        session.requireLease(lease)
        val snapshot = row?.let { decode(it, lease.owner) }
        session.requireLease(lease); snapshot
    }.distinctUntilChangedBy { it?.account }.protectedReads()
    suspend fun load(lease: SessionLease): NightPreferenceLoad = withContext(Dispatchers.IO) {
        session.requireLease(lease)
        val response = try { session.requestLeased(lease, "GET", "night-prayer/preferences") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: IOException) {
            session.requireLease(lease)
            if (failure is ApiException && failure.status in 400..499 && failure.status != 429) throw failure
            val snapshot = cached(lease) ?: throw failure
            return@withContext NightPreferenceLoad(snapshot, offline = true)
        }
        // Invalid server data is not an offline result or an empty setting.
        val account = NightPreferencesEnvelope.parse(response)
        session.requireLease(lease); retain(lease, account)
    }
    private suspend fun write(lease: SessionLease, snapshot: NightPreferencesSnapshot) {
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
        require(snapshot.owner == lease.owner && snapshot.namespace == namespace)
        database.dao().cache(CachedDocument(lease.owner, NightPreferencesCodec.KEY, cipher.seal(NightPreferencesCodec.encode(snapshot)), System.currentTimeMillis()))
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
    }
    private suspend fun retain(lease: SessionLease, account: NightPreferencesEnvelope): NightPreferenceLoad = database.withTransaction {
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
        val offered = NightPreferencesSnapshot(lease.owner, namespace, account, now().toString())
        val row = database.dao().cache(lease.owner, NightPreferencesCodec.KEY)
        if (row != null) {
            val current = try { decode(row, lease.owner) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (current == null || (current.account.revision == account.revision && current.account != account)) {
                session.requireLease(lease)
                // Show the genuine account offer; retain unknown local bytes until explicit review.
                return@withTransaction NightPreferenceLoad(offered, false, NightPreferenceRepair(lease.owner, namespace, digest(row), account))
            }
            if (current.account.revision > account.revision) {
                session.requireLease(lease)
                return@withTransaction NightPreferenceLoad(current, false,
                    repair = NightPreferenceRepair(lease.owner, namespace, digest(row), account), newerCachedRevision = true)
            }
        }
        write(lease, offered); NightPreferenceLoad(offered, false)
    }
    suspend fun save(lease: SessionLease, reviewed: NightPreferencesEnvelope, values: NightPreferenceValues): NightPreferenceLoad = withContext(Dispatchers.IO) {
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
        val expected = reviewed.revision
        require(expected in 0 until NightPreferencesEnvelope.MAX_REVISION)
        val wanted = values.validatedForSave()
        // Unreadable cache is never silently replaced by a save (or misread as revision zero).
        val previous = cached(lease)
        if (previous?.account != reviewed) throw ApiException(409, "night_preferences_changed", "")
        val body = linkedMapOf<String, Any?>("expected_revision" to expected,
            "city" to wanted.city, "timezone" to wanted.timezone, "latitude" to wanted.latitude, "longitude" to wanted.longitude,
            "calculation_method" to wanted.calculationMethod, "madhab" to wanted.madhab, "high_latitude_rule" to wanted.highLatitudeRule)
        val reply = session.requestLeased(lease, "PUT", "night-prayer/preferences", body)
        val confirmed = try { NightPreferencesEnvelope.parse(reply) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw IOException("unconfirmed_night_preferences", failure) }
        if (confirmed.revision != expected + 1 || confirmed.values != wanted) throw IOException("unconfirmed_night_preferences")
        session.requireLease(lease)
        val result = retain(lease, confirmed)
        session.requireLease(lease); onChanged(lease); session.requireLease(lease)
        result
    }
    /** Replace only the reviewed local bytes with the still-current account copy; no cloud mutation. */
    suspend fun useAccountCopy(lease: SessionLease, reviewed: NightPreferenceRepair): NightPreferenceLoad = withContext(Dispatchers.IO) {
        session.requireLease(lease); database.dao().requireWritable(lease.owner)
        require(reviewed.owner == lease.owner && reviewed.namespace == namespace)
        val currentAccount = NightPreferencesEnvelope.parse(session.requestLeased(lease, "GET", "night-prayer/preferences"))
        if (currentAccount != reviewed.account) throw ApiException(409, "night_preferences_changed", "")
        val result = database.withTransaction {
            session.requireLease(lease); database.dao().requireWritable(lease.owner)
            val row = database.dao().cache(lease.owner, NightPreferencesCodec.KEY) ?: throw ApiException(409, "night_preferences_changed", "")
            if (digest(row) != reviewed.digest) throw ApiException(409, "night_preferences_changed", "")
            val snapshot = NightPreferencesSnapshot(lease.owner, namespace, currentAccount, now().toString())
            write(lease, snapshot); NightPreferenceLoad(snapshot, false)
        }
        session.requireLease(lease); onChanged(lease); session.requireLease(lease); result
    }
    suspend fun publicDefaults(lease: SessionLease): NightPublicDefaults = withContext(Dispatchers.IO) {
        session.requireLease(lease)
        val defaults = NightPublicDefaults.parse(session.requestLeased(lease, "GET", "night-prayer/config"))
        session.requireLease(lease); defaults
    }
}

internal suspend fun retainedNightPreferences(owner: Long, namespace: String, dao: AtharDao, cipher: PrivateCipher): JsonArray = JsonArray().apply {
    dao.cache(owner, NightPreferencesCodec.KEY)?.let { row ->
        require(row.userId == owner && row.cacheKey == NightPreferencesCodec.KEY)
        val snapshot = NightPreferencesCodec.decode(cipher.open(row.payloadCipher), owner, namespace)
        add(JsonParser.parseString(NightPreferencesCodec.encode(snapshot)))
    }
}
