package org.athar.app

import org.athar.app.notifications.LocalReminderSpec
import org.junit.Assert.*
import org.junit.Test

class LocalReminderSpecTest {
    private fun valid() = LocalReminderSpec(
        id = "lesson-42-20261027",
        category = "lesson_reminders",
        destinationToken = "lesson:42",
        title = "Lesson reminder",
        body = "The lesson starts soon.",
        scheduledAtMillis = 1_800_000_000_000,
        expiresAtMillis = 1_800_003_600_000,
        idempotencyKey = "lesson:42:2026-10-27T18:00Z",
        minimumIntervalSeconds = 900,
        quietStart = "22:00",
        quietEnd = "07:00",
        timezone = "Africa/Cairo",
    )

    @Test fun validEncryptedWorkSpecAcceptsAllowlistedDestination() {
        val value = valid()
        assertSame(value, value.validated())
    }

    @Test fun privateOrUnboundedFieldsAreRejectedBeforePersistence() {
        val base = valid()
        val cases = listOf(
            base.copy(destinationToken = "night:private-session-id"),
            base.copy(id = "../escape"),
            base.copy(title = "x".repeat(161)),
            base.copy(expiresAtMillis = base.scheduledAtMillis + 31L * 24 * 60 * 60 * 1000),
            base.copy(quietStart = "25:00"),
            base.copy(timezone = "Not/AZone"),
            base.copy(category = "campaigns"),
        )
        cases.forEach { assertTrue(runCatching { it.validated() }.isFailure) }
    }
}
