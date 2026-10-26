package org.athar.app.notifications

import com.google.gson.JsonArray
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
import java.time.LocalTime
import java.time.ZoneId

data class LocalReminderSpec(
    val id: String,
    val category: String,
    val destinationToken: String,
    val title: String,
    val body: String,
    val scheduledAtMillis: Long,
    val expiresAtMillis: Long,
    val idempotencyKey: String,
    val minimumIntervalSeconds: Long = 0,
    val quietStart: String? = null,
    val quietEnd: String? = null,
    val timezone: String? = null,
) {
    fun validated(): LocalReminderSpec {
        require(validId(id))
        require(category in NotificationPreferenceContract.categories)
        require(NotificationDeepLinks.decode(destinationToken) != null)
        require(title.isNotBlank() && title.length <= 160)
        require(body.length <= 500)
        require(scheduledAtMillis > 0 && expiresAtMillis > scheduledAtMillis)
        require(expiresAtMillis - scheduledAtMillis <= MAX_TTL_MILLIS)
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 160)
        require(minimumIntervalSeconds in 0..MAX_INTERVAL_SECONDS)
        require((quietStart == null) == (quietEnd == null))
        require((quietStart == null) == (timezone == null))
        if (quietStart != null) {
            LocalTime.parse(quietStart)
            LocalTime.parse(requireNotNull(quietEnd))
            ZoneId.of(requireNotNull(timezone))
        }
        return this
    }

    companion object {
        private val ID = Regex("[A-Za-z0-9._-]{1,80}")
        private const val MAX_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_INTERVAL_SECONDS = 7L * 24 * 60 * 60
        fun validId(value: String): Boolean = ID.matches(value)
    }
}

class LocalReminderStore(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val cacheKey = "private:notification-schedules:v1"

    suspend fun list(expectedOwner: Long? = session.userId): ProtectedRead<List<LocalReminderSpec>> {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val stored = dao.cache(owner, cacheKey) ?: return ProtectedRead.Ready(emptyList())
        return try {
            val parsed = decode(cipher.open(stored.payloadCipher))
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(parsed)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            ProtectedRead.Unreadable
        }
    }

    suspend fun find(owner: Long, id: String): ProtectedRead<LocalReminderSpec?> {
        require(LocalReminderSpec.validId(id))
        return when (val current = list(owner)) {
            is ProtectedRead.Ready -> ProtectedRead.Ready(current.value.firstOrNull { it.id == id })
            ProtectedRead.Loading -> ProtectedRead.Loading
            ProtectedRead.Unreadable -> ProtectedRead.Unreadable
        }
    }

    suspend fun upsert(
        spec: LocalReminderSpec,
        expectedOwner: Long? = session.userId,
    ): LocalReminderSpec = mutex.withLock {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val valid = spec.validated()
        val current = when (val read = list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("notification_schedule_unreadable")
        }
        val next = (current.filterNot { it.id == valid.id } + valid)
            .sortedWith(compareBy<LocalReminderSpec> { it.scheduledAtMillis }.thenBy { it.id })
        require(next.size <= MAX_REMINDERS)
        persist(owner, next)
        valid
    }

    suspend fun remove(
        id: String,
        expectedOwner: Long? = session.userId,
    ): Boolean = mutex.withLock {
        require(LocalReminderSpec.validId(id))
        val owner = session.requireVerifiedOwner(expectedOwner)
        val current = when (val read = list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("notification_schedule_unreadable")
        }
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return@withLock false
        persist(owner, next)
        true
    }

    private suspend fun persist(owner: Long, reminders: List<LocalReminderSpec>) {
        session.requireVerifiedOwner(owner)
        if (reminders.isEmpty()) {
            dao.removeCache(owner, cacheKey)
        } else {
            dao.cache(CachedDocument(owner, cacheKey, cipher.seal(encode(reminders)), now()))
        }
        session.requireVerifiedOwner(owner)
    }

    private fun encode(reminders: List<LocalReminderSpec>): String = JsonObject().apply {
        addProperty("version", 1)
        add("items", JsonArray().apply {
            reminders.forEach { value ->
                add(JsonObject().apply {
                    addProperty("id", value.id)
                    addProperty("category", value.category)
                    addProperty("destination", value.destinationToken)
                    addProperty("title", value.title)
                    addProperty("body", value.body)
                    addProperty("scheduled_at_ms", value.scheduledAtMillis)
                    addProperty("expires_at_ms", value.expiresAtMillis)
                    addProperty("idempotency_key", value.idempotencyKey)
                    addProperty("minimum_interval_seconds", value.minimumIntervalSeconds)
                    value.quietStart?.let { addProperty("quiet_start", it) }
                    value.quietEnd?.let { addProperty("quiet_end", it) }
                    value.timezone?.let { addProperty("timezone", it) }
                })
            }
        })
    }.toString()

    private fun decode(raw: String): List<LocalReminderSpec> {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val items = root.getAsJsonArray("items") ?: JsonArray()
        require(items.size() <= MAX_REMINDERS)
        val result = items.map { item ->
            val value = item.asJsonObject
            LocalReminderSpec(
                id = value.requiredString("id"),
                category = value.requiredString("category"),
                destinationToken = value.requiredString("destination"),
                title = value.requiredString("title"),
                body = value.requiredString("body"),
                scheduledAtMillis = value.requiredLong("scheduled_at_ms"),
                expiresAtMillis = value.requiredLong("expires_at_ms"),
                idempotencyKey = value.requiredString("idempotency_key"),
                minimumIntervalSeconds = value["minimum_interval_seconds"]?.asLong ?: 0,
                quietStart = value.optionalString("quiet_start"),
                quietEnd = value.optionalString("quiet_end"),
                timezone = value.optionalString("timezone"),
            ).validated()
        }
        require(result.map { it.id }.toSet().size == result.size)
        return result.sortedWith(compareBy<LocalReminderSpec> { it.scheduledAtMillis }.thenBy { it.id })
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asLong
    }

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    companion object {
        const val MAX_REMINDERS = 64
    }
}
