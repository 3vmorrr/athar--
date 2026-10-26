package org.athar.app.notifications

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

object NotificationPreferenceContract {
    const val SCHEMA_VERSION = 1
    const val CONSENT_VERSION = 1
    val categories = linkedSetOf(
        "lesson_reminders",
        "following_updates",
        "quran_routine",
        "adhkar_routine",
        "salawat_friday",
        "custom_routines",
        "khatmah",
        "night_prayer",
    )
}

data class NotificationConsent(
    val requiredVersion: Int,
    val acceptedVersion: Int,
    val current: Boolean,
    val acceptedAt: String?,
)

data class NotificationPreferences(
    val schemaVersion: Int,
    val revision: Int,
    val masterEnabled: Boolean,
    val consent: NotificationConsent,
    val categories: Map<String, Boolean>,
    val updatedAt: String?,
) {
    fun allows(category: String): Boolean =
        category in NotificationPreferenceContract.categories &&
            masterEnabled &&
            consent.current &&
            categories[category] == true

    companion object {
        fun safeDefault() = NotificationPreferences(
            schemaVersion = NotificationPreferenceContract.SCHEMA_VERSION,
            revision = 0,
            masterEnabled = false,
            consent = NotificationConsent(
                requiredVersion = NotificationPreferenceContract.CONSENT_VERSION,
                acceptedVersion = 0,
                current = false,
                acceptedAt = null,
            ),
            categories = NotificationPreferenceContract.categories.associateWith { false },
            updatedAt = null,
        )
    }
}

data class NotificationPreferencePatch(
    val expectedRevision: Int,
    val masterEnabled: Boolean? = null,
    val consentVersion: Int? = null,
    val categories: Map<String, Boolean> = emptyMap(),
) {
    fun asBody(): Map<String, Any?> {
        require(expectedRevision >= 0)
        require(consentVersion == null || consentVersion == 0 || consentVersion == NotificationPreferenceContract.CONSENT_VERSION)
        require(categories.keys.all { it in NotificationPreferenceContract.categories })
        return buildMap {
            put("expected_revision", expectedRevision)
            masterEnabled?.let { put("master_enabled", it) }
            consentVersion?.let { put("consent_version", it) }
            if (categories.isNotEmpty()) put("categories", categories)
        }
    }
}

fun notificationPreferencesFromJson(element: JsonElement): NotificationPreferences {
    require(element.isJsonObject)
    val root = element.asJsonObject
    val schema = root.requiredInt("schema_version")
    require(schema == NotificationPreferenceContract.SCHEMA_VERSION)
    val revision = root.requiredInt("revision")
    require(revision >= 0)
    val master = root.requiredBoolean("master_enabled")
    val consentObject = root.requiredObject("consent")
    val requiredVersion = consentObject.requiredInt("required_version")
    val acceptedVersion = consentObject.requiredInt("accepted_version")
    require(requiredVersion > 0 && acceptedVersion >= 0)
    val supportedCurrent = acceptedVersion == requiredVersion &&
        requiredVersion <= NotificationPreferenceContract.CONSENT_VERSION &&
        consentObject.requiredBoolean("current")
    val categoryObject = root.requiredObject("categories")
    val categories = NotificationPreferenceContract.categories.associateWith { key ->
        categoryObject.optionalBoolean(key) ?: false
    }
    return NotificationPreferences(
        schemaVersion = schema,
        revision = revision,
        masterEnabled = master,
        consent = NotificationConsent(
            requiredVersion = requiredVersion,
            acceptedVersion = acceptedVersion,
            current = supportedCurrent,
            acceptedAt = consentObject.optionalString("accepted_at"),
        ),
        categories = categories,
        updatedAt = root.optionalString("updated_at"),
    )
}

class NotificationPreferenceRepository(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val cacheKey = "private:notification-preferences:v1"

    suspend fun read(owner: Long): ProtectedRead<NotificationPreferences> {
        session.requireVerifiedOwner(owner)
        val stored = dao.cache(owner, cacheKey) ?: return ProtectedRead.Ready(NotificationPreferences.safeDefault())
        return try {
            val value = notificationPreferencesFromJson(JsonParser.parseString(cipher.open(stored.payloadCipher)))
            session.requireVerifiedOwner(owner)
            ProtectedRead.Ready(value)
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (_: Exception) {
            ProtectedRead.Unreadable
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<NotificationPreferences>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { stored ->
                session.requireVerifiedOwner(owner)
                if (stored == null) {
                    NotificationPreferences.safeDefault()
                } else {
                    notificationPreferencesFromJson(JsonParser.parseString(cipher.open(stored.payloadCipher)))
                }
            },
        )
    }.protectedReads()

    suspend fun refresh(expectedOwner: Long? = session.userId): NotificationPreferences {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val data = session.request("GET", "me/notification-preferences", expectedUser = owner)
        return persist(owner, data)
    }

    suspend fun update(
        patch: NotificationPreferencePatch,
        expectedOwner: Long? = session.userId,
    ): NotificationPreferences {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val data = session.request(
            "PATCH",
            "me/notification-preferences",
            body = patch.asBody(),
            expectedUser = owner,
        )
        return persist(owner, data)
    }

    private suspend fun persist(owner: Long, data: JsonElement): NotificationPreferences {
        val parsed = notificationPreferencesFromJson(data)
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(data.toString()), now()))
        session.requireVerifiedOwner(owner)
        return parsed
    }
}

private fun JsonObject.requiredInt(name: String): Int {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
    return value.asInt
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
    return value.asBoolean
}

private fun JsonObject.requiredObject(name: String): JsonObject {
    val value = get(name)
    require(value != null && value.isJsonObject)
    return value.asJsonObject
}

private fun JsonObject.optionalBoolean(name: String): Boolean? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
    return value.asBoolean
}

private fun JsonObject.optionalString(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString
}
