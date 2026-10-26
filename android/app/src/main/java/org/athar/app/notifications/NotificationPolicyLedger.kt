package org.athar.app.notifications

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.security.PrivateCipher
import java.time.Duration
import java.time.Instant

data class NotificationPolicyLedger(
    val delivered: Map<String, Long> = emptyMap(),
    val lastByCategory: Map<String, Long> = emptyMap(),
) {
    fun deliveredKeys(): Set<String> = delivered.keys
    fun lastDelivered(category: String): Instant? = lastByCategory[category]?.let(Instant::ofEpochMilli)
}

class NotificationPolicyLedgerRepository(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val cacheKey = "private:notification-policy-ledger:v1"
    private val retention = Duration.ofDays(30).toMillis()
    private val maxDeliveredKeys = 256

    suspend fun read(expectedOwner: Long? = session.userId): ProtectedRead<NotificationPolicyLedger> {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val stored = dao.cache(owner, cacheKey) ?: return ProtectedRead.Ready(NotificationPolicyLedger())
        return try {
            val parsed = parse(cipher.open(stored.payloadCipher))
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(parsed)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            ProtectedRead.Unreadable
        }
    }

    suspend fun recordDelivered(
        idempotencyKey: String,
        category: String,
        deliveredAt: Instant,
        expectedOwner: Long? = session.userId,
    ): NotificationPolicyLedger = mutex.withLock {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 160)
        require(category in NotificationPreferenceContract.categories || category == "campaigns")
        val owner = session.requireVerifiedOwner(expectedOwner)
        val existing = when (val current = read(owner)) {
            is ProtectedRead.Ready -> current.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("notification_policy_ledger_unreadable")
        }
        val cutoff = now() - retention
        val delivered = (existing.delivered + (idempotencyKey to deliveredAt.toEpochMilli()))
            .filterValues { it >= cutoff }
            .entries
            .sortedByDescending { it.value }
            .take(maxDeliveredKeys)
            .associate { it.key to it.value }
        val last = existing.lastByCategory.toMutableMap().apply {
            val previous = get(category) ?: Long.MIN_VALUE
            if (deliveredAt.toEpochMilli() > previous) put(category, deliveredAt.toEpochMilli())
        }
        val updated = NotificationPolicyLedger(delivered, last)
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(serialize(updated)), now()))
        session.requireVerifiedOwner(owner)
        updated
    }

    private fun serialize(value: NotificationPolicyLedger): String = JsonObject().apply {
        add("delivered", JsonObject().apply {
            value.delivered.forEach { (key, at) -> addProperty(key, at) }
        })
        add("last_by_category", JsonObject().apply {
            value.lastByCategory.forEach { (key, at) -> addProperty(key, at) }
        })
    }.toString()

    private fun parse(raw: String): NotificationPolicyLedger {
        val root = JsonParser.parseString(raw).asJsonObject
        fun map(name: String): Map<String, Long> {
            val objectValue = root.getAsJsonObject(name) ?: return emptyMap()
            return objectValue.entrySet().associate { entry ->
                require(entry.key.isNotBlank() && entry.key.length <= 160)
                entry.key to entry.value.asLong
            }
        }
        val delivered = map("delivered")
        require(delivered.size <= maxDeliveredKeys)
        val categories = map("last_by_category")
        require(categories.keys.all { it in NotificationPreferenceContract.categories || it == "campaigns" })
        return NotificationPolicyLedger(delivered, categories)
    }
}
