package org.athar.app.core.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import java.util.Locale

/** Explicit export schema; a bad owner or unknown private section aborts the entire copy. */
object AccountExportPolicy {
    val accountTables = setOf("quran_bookmarks", "quran_positions", "khatmah_plans", "user_favorites", "video_progress", "account_daily_stats", "adhkar_progress")
    val deviceSections = setOf("device_lesson_progress", "device_night_sessions", "device_night_mutations", "device_reading_position", "device_adhkar_counters", "device_book_positions", "device_book_inventory", "device_book_activity")
    private val forbidden = setOf("privateexemptions", "localexemptions", "deviceprivateexemptions", "password", "passwordhash", "accesstoken", "refreshtoken", "tokenhash", "accesshash", "refreshhash", "secret", "apikey", "payloadcipher", "encryptedpayload", "sessionversion")

    private fun safe(value: JsonElement, depth: Int = 0) {
        require(depth <= 40)
        when {
            value.isJsonObject -> value.asJsonObject.entrySet().forEach { (key, child) ->
                require(key.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) !in forbidden)
                safe(child, depth + 1)
            }
            value.isJsonArray -> value.asJsonArray.forEach { safe(it, depth + 1) }
        }
    }
    internal fun integer(value: JsonElement?): Long {
        require(value != null && value.isJsonPrimitive && !value.asJsonPrimitive.isBoolean)
        require(value.asString.matches(Regex("0|[1-9][0-9]*")))
        return value.asString.toLong()
    }
    fun requireAccount(owner: Long, account: JsonObject) {
        require(owner > 0)
        require(account.keySet().all { it in accountTables || it in setOf("profile", "night_prayer_sessions", "night_prayer_preferences", "activity_events") })
        require(integer(account.getAsJsonObject("profile")["id"]) == owner)
        accountTables.forEach { table ->
            val rows = requireNotNull(account[table]).asJsonArray
            rows.forEach { require(integer(it.asJsonObject["user_id"]) == owner) }
        }
        account["activity_events"]?.asJsonArray?.forEach { require(integer(it.asJsonObject["user_id"]) == owner) }
        account["night_prayer_sessions"]?.let { require(it.isJsonArray); it.asJsonArray.forEach { row -> require(row.isJsonObject) } }
        account["night_prayer_preferences"]?.let { require(it.isJsonObject) }
        safe(account)
    }
    fun requireDevice(owner: Long, namespace: String, device: JsonObject) {
        require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}")))
        require(integer(device["owner_id"]) == owner && device["namespace"].asString == namespace)
        val legacyKeys = deviceSections + setOf("owner_id", "namespace")
        require(device.keySet() == legacyKeys || device.keySet() == legacyKeys + "device_khatmah_state")
        deviceSections.forEach { section ->
            val value = requireNotNull(device[section])
            if (section == "device_reading_position") require(value.isJsonNull || value.isJsonObject)
            else require(value.isJsonArray)
        }
        listOf("device_book_positions", "device_book_inventory", "device_book_activity").forEach { section -> device.getAsJsonArray(section).forEach {
            val row = it.asJsonObject
            require(integer(row["owner"]) == owner && row["namespace"].asString == namespace)
        } }
        device.getAsJsonArray("device_book_activity").forEach { row ->
            val request = row.asJsonObject["event_id"].asString
            BookCompletionEvent.parse(row, namespace, owner, request)
        }
        device["device_khatmah_state"]?.let { value ->
            require(value.isJsonArray && value.asJsonArray.size() <= 1)
            value.asJsonArray.forEach { org.athar.app.khatmah.KhatmahJournalCodec.decode(it.toString(), owner, namespace) }
        }
        safe(device)
    }
    fun envelope(owner: Long, namespace: String, account: JsonObject, device: JsonObject, generatedAt: Instant = Instant.now()): JsonObject {
        requireAccount(owner, account); requireDevice(owner, namespace, device)
        return JsonObject().apply {
            addProperty("export_version", 1); addProperty("owner_id", owner)
            addProperty("namespace", namespace); addProperty("generated_at", generatedAt.toString())
            add("account", account.deepCopy()); add("device", device.deepCopy())
            add("privacy", JsonObject().apply { addProperty("local_private_exemptions_included", false) })
        }
    }
}
