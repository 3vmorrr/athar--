package org.athar.app.nightprayer

import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.security.PrivateCipher
import java.time.Instant

@Keep
data class NightVersePlan(
    val startIndex: Int,
    val endIndex: Int,
    val verseCount: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val localVersion: Long = 1,
    val updatedAt: String,
) {
    init {
        require(startIndex in 1..6236)
        require(endIndex in startIndex..6236)
        require(verseCount == endIndex - startIndex + 1)
        require(startSurah in 1..114 && endSurah in 1..114)
        require(startAyah > 0 && endAyah > 0)
        require(localVersion > 0)
        Instant.parse(updatedAt)
    }
}

/**
 * A verse plan is a private planning aid, not worship history and not Quran/Khatmah progress.
 * It stays account-partitioned and encrypted in the existing private cache.
 */
class NightVersePlanRepository(
    private val dao: AtharDao,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
) {
    private val gson = Gson()
    private val key = "night-prayer-verse-plan"

    fun observe(owner: Long): Flow<NightVersePlan?> =
        dao.observeCache(owner, key)
            .map { row -> row?.let(::decode) }
            .flowOn(Dispatchers.Default)

    suspend fun saved(owner: Long): NightVersePlan? =
        withContext(Dispatchers.IO) {
            session.requireVerifiedOwner(owner)
            dao.cache(owner, key)?.let(::decode)
        }

    suspend fun save(
        owner: Long,
        startIndex: Int,
        endIndex: Int,
        startSurah: Int,
        startAyah: Int,
        endSurah: Int,
        endAyah: Int,
    ): NightVersePlan = withContext(Dispatchers.IO) {
        session.requireVerifiedOwner(owner)
        require(startIndex in 1..6236)
        require(endIndex in startIndex..6236)
        val previous = dao.cache(owner, key)?.let(::decode)
        val value = NightVersePlan(
            startIndex = startIndex,
            endIndex = endIndex,
            verseCount = endIndex - startIndex + 1,
            startSurah = startSurah,
            startAyah = startAyah,
            endSurah = endSurah,
            endAyah = endAyah,
            localVersion = (previous?.localVersion ?: 0L) + 1L,
            updatedAt = Instant.now().toString(),
        )
        session.requireVerifiedOwner(owner)
        dao.cache(
            CachedDocument(
                userId = owner,
                cacheKey = key,
                payloadCipher = cipher.seal(gson.toJson(value)),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        session.requireVerifiedOwner(owner)
        value
    }

    suspend fun clear(owner: Long) = withContext(Dispatchers.IO) {
        session.requireVerifiedOwner(owner)
        dao.removeCache(owner, key)
    }

    private fun decode(row: CachedDocument): NightVersePlan {
        require(row.cacheKey == key)
        return requireNotNull(
            gson.fromJson(
                cipher.open(row.payloadCipher),
                NightVersePlan::class.java,
            ),
        ).also {
            require(it.verseCount == it.endIndex - it.startIndex + 1)
        }
    }
}
