package org.athar.app.core.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.UUID

internal enum class DeletionPhase(val wire: String) { PREPARED("prepared"), PENDING("pending"), REJECTED("rejected"), CONFIRMED("confirmed"), CLEANED("cleaned") }

internal data class AccountDeletionIntent(val namespace: String, val owner: Long, val requestId: String, val secret: String, val phase: DeletionPhase) {
    init {
        require(namespace.matches(Regex("[a-f0-9]{12}")) && owner > 0)
        require(UUID.fromString(requestId).let { it.version() == 4 && it.toString() == requestId })
        require(secret.matches(Regex("[a-f0-9]{64}")))
    }
    override fun toString() = "AccountDeletionIntent(redacted)"
    fun encode(): String = JsonObject().apply {
        addProperty("version", 1); addProperty("namespace", namespace); addProperty("owner", owner)
        addProperty("request_id", requestId); addProperty("secret", secret); addProperty("phase", phase.wire)
    }.toString()
    companion object {
        fun prepare(namespace: String, owner: Long): AccountDeletionIntent {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val secret = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            bytes.fill(0)
            return AccountDeletionIntent(namespace, owner, UUID.randomUUID().toString(), secret, DeletionPhase.PREPARED)
        }
        fun decode(namespace: String, row: LocalAccountDeletion, plaintext: String): AccountDeletionIntent {
            val body = JsonParser.parseString(plaintext).asJsonObject
            require(body.keySet() == setOf("version", "namespace", "owner", "request_id", "secret", "phase"))
            require(integer(body["version"]) == 1L && body["namespace"].asString == namespace)
            require(integer(body["owner"]) == row.userId && body["request_id"].asString == row.requestId && body["phase"].asString == row.state)
            return AccountDeletionIntent(namespace, row.userId, row.requestId, body["secret"].asString, DeletionPhase.entries.single { it.wire == row.state })
        }
        internal fun integer(value: JsonElement?): Long {
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.toString().matches(Regex("[1-9][0-9]*")))
            return value.asString.toLong()
        }
    }
}

internal object DeletionReceiptPayload {
    fun confirms(intent: AccountDeletionIntent, value: JsonElement): Boolean = runCatching {
        val body = value.asJsonObject
        body["status"].asString == "deleted" && body["request_id"].asString == intent.requestId && AccountDeletionIntent.integer(body["owner_id"]) == intent.owner
    }.getOrDefault(false)
}
