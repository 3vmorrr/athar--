package org.athar.app.nightprayer

import java.time.*

data class NightContext(val date: LocalDate, val zone: ZoneId, val maghrib: Instant, val fajr: Instant) {
    init { require(fajr > maghrib && Duration.between(maghrib, fajr).seconds <= 86400); require(maghrib.atZone(zone).toLocalDate() == date) }
    val lastThird: Instant get() = maghrib.plusSeconds(Duration.between(maghrib, fajr).seconds * 2 / 3)
    fun contains(instant: Instant) = instant >= maghrib && instant < fajr
}

data class NightIdentity(
    val nightDate: LocalDate,
    val inProgress: Boolean,
    val context: NightContext,
)

/**
 * Islamic-night identity is anchored to the local Maghrib date, not the civil date after midnight.
 * During daytime the returned date is the upcoming local Maghrib date and [inProgress] is false.
 */
fun resolveNightIdentity(
    now: Instant,
    zone: ZoneId,
    calculate: (LocalDate) -> NightContext,
): NightIdentity {
    val localDate = now.atZone(zone).toLocalDate()
    val today = calculate(localDate)
    if (today.contains(now)) {
        return NightIdentity(localDate, true, today)
    }

    val previousDate = localDate.minusDays(1)
    val previous = calculate(previousDate)
    if (previous.contains(now)) {
        return NightIdentity(previousDate, true, previous)
    }

    return NightIdentity(localDate, false, today)
}
data class NightPaceInsight(
    val sampleCount: Int,
    val usedSampleCount: Int,
    val measuredVerses: Int,
    val measuredSeconds: Int,
    val secondsPerVerse: Double,
)

object NightRules {
    fun serverTimezone(zone: ZoneId): String = if (zone.normalized() == ZoneOffset.UTC) "UTC" else zone.id
    fun milestone(count: Int): Int = when { count >= 1000 -> 1000; count >= 100 -> 100; count >= 10 -> 10; else -> 0 }
    fun pace(sessions: List<Pair<Int, Int>>): Double? =
        paceInsight(sessions)?.secondsPerVerse

    fun paceInsight(sessions: List<Pair<Int, Int>>): NightPaceInsight? {
        val valid = sessions.filter { (verses, seconds) ->
            verses in 1..100_000 && seconds in 1..86_400
        }
        if (valid.size < 2) return null

        val used = if (valid.size >= 5) {
            val rates = valid.map { (verses, seconds) ->
                seconds.toDouble() / verses.toDouble()
            }.sorted()
            fun percentile(fraction: Double): Double {
                val index = ((rates.lastIndex) * fraction)
                val lower = index.toInt()
                val upper = kotlin.math.ceil(index).toInt()
                if (lower == upper) return rates[lower]
                val weight = index - lower
                return rates[lower] * (1.0 - weight) + rates[upper] * weight
            }
            val q1 = percentile(0.25)
            val q3 = percentile(0.75)
            val iqr = (q3 - q1).coerceAtLeast(0.0)
            val low = q1 - 1.5 * iqr
            val high = q3 + 1.5 * iqr
            valid.filter { (verses, seconds) ->
                val rate = seconds.toDouble() / verses.toDouble()
                rate in low..high
            }.takeIf { it.size >= 2 } ?: valid
        } else {
            valid
        }

        val verses = used.sumOf { it.first.toLong() }
        val seconds = used.sumOf { it.second.toLong() }
        if (verses <= 0L || seconds <= 0L) return null

        return NightPaceInsight(
            sampleCount = valid.size,
            usedSampleCount = used.size,
            measuredVerses = verses.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            measuredSeconds = seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            secondsPerVerse = seconds.toDouble() / verses.toDouble(),
        )
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
