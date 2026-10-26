package org.athar.app

import java.time.Instant
import java.time.ZoneId
import org.athar.app.khatmah.*
import org.junit.Assert.*
import org.junit.Test

class KhatmahReminderTest {
    private fun plan(
        status:String="active",
        completed:Int=100,
        start:String="2026-10-20",
        target:String="2026-11-20",
        conflict:Boolean=false,
    )=KhatmahPlanState(
        startDate=start,
        targetDate=target,
        completedPages=completed,
        status=status,
        revision=1,
        localVersion=1,
        conflict=conflict,
    )

    private fun rule(
        time:String="20:00",
        enabled:Boolean=true,
    )=KhatmahReminderRule(
        enabled=enabled,
        time=time,
        timezone="Africa/Cairo",
        revision=1,
        activatedAtMillis=Instant.parse("2026-10-26T08:00:00Z").toEpochMilli(),
    )

    @Test fun activePlanSchedulesOnlyRealRemainingWorkload() {
        val spec=requireNotNull(KhatmahReminderPlanner.next(
            rule(),
            plan(),
            Instant.parse("2026-10-26T10:00:00Z"),
            "Khatmah",
            { daily,remaining -> "$daily/$remaining" },
        ))
        assertTrue(spec.body.substringBefore('/').toInt()>0)
        assertTrue(spec.body.substringAfter('/').toInt()>0)
        assertEquals("khatmah",spec.category)
    }

    @Test fun pausedFinishedConflictAndCompletedPlansSuppress() {
        val now=Instant.parse("2026-10-26T10:00:00Z")
        val body:(Int,Int)->String={d,r->"$d/$r"}
        assertNull(KhatmahReminderPlanner.next(rule(),plan(status="paused"),now,"K",body))
        assertNull(KhatmahReminderPlanner.next(rule(),plan(status="finished",completed=604),now,"K",body))
        assertNull(KhatmahReminderPlanner.next(rule(),plan(conflict=true),now,"K",body))
        assertNull(KhatmahReminderPlanner.next(rule(),plan(completed=604),now,"K",body))
    }

    @Test fun futureStartWaitsUntilPlanActuallyHasDailyWorkload() {
        val spec=requireNotNull(KhatmahReminderPlanner.next(
            rule(time="09:00"),
            plan(start="2026-10-29",target="2026-11-10",completed=0),
            Instant.parse("2026-10-26T05:00:00Z"),
            "K",
            {d,r->"$d/$r"},
        ))
        val local=Instant.ofEpochMilli(spec.scheduledAtMillis).atZone(ZoneId.of("Africa/Cairo"))
        assertTrue(!local.toLocalDate().isBefore(java.time.LocalDate.parse("2026-10-29")))
    }

    @Test fun planEditUpdatesPayloadButKeepsSameDayDedupe() {
        val now=Instant.parse("2026-10-26T10:00:00Z")
        val first=requireNotNull(KhatmahReminderPlanner.next(rule(),plan(target="2026-11-20"),now,"K",{d,r->"$d/$r"}))
        val edited=requireNotNull(KhatmahReminderPlanner.next(rule(),plan(target="2026-12-20"),now,"K",{d,r->"$d/$r"}))
        assertEquals(first.idempotencyKey,edited.idempotencyKey)
        assertNotEquals(first.body,edited.body)
    }

    @Test fun expiredOverduePlanDoesNotInventWorkload() {
        assertNull(KhatmahReminderPlanner.next(
            rule(),
            plan(start="2026-09-01",target="2026-10-20",completed=200),
            Instant.parse("2026-10-26T10:00:00Z"),
            "K",
            {d,r->"$d/$r"},
        ))
    }
}
