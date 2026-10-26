package org.athar.app.notifications

data class RemotePushEnvelope(
    val ownerId: Long,
    val environment: String,
    val category: String,
    val destination: NotificationDestination,
    val title: String,
    val body: String,
    val dedupeKey: String,
    val expiresAtMillis: Long,
) {
    fun expired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis >= expiresAtMillis
}

object RemotePushEnvelopeContract {
    fun parse(data: Map<String, String>): RemotePushEnvelope {
        require(data.keys.all {
            it in setOf(
                "version","owner_id","environment","category","destination",
                "title","body","dedupe_key","expires_at_ms"
            )
        })
        require(data["version"] == "1")
        val owner = data["owner_id"]?.toLongOrNull()?.takeIf { it > 0 }
            ?: error("invalid_push_owner")
        val environment = data["environment"]?.takeIf { it.matches(Regex("[a-f0-9]{12}")) }
            ?: error("invalid_push_environment")
        val category = data["category"]?.takeIf { it in NotificationPreferenceContract.categories }
            ?: error("invalid_push_category")
        val destination = NotificationDeepLinks.decode(data["destination"])
            ?: error("invalid_push_destination")
        val title = data["title"]?.takeIf { it.isNotBlank() && it.length <= 160 }
            ?: error("invalid_push_title")
        val body = data["body"]?.takeIf { it.length <= 500 }
            ?: error("invalid_push_body")
        val dedupe = data["dedupe_key"]?.takeIf { it.isNotBlank() && it.length <= 160 }
            ?: error("invalid_push_dedupe")
        val expiry = data["expires_at_ms"]?.toLongOrNull()?.takeIf { it > 0 }
            ?: error("invalid_push_expiry")
        return RemotePushEnvelope(
            ownerId = owner,
            environment = environment,
            category = category,
            destination = destination,
            title = title,
            body = body,
            dedupeKey = dedupe,
            expiresAtMillis = expiry,
        )
    }
}
