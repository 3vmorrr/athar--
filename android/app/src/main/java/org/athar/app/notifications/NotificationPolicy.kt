package org.athar.app.notifications

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class NotificationQuietHours(
    val start: LocalTime,
    val end: LocalTime,
    val zoneId: ZoneId,
)

data class NotificationDeliveryCandidate(
    val idempotencyKey: String,
    val category: String,
    val scheduledAt: Instant,
    val expiresAt: Instant,
    val minimumInterval: Duration = Duration.ZERO,
    val quietHours: NotificationQuietHours? = null,
) {
    init {
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 160)
        require(category in NotificationPreferenceContract.categories || category == "campaigns")
        require(expiresAt.isAfter(scheduledAt))
        require(!minimumInterval.isNegative)
    }
}

enum class NotificationDropReason {
    EXPIRED,
    DUPLICATE,
    POLICY_EXCEEDS_TTL,
}

sealed interface NotificationPolicyDecision {
    data object Deliver : NotificationPolicyDecision
    data class Defer(val until: Instant) : NotificationPolicyDecision
    data class Drop(val reason: NotificationDropReason) : NotificationPolicyDecision
}

object NotificationPolicy {
    fun evaluate(
        candidate: NotificationDeliveryCandidate,
        now: Instant,
        deliveredKeys: Set<String> = emptySet(),
        lastDeliveredAt: Instant? = null,
        lastDeliveredAnyAt: Instant? = null,
        globalQuietHours: NotificationQuietHours? = null,
    ): NotificationPolicyDecision {
        if (candidate.idempotencyKey in deliveredKeys) {
            return NotificationPolicyDecision.Drop(NotificationDropReason.DUPLICATE)
        }
        if (!now.isBefore(candidate.expiresAt)) {
            return NotificationPolicyDecision.Drop(NotificationDropReason.EXPIRED)
        }

        var eligible = maxOf(now, candidate.scheduledAt)
        if (!candidate.minimumInterval.isZero) {
            val last = listOfNotNull(lastDeliveredAt, lastDeliveredAnyAt).maxOrNull()
            if (last != null) eligible = maxOf(eligible, last.plus(candidate.minimumInterval))
        }
        val quietWindows = listOfNotNull(candidate.quietHours, globalQuietHours)
        repeat(8) {
            val next = quietWindows.mapNotNull { quietEndIfInside(eligible, it) }.maxOrNull()
                ?: return@repeat
            if (!next.isAfter(eligible)) return@repeat
            eligible = next
        }

        if (!eligible.isBefore(candidate.expiresAt)) {
            return NotificationPolicyDecision.Drop(NotificationDropReason.POLICY_EXCEEDS_TTL)
        }
        return if (eligible.isAfter(now)) {
            NotificationPolicyDecision.Defer(eligible)
        } else {
            NotificationPolicyDecision.Deliver
        }
    }

    fun quietEndIfInside(at: Instant, quiet: NotificationQuietHours): Instant? {
        val local = at.atZone(quiet.zoneId)
        val day = local.toLocalDate()
        var start = ZonedDateTime.of(day, quiet.start, quiet.zoneId)
        var end = ZonedDateTime.of(day, quiet.end, quiet.zoneId)
        if (!end.isAfter(start)) {
            if (local.isBefore(end)) start = start.minusDays(1) else end = end.plusDays(1)
        }
        return if (!local.isBefore(start) && local.isBefore(end)) end.toInstant() else null
    }
}
