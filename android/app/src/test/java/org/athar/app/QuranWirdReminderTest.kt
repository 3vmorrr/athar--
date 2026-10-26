package org.athar.app

import org.athar.app.quran.QuranWirdDayProgress
import org.athar.app.quran.QuranWirdPlanner
import org.athar.app.quran.QuranWirdProgressTracker
import org.athar.app.quran.QuranWirdRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class QuranWirdReminderTest {
    private fun rule(
        enabled: Boolean = true,
        timezone: String = "Africa/Cairo",
        days: Set<Int> = (1..7).toSet(),
        quietStart: String? = null,
        quietEnd: String? = null,
    ) = QuranWirdRule(
        enabled = enabled,
        targetAyahs = 10,
        time = "20:00",
        days = days,
        timezone = timezone,
        revision = 1,
        activatedAtMillis = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli(),
        quietStart = quietStart,
        quietEnd = quietEnd,
    )

    @Test fun openingWithoutSavedProgressNeverCompletesTheDay() {
        val plans = QuranWirdPlanner.plan(
            rule(),
            progress = null,
            now = Instant.parse("2026-10-26T12:00:00Z"),
            title = "Wird",
            body = "Body",
            horizonDays = 1,
        )
        assertEquals(1, plans.size)
    }

    @Test fun onlyForwardSavedPositionProgressCompletesTarget() {
        val date = LocalDate.parse("2026-10-26")
        val first = QuranWirdProgressTracker.advance(rule(), null, date, 100, 104)
        assertEquals(4, first.advancedAyahs())
        assertFalse(first.completed(rule()))
        val backward = QuranWirdProgressTracker.advance(rule(), first, date, 104, 90)
        assertEquals(4, backward.advancedAyahs())
        val complete = QuranWirdProgressTracker.advance(rule(), backward, date, 90, 111)
        assertTrue(complete.completed(rule()))
        val plans = QuranWirdPlanner.plan(
            rule(),
            complete,
            Instant.parse("2026-10-26T12:00:00Z"),
            "Wird",
            "Body",
            1,
        )
        assertTrue(plans.isEmpty())
    }

    @Test fun newDayResetsBaselineToPreviousSavedPosition() {
        val old = QuranWirdDayProgress("2026-10-26", 100, 120)
        val next = QuranWirdProgressTracker.advance(
            rule(),
            old,
            LocalDate.parse("2026-10-27"),
            previousGlobal = 120,
            currentGlobal = 123,
        )
        assertEquals(120, next.baselineGlobal)
        assertEquals(3, next.advancedAyahs())
        assertFalse(next.completed(rule()))
    }

    @Test fun plannerUsesIanaTimezoneAcrossDstAndSelectedDays() {
        val berlin = rule(timezone = "Europe/Berlin", days = setOf(7))
        val before = QuranWirdPlanner.plan(
            berlin, null, Instant.parse("2026-10-18T10:00:00Z"), "W", "B", 1
        ).single()
        val after = QuranWirdPlanner.plan(
            berlin, null, Instant.parse("2026-10-25T10:00:00Z"), "W", "B", 1
        ).single()
        assertEquals(20, Instant.ofEpochMilli(before.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin")).hour)
        assertEquals(20, Instant.ofEpochMilli(after.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin")).hour)
        assertNotEquals(
            Instant.ofEpochMilli(before.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin")).offset,
            Instant.ofEpochMilli(after.scheduledAtMillis).atZone(ZoneId.of("Europe/Berlin")).offset,
        )
    }

    @Test fun quietWindowIsCarriedIntoGenericPolicyAndWithdrawalRemovesPlans() {
        val quiet = rule(quietStart = "22:00", quietEnd = "07:00")
        val plan = QuranWirdPlanner.plan(
            quiet, null, Instant.parse("2026-10-26T12:00:00Z"), "W", "B", 1
        ).single()
        assertEquals("22:00", plan.quietStart)
        assertEquals("07:00", plan.quietEnd)
        assertEquals("Africa/Cairo", plan.timezone)
        assertTrue(
            QuranWirdPlanner.plan(
                rule(enabled = false), null, Instant.parse("2026-10-26T12:00:00Z"), "W", "B", 1
            ).isEmpty()
        )
    }
}
