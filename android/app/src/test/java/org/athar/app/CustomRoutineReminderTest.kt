package org.athar.app

import java.time.Instant
import java.time.ZoneId
import org.athar.app.notifications.CustomRoutineCodec
import org.athar.app.notifications.CustomRoutinePlanner
import org.athar.app.notifications.CustomRoutineRule
import org.athar.app.notifications.CustomRoutineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRoutineReminderTest {
    private fun createRule(
        id: String = "0123456789abcdef",
        label: String = "Read",
        enabled: Boolean = true,
        time: String = "18:00",
        activeDays: kotlin.collections.Set<Int> = setOf(1, 3, 5),
    ): CustomRoutineRule {
        return CustomRoutineRule(
            id = id,
            label = label,
            enabled = enabled,
            time = time,
            days = activeDays,
            timezone = "Africa/Cairo",
            revision = 1,
            activatedAtMillis = Instant
                .parse("2026-10-26T00:00:00Z")
                .toEpochMilli(),
        )
    }

    @Test
    fun validationIsBoundedAndExplicit() {
        assertTrue(runCatching { createRule(label = "").validated() }.isFailure)
        assertTrue(
            runCatching {
                createRule(label = "x".repeat(61)).validated()
            }.isFailure,
        )
        assertTrue(
            runCatching {
                createRule(activeDays = emptySet()).validated()
            }.isFailure,
        )
        assertTrue(runCatching { createRule(id = "bad").validated() }.isFailure)
        assertTrue(createRule().validated().enabled)
    }

    @Test
    fun multipleSchedulesRemainIndependent() {
        val first = createRule(
            id = "0123456789abcdef",
            label = "A",
            time = "10:00",
        )
        val second = createRule(
            id = "fedcba9876543210",
            label = "B",
            time = "20:00",
        )
        val now = Instant.parse("2026-10-26T05:00:00Z")
        val firstPlan = requireNotNull(
            CustomRoutinePlanner.next(first, now, "Custom"),
        )
        val secondPlan = requireNotNull(
            CustomRoutinePlanner.next(second, now, "Custom"),
        )
        assertNotEquals(firstPlan.id, secondPlan.id)
        assertNotEquals(firstPlan.idempotencyKey, secondPlan.idempotencyKey)
    }

    @Test
    fun editKeepsStableIdentityAndRestartRoundTrips() {
        val original = createRule()
        val edited = original.copy(
            label = "Edited",
            time = "21:30",
            revision = 2,
        )
        val state = CustomRoutineState(emptyList())
            .upsert(original)
            .upsert(edited)

        assertEquals(1, state.rules.size)
        assertEquals("Edited", state.rules.single().label)

        val restored = CustomRoutineCodec.decode(
            CustomRoutineCodec.encode(state),
        )
        assertEquals(state, restored)
    }

    @Test
    fun pauseAndDeleteAreDeterministic() {
        val paused = createRule(enabled = false)
        assertFalse(paused.enabled)
        assertNull(
            CustomRoutinePlanner.next(
                paused,
                Instant.parse("2026-10-26T05:00:00Z"),
                "Custom",
            ),
        )

        val state = CustomRoutineState(listOf(createRule())).validated()
        assertTrue(
            state.remove("0123456789abcdef").rules.isEmpty(),
        )
    }

    @Test
    fun plannerUsesLocalDayAndExpiresBeforeAnotherDay() {
        val plan = requireNotNull(
            CustomRoutinePlanner.next(
                createRule(
                    time = "18:00",
                    activeDays = setOf(1),
                ),
                Instant.parse("2026-10-26T05:00:00Z"),
                "Custom",
            ),
        )

        val zone = ZoneId.of("Africa/Cairo")
        val due = Instant
            .ofEpochMilli(plan.scheduledAtMillis)
            .atZone(zone)
        val expiry = Instant
            .ofEpochMilli(plan.expiresAtMillis)
            .atZone(zone)

        assertEquals(18, due.hour)
        assertEquals(due.toLocalDate().plusDays(1), expiry.toLocalDate())
        assertEquals(0, expiry.hour)
    }
}
