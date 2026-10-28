package org.athar.app.core.data

import java.time.LocalDate

internal fun validCounterDay(value: String?): Boolean = value != null &&
    runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)

/** Pending history and a clock that moved backwards must never be silently reset. */
internal fun counterForDay(value: CounterState, target: Int, today: String): CounterState {
    require(validCounterDay(today) && validCounterDay(value.day))
    if (requireNotNull(value.day) >= today || value.pending || value.conflict || value.events.isNotEmpty()) return value
    return value.copy(target = target, count = 0, day = today, lastCompletion = null)
}

internal fun validCounterReceipt(snapshot: CounterState, count: Int, revision: Int, day: String): Boolean =
    validCounterDay(day) && validCounterDay(snapshot.day) && revision > snapshot.revision &&
        count in 0..snapshot.target && day >= requireNotNull(snapshot.day) &&
        (if (day == snapshot.day) count == snapshot.count else count == 0)
