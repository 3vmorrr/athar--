package org.athar.app.nightprayer

import java.time.*

data class NightContext(val date: LocalDate, val zone: ZoneId, val maghrib: Instant, val fajr: Instant) {
    init { require(fajr > maghrib && Duration.between(maghrib, fajr).seconds <= 86400); require(maghrib.atZone(zone).toLocalDate() == date) }
    val lastThird: Instant get() = maghrib.plusSeconds(Duration.between(maghrib, fajr).seconds * 2 / 3)
    fun contains(instant: Instant) = instant >= maghrib && instant < fajr
}
object NightRules {
    fun serverTimezone(zone: ZoneId): String = if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id
    fun milestone(count: Int): Int = when { count >= 1000 -> 1000; count >= 100 -> 100; count >= 10 -> 10; else -> 0 }
    fun pace(sessions: List<Pair<Int, Int>>): Double? {
        val measured = sessions.filter { it.first > 0 && it.second > 0 }
        return measured.takeIf { it.isNotEmpty() }?.let { it.sumOf { session -> session.second.toLong() }.toDouble() / it.sumOf { session -> session.first.toLong() } }
    }
    fun streak(totals: Map<LocalDate, Int>, threshold: Int, current: LocalDate, nightInProgress: Boolean, exemptions: Set<LocalDate> = emptySet()): Pair<Int, Int> {
        require(threshold in listOf(1, 10, 100, 1000))
        val eligible = totals.filterValues { it >= threshold }.keys
        if (eligible.isEmpty()) return 0 to 0
        var day = if (nightInProgress && current !in eligible) current.minusDays(1) else current
        var active = 0
        val first = eligible.minOrNull()!!
        while (day >= first && (day in eligible || day in exemptions)) { if (day in eligible) active++; day = day.minusDays(1) }
        var best = 0; var run = 0; day = first
        val last = eligible.maxOrNull()!!
        while (day <= last) { if (day in eligible) run++ else if (day !in exemptions) run = 0; best = maxOf(best, run); day = day.plusDays(1) }
        return active to best
    }
}
