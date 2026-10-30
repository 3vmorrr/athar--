package org.athar.app.nightprayer

import java.time.*

data class NightIdentity(val nightDate: LocalDate, val inProgress: Boolean, val context: NightContext)

/** A night keeps its Maghrib date after civil midnight; daytime refers to the upcoming night. */
fun resolveNightIdentity(now: Instant, zone: ZoneId, calculate: (LocalDate) -> NightContext): NightIdentity {
    val date = now.atZone(zone).toLocalDate()
    fun context(day: LocalDate): NightContext? = try {
        calculate(day).also { require(it.date == day && it.zone == zone) }
    } catch (_: IllegalArgumentException) { null }
    val today = context(date)
    if (today?.contains(now) == true) return NightIdentity(date, true, today)
    val previousDate = date.minusDays(1)
    // An unavailable previous night must not erase today's valid upcoming night in daytime.
    if (today == null || now < today.maghrib) {
        val previous = context(previousDate)
        if (previous?.contains(now) == true) return NightIdentity(previousDate, true, previous)
    }
    requireNotNull(today) { "Night boundaries unavailable" }
    // If today's night has already ended within this civil date, compute the next valid night.
    if (now >= today.fajr) {
        val nextDate = date.plusDays(1)
        val next = calculate(nextDate).also { require(it.date == nextDate && it.zone == zone) }
        return NightIdentity(nextDate, false, next)
    }
    return NightIdentity(date, false, today)
}

/** Refresh exactly at known boundaries; bounded checks also recover from unannounced wall-clock jumps. */
internal fun nightRefreshDelay(identity: NightIdentity?, now: Instant): Long =
    listOfNotNull(identity?.context?.maghrib, identity?.context?.fajr)
        .filter { it > now }.minOrNull()?.let { Duration.between(now, it).toMillis().coerceIn(1, 30_000) } ?: 30_000
