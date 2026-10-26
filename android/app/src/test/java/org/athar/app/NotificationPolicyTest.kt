package org.athar.app

import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class NotificationPolicyTest {
    private val zone = ZoneId.of("Africa/Cairo")

    @Test fun crossingMidnightQuietHoursDefersInUserTimezone() {
        val quiet = NotificationQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), zone)
        val now = Instant.parse("2026-01-26T21:30:00Z") // 23:30 Cairo outside DST
        val end = NotificationPolicy.quietEndIfInside(now, quiet)
        assertEquals(Instant.parse("2026-01-27T05:00:00Z"), end)
    }

    @Test fun duplicateAndExpiredCandidatesFailClosed() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "fixture-1",
            category = "lesson_reminders",
            scheduledAt = Instant.parse("2026-10-26T10:00:00Z"),
            expiresAt = Instant.parse("2026-10-26T11:00:00Z"),
        )
        assertEquals(
            NotificationPolicyDecision.Drop(NotificationDropReason.DUPLICATE),
            NotificationPolicy.evaluate(candidate, Instant.parse("2026-10-26T10:30:00Z"), setOf("fixture-1")),
        )
        assertEquals(
            NotificationPolicyDecision.Drop(NotificationDropReason.EXPIRED),
            NotificationPolicy.evaluate(candidate, Instant.parse("2026-10-26T11:00:00Z")),
        )
    }

    @Test fun frequencyAndQuietDeferralNeverEscapeTtl() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "fixture-2",
            category = "following_updates",
            scheduledAt = Instant.parse("2026-10-26T20:00:00Z"),
            expiresAt = Instant.parse("2026-10-27T04:00:00Z"),
            minimumInterval = Duration.ofHours(6),
            quietHours = NotificationQuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), zone),
        )
        val decision = NotificationPolicy.evaluate(
            candidate = candidate,
            now = Instant.parse("2026-10-26T20:00:00Z"),
            lastDeliveredAt = Instant.parse("2026-10-26T18:00:00Z"),
        )
        assertEquals(
            NotificationPolicyDecision.Drop(NotificationDropReason.POLICY_EXCEEDS_TTL),
            decision,
        )
    }

    @Test fun eligibleCandidateDeliversImmediately() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "fixture-3",
            category = "quran_routine",
            scheduledAt = Instant.parse("2026-10-26T10:00:00Z"),
            expiresAt = Instant.parse("2026-10-26T12:00:00Z"),
        )
        assertEquals(
            NotificationPolicyDecision.Deliver,
            NotificationPolicy.evaluate(candidate, Instant.parse("2026-10-26T10:15:00Z")),
        )
    }


    @Test fun minimumIntervalUsesLastDeliveryAcrossDifferentCategories() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "cross-category-spacing",
            category = "quran_routine",
            scheduledAt = Instant.parse("2026-10-26T10:00:00Z"),
            expiresAt = Instant.parse("2026-10-26T12:00:00Z"),
            minimumInterval = Duration.ofMinutes(30),
        )
        assertEquals(
            NotificationPolicyDecision.Defer(Instant.parse("2026-10-26T10:25:00Z")),
            NotificationPolicy.evaluate(
                candidate = candidate,
                now = Instant.parse("2026-10-26T10:05:00Z"),
                lastDeliveredAnyAt = Instant.parse("2026-10-26T09:55:00Z"),
            ),
        )
    }

    @Test fun globalAndRuleQuietWindowsComposeAcrossCategories() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "overlap-quiet",
            category = "adhkar_routine",
            scheduledAt = Instant.parse("2026-01-26T19:30:00Z"),
            expiresAt = Instant.parse("2026-01-27T06:00:00Z"),
            quietHours = NotificationQuietHours(LocalTime.of(21,0),LocalTime.of(23,0),zone),
        )
        assertEquals(
            NotificationPolicyDecision.Defer(Instant.parse("2026-01-27T05:00:00Z")),
            NotificationPolicy.evaluate(
                candidate = candidate,
                now = Instant.parse("2026-01-26T19:30:00Z"),
                globalQuietHours = NotificationQuietHours(LocalTime.of(22,0),LocalTime.of(7,0),zone),
            ),
        )
    }

    @Test fun globalQuietHoursNeverPushReminderPastItsTtl() {
        val candidate = NotificationDeliveryCandidate(
            idempotencyKey = "quiet-ttl",
            category = "night_prayer",
            scheduledAt = Instant.parse("2026-01-26T21:00:00Z"),
            expiresAt = Instant.parse("2026-01-27T03:00:00Z"),
        )
        assertEquals(
            NotificationPolicyDecision.Drop(NotificationDropReason.POLICY_EXCEEDS_TTL),
            NotificationPolicy.evaluate(
                candidate = candidate,
                now = Instant.parse("2026-01-26T21:00:00Z"),
                globalQuietHours = NotificationQuietHours(LocalTime.of(22,0),LocalTime.of(7,0),zone),
            ),
        )
    }

}