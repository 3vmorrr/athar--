package org.athar.app

import org.athar.app.adhkar.AdhkarCompletionPolicy
import org.athar.app.adhkar.AdhkarReminderPlanner
import org.athar.app.adhkar.AdhkarReminderRule
import org.athar.app.core.data.CounterState
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AdhkarReminderTest {
    private val requirements = mapOf(1 to 3, 2 to 1)
    private fun rule(enabled:Boolean=true) = AdhkarReminderRule(
        category = "Morning",
        enabled = enabled,
        time = "08:00",
        days = (1..7).toSet(),
        timezone = "Africa/Cairo",
        requirements = requirements,
        revision = 1,
        activatedAtMillis = Instant.parse("2026-10-26T00:00:00Z").toEpochMilli(),
    )

    private fun state(item:Int,target:Int,count:Int,day:String="2026-10-26",conflict:Boolean=false) =
        CounterState(item=item,target=target,count=count,revision=1,localVersion=1,conflict=conflict,day=day)

    @Test fun skippedAndPartialItemsNeverSuppressReminder() {
        val missing = AdhkarCompletionPolicy.evaluate(requirements, mapOf(1 to state(1,3,3)), "2026-10-26")
        assertFalse(missing.complete)
        assertEquals(1, missing.completedRequirements)
        val partial = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,2), 2 to state(2,1,1)),
            "2026-10-26",
        )
        assertFalse(partial.complete)
    }

    @Test fun repeatedRequirementsMustAllReachTheirExactTargets() {
        val complete = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,3), 2 to state(2,1,1)),
            "2026-10-26",
        )
        assertTrue(complete.complete)
        assertEquals(2, complete.completedRequirements)
    }

    @Test fun conflictOrPreviousDateNeverCountsAsCurrentCompletion() {
        val conflict = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,3,conflict=true), 2 to state(2,1,1)),
            "2026-10-26",
        )
        assertFalse(conflict.complete)
        val old = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,3,"2026-10-25"), 2 to state(2,1,1,"2026-10-25")),
            "2026-10-26",
        )
        assertFalse(old.complete)
    }

    @Test fun completionSuppressesOnlyTodaysPlannedReminder() {
        val incompletePlans = AdhkarReminderPlanner.plan(
            rule(),
            AdhkarCompletionPolicy.evaluate(requirements, emptyMap(), "2026-10-26"),
            Instant.parse("2026-10-26T03:00:00Z"),
            "Adhkar",
            "Body",
            2,
        )
        assertEquals(1, incompletePlans.size)
        val incompleteDate = Instant.ofEpochMilli(incompletePlans.single().scheduledAtMillis)
            .atZone(java.time.ZoneId.of("Africa/Cairo")).toLocalDate()
        assertEquals(java.time.LocalDate.parse("2026-10-26"), incompleteDate)

        val completePlans = AdhkarReminderPlanner.plan(
            rule(),
            AdhkarCompletionPolicy.evaluate(
                requirements,
                mapOf(1 to state(1,3,3), 2 to state(2,1,1)),
                "2026-10-26",
            ),
            Instant.parse("2026-10-26T03:00:00Z"),
            "Adhkar",
            "Body",
            2,
        )
        assertEquals(1, completePlans.size)
        val completeDate = Instant.ofEpochMilli(completePlans.single().scheduledAtMillis)
            .atZone(java.time.ZoneId.of("Africa/Cairo")).toLocalDate()
        assertEquals(java.time.LocalDate.parse("2026-10-27"), completeDate)
    }

    @Test fun nullRepeatItemsAreNotInventedAsRequirements() {
        val measurable = mapOf(7 to 5)
        val complete = AdhkarCompletionPolicy.evaluate(
            measurable,
            mapOf(7 to state(7,5,5)),
            "2026-10-26",
        )
        assertTrue(complete.complete)
        assertTrue(runCatching {
            AdhkarReminderRule(
                category="Only null repeats",
                enabled=true,
                time="08:00",
                days=setOf(1),
                timezone="UTC",
                requirements=emptyMap(),
                revision=1,
                activatedAtMillis=1,
            ).validated()
        }.isFailure)
    }

    @Test fun resetCountRemovesCompletionAgain() {
        val completed = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,3), 2 to state(2,1,1)),
            "2026-10-26",
        )
        assertTrue(completed.complete)
        val reset = AdhkarCompletionPolicy.evaluate(
            requirements,
            mapOf(1 to state(1,3,0), 2 to state(2,1,1)),
            "2026-10-26",
        )
        assertFalse(reset.complete)
    }
}
