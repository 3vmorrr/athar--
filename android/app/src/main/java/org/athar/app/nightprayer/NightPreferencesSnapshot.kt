package org.athar.app.nightprayer

import com.google.gson.*
import java.time.Instant

/** Confirmed account copy for offline calculation, never an unsubmitted edit or a location guess. */
internal data class NightPreferencesSnapshot(val owner: Long, val namespace: String, val account: NightPreferencesEnvelope, val fetchedAt: String) {
    init { require(owner > 0 && namespace.matches(Regex("[a-f0-9]{12}"))); Instant.parse(fetchedAt) }
}

internal object NightPreferencesCodec {
    const val KEY = "private:night-preferences:v1"
    fun encode(snapshot: NightPreferencesSnapshot): String = JsonObject().apply {
        addProperty("schema", 1); addProperty("owner", snapshot.owner); addProperty("namespace", snapshot.namespace)
        add("account", snapshot.account.json()); addProperty("fetched_at", snapshot.fetchedAt)
    }.toString()
    fun decode(raw: String, owner: Long, namespace: String): NightPreferencesSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= 8192)
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.keySet() == setOf("schema", "owner", "namespace", "account", "fetched_at"))
        fun text(key: String) = requireNotNull(root[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        fun number(key: String) = requireNotNull(root[key]).also {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("[1-9][0-9]{0,18}")))
        }.toString().toLong()
        require(number("schema") == 1L && number("owner") == owner && text("namespace") == namespace)
        return NightPreferencesSnapshot(owner, namespace, NightPreferencesEnvelope.parse(requireNotNull(root["account"])), text("fetched_at"))
    }
}

/** Public method suggestions have no city/coordinates. Applying them is a separate explicit choice. */
internal data class NightPublicDefaults(val revision: Long, val enabled: Boolean, val method: String, val madhab: String, val highLatitudeRule: String,
    val sourceArabic: String, val sourceEnglish: String) {
    init {
        require(revision in 1..2_147_483_647 && method in PrayerCalculator.methods && madhab in setOf("SHAFI", "HANAFI") && highLatitudeRule in NightPreferenceValues.HIGH_LATITUDE_RULES)
        require(sourceArabic.codePointCount(0, sourceArabic.length) in 2..120 && sourceEnglish.codePointCount(0, sourceEnglish.length) in 2..120)
    }
    fun applyTo(values: NightPreferenceValues) = values.copy(calculationMethod = method, madhab = madhab, highLatitudeRule = highLatitudeRule)
    companion object {
        fun parse(data: JsonElement): NightPublicDefaults {
            val root = data.asJsonObject
            require(root.keySet() == setOf("enabled", "revision", "defaults", "source", "intro"))
            fun text(obj: JsonObject, key: String) = requireNotNull(obj[key]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
            fun number(obj: JsonObject, key: String) = requireNotNull(obj[key]).also {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("[1-9][0-9]{0,9}")))
            }.toString().toLong()
            val enabled = requireNotNull(root["enabled"]).also { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
            val defaults = requireNotNull(root["defaults"]).asJsonObject
            require(defaults.keySet() == setOf("calculation_method", "madhab", "high_latitude_rule", "target_verses"))
            require(number(defaults, "target_verses") in 1..6236)
            val source = requireNotNull(root["source"]).asJsonObject
            require(source.keySet() == setOf("name_ar", "name_en", "url"))
            // Source URLs are not opened here; the editor renders the supplied names as plain text.
            require(text(source, "url").length <= 2048)
            val intro = requireNotNull(root["intro"]).asJsonObject; require(intro.keySet() == setOf("ar", "en"))
            listOf("ar", "en").forEach { require(text(intro, it).codePointCount(0, text(intro, it).length) <= 1200) }
            val school = when (text(defaults, "madhab")) { "standard" -> "SHAFI"; "hanafi" -> "HANAFI"; else -> error("invalid_public_madhab") }
            return NightPublicDefaults(number(root, "revision"), enabled, text(defaults, "calculation_method"), school,
                text(defaults, "high_latitude_rule"), text(source, "name_ar"), text(source, "name_en"))
        }
    }
}
