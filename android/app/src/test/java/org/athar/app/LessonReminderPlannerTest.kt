package org.athar.app

import org.athar.app.learning.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LessonReminderPlannerTest {
    private fun event(
        start: Instant = Instant.parse("2026-10-27T18:00:00Z"),
        revision: Int = 7,
        timezone: String = "Africa/Cairo",
        enabled: Boolean = true,
    ) = UpcomingLesson(
        id = 42, title = "Fixture lesson", description = "", status = EventStatus.SCHEDULED, start = start,
        scholarId = null, scholarName = "", seriesId = null, seriesName = "", durationMinutes = 60,
        revision = revision, canWatch = false, eventId = 91, scheduleTimezone = timezone, remindersEnabled = enabled,
    )

    @Test fun canonicalInstantProducesSingleFifteenMinuteReminder() {
        val start = Instant.parse("2026-10-27T18:00:00Z")
        val result = LessonReminderPlanner.plan(
            event(), LessonReminderChoice(42), "Fixture", "Starts in 15 minutes",
            Instant.parse("2026-10-27T12:00:00Z"),
        ) as LessonReminderPlanResult.Ready
        assertEquals(start.minusSeconds(900).toEpochMilli(), result.spec.scheduledAtMillis)
        assertEquals(start.toEpochMilli(), result.spec.expiresAtMillis)
        assertEquals("lesson:42", result.spec.destinationToken)
        assertEquals("Africa/Cairo", result.binding.sourceTimezone)
        assertEquals(7, result.binding.eventRevision)
    }

    @Test fun revisionChangesReplaceSameWorkWithNewIdempotencyFact() {
        val a = LessonReminderPlanner.plan(event(revision=7),LessonReminderChoice(42),"x","y",Instant.parse("2026-10-27T12:00:00Z")) as LessonReminderPlanResult.Ready
        val b = LessonReminderPlanner.plan(event(revision=8),LessonReminderChoice(42),"x","y",Instant.parse("2026-10-27T12:00:00Z")) as LessonReminderPlanResult.Ready
        assertEquals(a.spec.id,b.spec.id)
        assertNotEquals(a.spec.idempotencyKey,b.spec.idempotencyKey)
    }

    @Test fun missedWindowAndInvalidFactsDoNotBecomeImmediateReminder() {
        val late=LessonReminderPlanner.plan(event(),LessonReminderChoice(42),"x","y",Instant.parse("2026-10-27T17:50:00Z")) as LessonReminderPlanResult.Rejected
        assertEquals(LessonReminderPlanFailure.TOO_LATE,late.reason)
        val zone=LessonReminderPlanner.plan(event(timezone="Not/AZone"),LessonReminderChoice(42),"x","y",Instant.parse("2026-10-27T12:00:00Z")) as LessonReminderPlanResult.Rejected
        assertEquals(LessonReminderPlanFailure.UNAVAILABLE,zone.reason)
        val disabled=LessonReminderPlanner.plan(event(enabled=false),LessonReminderChoice(42),"x","y",Instant.parse("2026-10-27T12:00:00Z")) as LessonReminderPlanResult.Rejected
        assertEquals(LessonReminderPlanFailure.UNAVAILABLE,disabled.reason)
    }
}
