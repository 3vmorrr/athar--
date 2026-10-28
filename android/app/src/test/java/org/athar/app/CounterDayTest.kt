package org.athar.app

import org.athar.app.core.data.*
import org.junit.Assert.*
import org.junit.Test

class CounterDayTest {
    private val yesterday = CounterState(1, 3, count = 3, revision = 7, day = "2026-10-27", lastCompletion = "acknowledged")
    @Test fun acknowledgedPreviousDayRollsOverWithoutLosingRevisionOrRecountingCompletion() {
        val today = counterForDay(yesterday, 3, "2026-10-28")
        assertEquals(0, today.count); assertEquals(7, today.revision)
        assertEquals("2026-10-28", today.day); assertNull(today.lastCompletion)
        assertFalse(today.pending); assertTrue(today.events.isEmpty())
    }
    @Test fun pendingHistoryConflictAndClockRollbackRetainTheExactState() {
        for (state in listOf(yesterday.copy(pending = true), yesterday.copy(conflict = true), yesterday.copy(events = listOf("queued"))))
            assertSame(state, counterForDay(state, 3, "2026-10-28"))
        assertSame(yesterday, counterForDay(yesterday, 3, "2026-10-26"))
    }
    @Test fun invalidCalendarDaysAndMalformedReceiptsCannotEnterProtectedState() {
        for (day in listOf("2026-02-30", "2026-2-3", "", "2026-13-01")) assertFalse(validCounterDay(day))
        assertTrue(validCounterDay("2028-02-29"))
        assertTrue(validCounterReceipt(yesterday, 3, 8, "2026-10-27"))
        assertTrue(validCounterReceipt(yesterday, 0, 8, "2026-10-28"))
        assertFalse(validCounterReceipt(yesterday, 3, 8, "2026-10-28"))
        assertFalse(validCounterReceipt(yesterday, 0, 7, "2026-10-28"))
        assertFalse(validCounterReceipt(yesterday, 0, 8, "2026-10-26"))
        assertFalse(validCounterReceipt(yesterday, -1, 8, "2026-10-27"))
    }
}
