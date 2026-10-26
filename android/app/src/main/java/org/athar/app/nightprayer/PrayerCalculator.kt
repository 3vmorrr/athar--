package org.athar.app.nightprayer

import com.batoulapps.adhan.*
import com.batoulapps.adhan.data.DateComponents
import java.time.*

object PrayerCalculator {
    val methods = listOf("EGYPTIAN", "MUSLIM_WORLD_LEAGUE", "UMM_AL_QURA", "KARACHI", "DUBAI", "MOON_SIGHTING_COMMITTEE")
    fun calculate(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId, method: String, hanafi: Boolean = false, highLatitude: String = "MIDDLE_OF_THE_NIGHT"): NightContext {
        require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
        require(method in methods)
        val parameters = CalculationMethod.valueOf(method).parameters.apply { madhab = if (hanafi) Madhab.HANAFI else Madhab.SHAFI; highLatitudeRule = HighLatitudeRule.valueOf(highLatitude) }
        val coordinates = Coordinates(latitude, longitude)
        fun prayers(day: LocalDate) = PrayerTimes(coordinates, DateComponents(day.year, day.monthValue, day.dayOfMonth), parameters)
        val today = prayers(date); val tomorrow = prayers(date.plusDays(1))
        val maghrib = requireNotNull(today.maghrib).toInstant()
        val fajr = requireNotNull(tomorrow.fajr).toInstant()
        return NightContext(date, zone, maghrib, fajr)
    }
}
