package org.athar.app.nightprayer

import com.google.gson.*
import java.time.ZoneId

/** Missing fields are valid legacy preferences, distinct from malformed fields or invented defaults. */
internal data class NightPreferenceValues(
    val city: String? = null,
    val timezone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val calculationMethod: String? = null,
    val madhab: String? = null,
    val highLatitudeRule: String? = null,
) {
    init {
        listOf(city, timezone, calculationMethod, madhab, highLatitudeRule).filterNotNull().forEach { require(it.codePointCount(0, it.length) <= 80) }
        latitude?.let { require(it.isFinite() && it in -90.0..90.0) }
        longitude?.let { require(it.isFinite() && it in -180.0..180.0) }
    }
    /** The current API permits unknown method strings; retain them for explicit repair, never default them. */
    fun calculationSettings(): NightSavedSettings? {
        val zone = timezone?.takeIf { it == "UTC" || it in ZoneId.getAvailableZoneIds() } ?: return null
        val lat = latitude ?: return null; val lon = longitude ?: return null
        val method = calculationMethod?.takeIf { it in PrayerCalculator.methods } ?: return null
        val school = madhab?.takeIf { it in setOf("SHAFI", "HANAFI") } ?: return null
        val high = highLatitudeRule?.takeIf { it in HIGH_LATITUDE_RULES } ?: return null
        return NightSavedSettings(ZoneId.of(zone), lat, lon, method, school == "HANAFI", high)
    }
    fun validatedForSave(): NightPreferenceValues {
        require(city != null && calculationSettings() != null)
        return this
    }
    fun json(): JsonObject = JsonObject().apply {
        city?.let { addProperty("city", it) }; timezone?.let { addProperty("timezone", it) }
        latitude?.let { addProperty("latitude", it) }; longitude?.let { addProperty("longitude", it) }
        calculationMethod?.let { addProperty("calculation_method", it) }; madhab?.let { addProperty("madhab", it) }
        highLatitudeRule?.let { addProperty("high_latitude_rule", it) }
    }
    companion object {
        val HIGH_LATITUDE_RULES = setOf("MIDDLE_OF_THE_NIGHT", "SEVENTH_OF_THE_NIGHT", "TWILIGHT_ANGLE")
        private val KEYS = setOf("city", "timezone", "latitude", "longitude", "calculation_method", "madhab", "high_latitude_rule")
        fun parse(value: JsonElement): NightPreferenceValues {
            require(value.isJsonObject)
            val root = value.asJsonObject; require(root.keySet().all { it in KEYS })
            fun text(key: String): String? = root[key]?.also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }?.asString
            fun coordinate(key: String): Double? = root[key]?.also { require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) }?.asDouble
            return NightPreferenceValues(text("city"), text("timezone"), coordinate("latitude"), coordinate("longitude"),
                text("calculation_method"), text("madhab"), text("high_latitude_rule"))
        }
    }
}

internal data class NightPreferencesEnvelope(val revision: Long, val values: NightPreferenceValues) {
    init { require(revision in 0..MAX_REVISION && (revision != 0L || values == NightPreferenceValues())) }
    fun json(): JsonObject = JsonObject().apply { addProperty("revision", revision); add("values", values.json()) }
    companion object {
        const val MAX_REVISION = 4_294_967_295L // Existing INT UNSIGNED server column; do not truncate to signed Int.
        fun parse(data: JsonElement): NightPreferencesEnvelope {
            require(data.isJsonObject)
            val root = data.asJsonObject; require(root.keySet() == setOf("revision", "values"))
            val revision = requireNotNull(root["revision"])
            require(revision.isJsonPrimitive && revision.asJsonPrimitive.isNumber && revision.toString().matches(Regex("0|[1-9][0-9]{0,9}")))
            return NightPreferencesEnvelope(revision.toString().toLong(), NightPreferenceValues.parse(requireNotNull(root["values"])))
        }
    }
}
