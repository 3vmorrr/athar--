package org.athar.app.nightprayer

import java.time.LocalDate
import java.time.YearMonth

data class CalendarNight(
    val date: LocalDate,
    val verseCount: Int,
    val exempt: Boolean,
    val future: Boolean = false,
)

data class YearMonthNightSummary(
    val month: YearMonth,
    val days: List<CalendarNight>,
    val activeNights: Int,
    val verseCount: Int,
    val exemptDays: Int,
)

object NightCalendar {
    fun month(
        month: YearMonth,
        totals: Map<LocalDate, Int>,
        exemptions: Set<LocalDate> = emptySet(),
        today: LocalDate = LocalDate.now(),
    ): List<CalendarNight> =
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            CalendarNight(
                date = date,
                verseCount = (totals[date] ?: 0).coerceAtLeast(0),
                exempt = date in exemptions,
                future = date.isAfter(today),
            )
        }

    fun year(
        year: Int,
        totals: Map<LocalDate, Int>,
        exemptions: Set<LocalDate> = emptySet(),
        today: LocalDate = LocalDate.now(),
    ): List<YearMonthNightSummary> {
        require(year in 1900..today.year)
        return (1..12).map { monthNumber ->
            val month = YearMonth.of(year, monthNumber)
            val days = month(month, totals, exemptions, today)
            YearMonthNightSummary(
                month = month,
                days = days,
                activeNights = days.count { !it.future && it.verseCount > 0 },
                verseCount = days
                    .filterNot { it.future }
                    .sumOf { it.verseCount },
                exemptDays = days.count { !it.future && it.exempt },
            )
        }
    }
}
