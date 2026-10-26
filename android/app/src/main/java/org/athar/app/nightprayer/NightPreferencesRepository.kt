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
    private val onChanged: suspend (Long, NightPrayerPreferences?) -> Unit = { _, _ -> },
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
        val parsed = parse(response)
        onChanged(owner, parsed.values)
        return parsed
    }

    suspend fun save(
        values: NightPrayerPreferences,
        expectedRevision: Int,
        expectedOwner: Long? = session.userId,
    ): NightPreferencesEnvelope {
        val owner = session.requireVerifiedOwner(expectedOwner)
        require(expectedRevision >= 0)
        values.validated()

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
        val parsed = parse(response)
        onChanged(owner, parsed.values)
        return parsed
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
                parsed.validated()
            }

        return NightPreferencesEnvelope(
            revision = revision,
            values = values,
        )
    }

}


fun NightPrayerPreferences.validated(): NightPrayerPreferences {
    require(city.length <= 80)
    require(latitude.isFinite() && latitude in -90.0..90.0)
    require(longitude.isFinite() && longitude in -180.0..180.0)
    require(timezone.length <= 80)
    ZoneId.of(timezone)
    require(calculationMethod in PrayerCalculator.methods)
    require(madhab in setOf("SHAFI", "HANAFI"))
    require(
        highLatitudeRule in setOf(
            "MIDDLE_OF_THE_NIGHT",
            "SEVENTH_OF_THE_NIGHT",
            "TWILIGHT_ANGLE",
        ),
    )
    return this
}
