package org.athar.app.nightprayer

import com.google.gson.JsonElement
import org.athar.app.auth.SessionRepository
import java.io.IOException
import java.time.ZoneId

data class NightPrayerPreferences(
    val city: String,
    val timezone: String,
    val latitude: Double,
    val longitude: Double,
    val calculationMethod: String,
    val madhab: String,
    val highLatitudeRule: String,
)

data class NightPreferencesEnvelope(
    val revision: Int,
    val values: NightPrayerPreferences?,
)

class NightPreferencesRepository(
    private val session: SessionRepository,
) {
    suspend fun get(
        expectedOwner: Long? = session.userId,
    ): NightPreferencesEnvelope {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val response = session.request(
            "GET",
            "night-prayer/preferences",
            expectedUser = owner,
        )
        session.requireVerifiedOwner(owner)
        return parse(response)
    }

    suspend fun save(
        values: NightPrayerPreferences,
        expectedRevision: Int,
        expectedOwner: Long? = session.userId,
    ): NightPreferencesEnvelope {
        val owner = session.requireVerifiedOwner(expectedOwner)
        require(expectedRevision >= 0)
        validate(values)

        val response = session.request(
            method = "PUT",
            path = "night-prayer/preferences",
            body = mapOf(
                "expected_revision" to expectedRevision,
                "city" to values.city.trim(),
                "timezone" to values.timezone,
                "latitude" to values.latitude,
                "longitude" to values.longitude,
                "calculation_method" to values.calculationMethod,
                "madhab" to values.madhab,
                "high_latitude_rule" to values.highLatitudeRule,
            ),
            expectedUser = owner,
        )
        session.requireVerifiedOwner(owner)
        return parse(response)
    }

    private fun parse(data: JsonElement): NightPreferencesEnvelope {
        val root = data.asJsonObject
        val revision = root["revision"]?.asInt
            ?: throw IOException("Missing Night Prayer preference revision")
        require(revision >= 0)

        val values = root["values"]
            ?.takeUnless { it.isJsonNull }
            ?.asJsonObject
            ?.takeIf { it.entrySet().isNotEmpty() }
            ?.let { row ->
                val parsed = NightPrayerPreferences(
                    city = row["city"]?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                    timezone = row["timezone"]?.asString
                        ?: throw IOException("Missing timezone"),
                    latitude = row["latitude"]?.asDouble
                        ?: throw IOException("Missing latitude"),
                    longitude = row["longitude"]?.asDouble
                        ?: throw IOException("Missing longitude"),
                    calculationMethod = row["calculation_method"]?.asString
                        ?: throw IOException("Missing calculation method"),
                    madhab = row["madhab"]?.asString
                        ?: throw IOException("Missing madhab"),
                    highLatitudeRule = row["high_latitude_rule"]?.asString
                        ?: throw IOException("Missing high latitude rule"),
                )
                validate(parsed)
                parsed
            }

        return NightPreferencesEnvelope(
            revision = revision,
            values = values,
        )
    }

    private fun validate(values: NightPrayerPreferences) {
        require(values.city.length <= 80)
        require(values.latitude.isFinite() && values.latitude in -90.0..90.0)
        require(values.longitude.isFinite() && values.longitude in -180.0..180.0)
        require(values.timezone.length <= 80)
        ZoneId.of(values.timezone)
        require(values.calculationMethod in PrayerCalculator.methods)
        require(values.madhab in setOf("SHAFI", "HANAFI"))
        require(
            values.highLatitudeRule in setOf(
                "MIDDLE_OF_THE_NIGHT",
                "SEVENTH_OF_THE_NIGHT",
                "TWILIGHT_ANGLE",
            ),
        )
    }
}
