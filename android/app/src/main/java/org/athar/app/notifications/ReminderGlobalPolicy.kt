package org.athar.app.notifications

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.security.PrivateCipher

data class ReminderGlobalPolicy(
    val quietEnabled: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val timezone: String = "UTC",
) {
    fun validated(): ReminderGlobalPolicy {
        LocalTime.parse(quietStart)
        LocalTime.parse(quietEnd)
        ZoneId.of(timezone)
        require(quietStart != quietEnd)
        return this
    }

    fun quietHours(): NotificationQuietHours? {
        validated()
        if (!quietEnabled) return null
        return NotificationQuietHours(
            start = LocalTime.parse(quietStart),
            end = LocalTime.parse(quietEnd),
            zoneId = ZoneId.of(timezone),
        )
    }
}

class ReminderGlobalPolicyRepository(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cacheKey = "private:reminder-global-policy:v1"

    suspend fun read(owner: Long): ProtectedRead<ReminderGlobalPolicy> {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey)
            ?: return ProtectedRead.Ready(ReminderGlobalPolicy(timezone = ZoneId.systemDefault().id))
        return try {
            val value = decode(cipher.open(row.payloadCipher))
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(value)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            ProtectedRead.Unreadable
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<ReminderGlobalPolicy>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { decode(cipher.open(it.payloadCipher)) }
                    ?: ReminderGlobalPolicy(timezone = ZoneId.systemDefault().id)
            },
        )
    }.protectedReads()

    suspend fun save(
        owner: Long,
        quietEnabled: Boolean,
        quietStart: LocalTime,
        quietEnd: LocalTime,
        timezone: ZoneId,
    ): ReminderGlobalPolicy {
        session.requireVerifiedOwner(owner)
        val value = ReminderGlobalPolicy(
            quietEnabled = quietEnabled,
            quietStart = quietStart.toString(),
            quietEnd = quietEnd.toString(),
            timezone = timezone.id,
        ).validated()
        val payload = JsonObject().apply {
            addProperty("version", 1)
            addProperty("quiet_enabled", value.quietEnabled)
            addProperty("quiet_start", value.quietStart)
            addProperty("quiet_end", value.quietEnd)
            addProperty("timezone", value.timezone)
        }.toString()
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(payload), now()))
        session.requireVerifiedOwner(owner)
        return value
    }

    private fun decode(raw: String): ReminderGlobalPolicy {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        return ReminderGlobalPolicy(
            quietEnabled = root["quiet_enabled"]?.asBoolean ?: false,
            quietStart = root["quiet_start"]?.asString ?: "22:00",
            quietEnd = root["quiet_end"]?.asString ?: "07:00",
            timezone = root["timezone"]?.asString ?: "UTC",
        ).validated()
    }
}
