package org.athar.app.notifications

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.athar.app.core.data.ProtectedRead

data class NotificationDiagnosticCounts(
    val ready: Int = 0,
    val deferred: Int = 0,
    val dropped: Int = 0,
    val consentBlocked: Int = 0,
) {
    val total: Int get() = ready + deferred + dropped + consentBlocked
}

data class NotificationDiagnosticsSnapshot(
    val masterEnabled: Boolean,
    val consentCurrent: Boolean,
    val quietHoursEnabled: Boolean,
    val hasDeliveryHistory: Boolean,
    val inboxPendingMutations: Int,
    val categoryAllowed: Map<String, Boolean>,
    val pendingByCategory: Map<String, Int>,
    val outcomes: NotificationDiagnosticCounts,
) {
    fun validated(): NotificationDiagnosticsSnapshot {
        require(categoryAllowed.keys == NotificationPreferenceContract.categories)
        require(pendingByCategory.keys == NotificationPreferenceContract.categories)
        require(pendingByCategory.values.all { it >= 0 })
        require(inboxPendingMutations >= 0)
        require(outcomes.total == pendingByCategory.values.sum())
        return this
    }
}

object NotificationDiagnosticsPolicy {
    fun build(
        preferences: NotificationPreferences,
        reminders: List<LocalReminderSpec>,
        ledger: NotificationPolicyLedger,
        globalPolicy: ReminderGlobalPolicy,
        inbox: NotificationInboxLocalState,
        now: Instant,
    ): NotificationDiagnosticsSnapshot {
        val pendingByCategory = NotificationPreferenceContract.categories.associateWith { category ->
            reminders.count { it.category == category }
        }
        var ready = 0
        var deferred = 0
        var dropped = 0
        var consentBlocked = 0
        val quiet = globalPolicy.quietHours()

        reminders.forEach { spec ->
            if (!preferences.allows(spec.category)) {
                consentBlocked++
                return@forEach
            }
            val decision = NotificationPolicy.evaluate(
                candidate = NotificationDeliveryCandidate(
                    idempotencyKey = spec.idempotencyKey,
                    category = spec.category,
                    scheduledAt = Instant.ofEpochMilli(spec.scheduledAtMillis),
                    expiresAt = Instant.ofEpochMilli(spec.expiresAtMillis),
                    minimumInterval = Duration.ofSeconds(spec.minimumIntervalSeconds),
                    quietHours = if (spec.quietStart == null) {
                        null
                    } else {
                        NotificationQuietHours(
                            start = LocalTime.parse(spec.quietStart),
                            end = LocalTime.parse(requireNotNull(spec.quietEnd)),
                            zoneId = ZoneId.of(requireNotNull(spec.timezone)),
                        )
                    },
                ),
                now = now,
                deliveredKeys = ledger.deliveredKeys(),
                lastDeliveredAt = ledger.lastDelivered(spec.category),
                lastDeliveredAnyAt = ledger.lastDeliveredAny(),
                globalQuietHours = quiet,
            )
            when (decision) {
                NotificationPolicyDecision.Deliver -> ready++
                is NotificationPolicyDecision.Defer -> deferred++
                is NotificationPolicyDecision.Drop -> dropped++
            }
        }

        return NotificationDiagnosticsSnapshot(
            masterEnabled = preferences.masterEnabled,
            consentCurrent = preferences.consent.current,
            quietHoursEnabled = globalPolicy.quietEnabled,
            hasDeliveryHistory = ledger.delivered.isNotEmpty(),
            inboxPendingMutations = inbox.pendingCount,
            categoryAllowed = NotificationPreferenceContract.categories.associateWith(preferences::allows),
            pendingByCategory = pendingByCategory,
            outcomes = NotificationDiagnosticCounts(
                ready = ready,
                deferred = deferred,
                dropped = dropped,
                consentBlocked = consentBlocked,
            ),
        ).validated()
    }
}

class NotificationDiagnosticsRepository(
    private val preferences: NotificationPreferenceRepository,
    private val reminders: LocalReminderStore,
    private val ledger: NotificationPolicyLedgerRepository,
    private val globalPolicy: ReminderGlobalPolicyRepository,
    private val inbox: NotificationInboxRepository,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun read(owner: Long): ProtectedRead<NotificationDiagnosticsSnapshot> {
        val pref = preferences.read(owner)
        val reminder = reminders.list(owner)
        val history = ledger.read(owner)
        val quiet = globalPolicy.read(owner)
        val inboxState = inbox.read(owner)

        if (
            pref is ProtectedRead.Unreadable ||
            reminder is ProtectedRead.Unreadable ||
            history is ProtectedRead.Unreadable ||
            quiet is ProtectedRead.Unreadable ||
            inboxState is ProtectedRead.Unreadable
        ) return ProtectedRead.Unreadable

        if (
            pref is ProtectedRead.Loading ||
            reminder is ProtectedRead.Loading ||
            history is ProtectedRead.Loading ||
            quiet is ProtectedRead.Loading ||
            inboxState is ProtectedRead.Loading
        ) return ProtectedRead.Loading

        return ProtectedRead.Ready(
            NotificationDiagnosticsPolicy.build(
                preferences = (pref as ProtectedRead.Ready).value,
                reminders = (reminder as ProtectedRead.Ready).value,
                ledger = (history as ProtectedRead.Ready).value,
                globalPolicy = (quiet as ProtectedRead.Ready).value,
                inbox = (inboxState as ProtectedRead.Ready).value,
                now = now(),
            ),
        )
    }
}
