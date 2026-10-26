package org.athar.app.nightprayer

import java.time.LocalDate
import java.time.YearMonth

data class CalendarNight(val date: LocalDate, val verseCount: Int, val exempt: Boolean)
object NightCalendar {
    fun month(month: YearMonth, totals: Map<LocalDate, Int>, exemptions: Set<LocalDate> = emptySet()): List<CalendarNight> =
        (1..month.lengthOfMonth()).map { day -> val date = month.atDay(day); CalendarNight(date, (totals[date] ?: 0).coerceAtLeast(0), date in exemptions) }
}
