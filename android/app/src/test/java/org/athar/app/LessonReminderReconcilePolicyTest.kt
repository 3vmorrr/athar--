package org.athar.app

import org.athar.app.learning.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LessonReminderReconcilePolicyTest {
    private fun event(status: EventStatus, enabled: Boolean = true, revision: Int = 8) = UpcomingLesson(
        id = 42,
        title = "Fixture",
        description = "",
        status = status,
        start = Instant.parse("2026-10-27T18:00:00Z"),
        scholarId = null,
        scholarName = "",
        seriesId = null,
        seriesName = "",
        durationMinutes = null,
        revision = revision,
        canWatch = false,
        eventId = 91,
        scheduleTimezone = "Africa/Cairo",
        remindersEnabled = enabled,
    )

    @Test fun plannerRejectsAuthoritativeNonScheduledStates() {
        listOf(EventStatus.LIVE, EventStatus.PUBLISHED, EventStatus.CANCELLED, EventStatus.DRAFT).forEach { status ->
            val result = LessonReminderPlanner.plan(
                event(status),
                LessonReminderChoice(42),
                "x",
                "y",
                Instant.parse("2026-10-27T12:00:00Z"),
            )
            assertEquals(LessonReminderPlanFailure.UNAVAILABLE, (result as LessonReminderPlanResult.Rejected).reason)
        }
    }

    @Test fun adminReminderDisableAlsoMakesEventUnschedulable() {
        val result = LessonReminderPlanner.plan(
            event(EventStatus.SCHEDULED, enabled = false),
            LessonReminderChoice(42),
            "x",
            "y",
            Instant.parse("2026-10-27T12:00:00Z"),
        )
        assertEquals(LessonReminderPlanFailure.UNAVAILABLE, (result as LessonReminderPlanResult.Rejected).reason)
    }
}
