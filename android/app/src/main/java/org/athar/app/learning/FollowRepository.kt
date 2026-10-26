package org.athar.app.learning

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

data class FollowSnapshot(
    val targetType: String,
    val targetId: Int,
    val following: Boolean,
    val followedAt: String?,
)

object FollowContract {
    val targetTypes = setOf("scholar", "series")

    fun validateTarget(type: String, id: Int) {
        require(type in targetTypes)
        require(id > 0)
    }

    fun parse(element: JsonElement, expectedType: String, expectedId: Int): FollowSnapshot {
        validateTarget(expectedType, expectedId)
        require(element.isJsonObject)
        val root = element.asJsonObject
        val type = root.requiredString("target_type")
        val id = root.requiredInt("target_id")
        require(type == expectedType && id == expectedId)
        return FollowSnapshot(
            targetType = type,
            targetId = id,
            following = root.requiredBoolean("following"),
            followedAt = root.optionalString("followed_at"),
        )
    }

    fun encode(value: FollowSnapshot): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("target_type", value.targetType)
        addProperty("target_id", value.targetId)
        addProperty("following", value.following)
        if (value.followedAt == null) add("followed_at", null) else addProperty("followed_at", value.followedAt)
    }.toString()

    fun decode(raw: String, expectedType: String, expectedId: Int): FollowSnapshot {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        return parse(root, expectedType, expectedId)
    }
}

class FollowRepository(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun key(type: String, id: Int): String {
        FollowContract.validateTarget(type, id)
        return "private:follow:$type:$id"
    }

    fun observe(owner: Long, type: String, id: Int): Flow<ProtectedRead<FollowSnapshot?>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, key(type, id)).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { FollowContract.decode(cipher.open(it.payloadCipher), type, id) }
            },
        )
    }.protectedReads()

    suspend fun refresh(
        owner: Long,
        type: String,
        id: Int,
    ): FollowSnapshot {
        session.requireVerifiedOwner(owner)
        val data = session.request(
            "GET",
            "me/follows/$type/$id",
            expectedUser = owner,
        )
        return persist(owner, FollowContract.parse(data, type, id))
    }

    suspend fun set(
        owner: Long,
        type: String,
        id: Int,
        following: Boolean,
    ): FollowSnapshot {
        session.requireVerifiedOwner(owner)
        val cacheKey = key(type, id)
        val previous = dao.cache(owner, cacheKey)
        previous?.let { FollowContract.decode(cipher.open(it.payloadCipher), type, id) }

        val optimistic = FollowSnapshot(type, id, following, null)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(FollowContract.encode(optimistic)), now()))

        return try {
            val data = session.request(
                if (following) "PUT" else "DELETE",
                "me/follows/$type/$id",
                expectedUser = owner,
            )
            persist(owner, FollowContract.parse(data, type, id))
        } catch (failure: Throwable) {
            if (previous == null) dao.removeCache(owner, cacheKey) else dao.cache(previous)
            throw failure
        }
    }

    private suspend fun persist(owner: Long, value: FollowSnapshot): FollowSnapshot {
        session.requireVerifiedOwner(owner)
        dao.cache(
            CachedDocument(
                owner,
                key(value.targetType, value.targetId),
                cipher.seal(FollowContract.encode(value)),
                now(),
            ),
        )
        session.requireVerifiedOwner(owner)
        return value
    }
}

private fun JsonObject.requiredString(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString
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

private fun JsonObject.optionalString(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString
}
