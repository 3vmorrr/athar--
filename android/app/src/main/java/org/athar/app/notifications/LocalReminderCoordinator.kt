package org.athar.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.athar.app.R
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.ProtectedRead

enum class ReminderExecutionOutcome {
    DELIVERED,
    DROPPED,
    DEFERRED,
    PERMISSION_REQUIRED,
    MISSING,
    AUTH_REQUIRED,
    ACTION_REQUIRED,
    CONSENT_REQUIRED,
}

class LocalReminderCoordinator(
    context: Context,
    private val partition: String,
    private val session: SessionRepository,
    private val store: LocalReminderStore,
    private val ledger: NotificationPolicyLedgerRepository,
    private val preferences: NotificationPreferenceRepository,
    private val globalPolicy: ReminderGlobalPolicyRepository,
) {
    private val app = context.applicationContext
    private val schedule = LocalReminderWorkSchedule(app, partition)
    private val poster = LocalNotificationPoster(app, partition)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            var previousOwner: Long? = null
            session.state.collect { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (state !is AuthState.Restoring && owner != previousOwner) {
                    previousOwner?.let {
                        schedule.cancelOwner(it)
                        poster.cancelOwner(it)
                    }
                    previousOwner = owner
                    owner?.let {
                        runCatching { reconcile(it) }
                            .onFailure { failure -> if (failure is CancellationException) throw failure }
                    }
                }
            }
        }
    }

    suspend fun save(
        spec: LocalReminderSpec,
        expectedOwner: Long? = session.userId,
    ): LocalReminderSpec {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val saved = store.upsert(spec, owner)
        session.requireVerifiedOwner(owner)
        schedule.enqueue(owner, saved.id, saved.scheduledAtMillis)
        return saved
    }

    suspend fun remove(
        reminderId: String,
        expectedOwner: Long? = session.userId,
    ): Boolean {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val removed = store.remove(reminderId, owner)
        schedule.cancel(owner, reminderId)
        return removed
    }

    suspend fun reconcile(expectedOwner: Long? = session.userId): Int {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val reminders = when (val read = store.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("notification_schedule_unreadable")
        }
        session.requireVerifiedOwner(owner)
        reminders.forEach { schedule.enqueue(owner, it.id, it.scheduledAtMillis) }
        return reminders.size
    }

    suspend fun execute(owner: Long, reminderId: String): ReminderExecutionOutcome {
        try {
            session.requireVerifiedOwner(owner)
        } catch (_: Exception) {
            return ReminderExecutionOutcome.AUTH_REQUIRED
        }

        val spec = when (val read = store.find(owner, reminderId)) {
            is ProtectedRead.Ready -> read.value ?: return ReminderExecutionOutcome.MISSING
            ProtectedRead.Loading, ProtectedRead.Unreadable -> return ReminderExecutionOutcome.ACTION_REQUIRED
        }
        val preferenceState = when (val read = preferences.read(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> return ReminderExecutionOutcome.ACTION_REQUIRED
        }
        if (!preferenceState.allows(spec.category)) return ReminderExecutionOutcome.CONSENT_REQUIRED
        val globalPolicyState = when (val read = globalPolicy.read(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> return ReminderExecutionOutcome.ACTION_REQUIRED
        }

        val policyLedger = when (val read = ledger.read(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> return ReminderExecutionOutcome.ACTION_REQUIRED
        }
        val quiet = if (spec.quietStart != null) {
            NotificationQuietHours(
                LocalTime.parse(spec.quietStart),
                LocalTime.parse(requireNotNull(spec.quietEnd)),
                ZoneId.of(requireNotNull(spec.timezone)),
            )
        } else {
            null
        }
        val now = Instant.now()
        val decision = NotificationPolicy.evaluate(
            candidate = NotificationDeliveryCandidate(
                idempotencyKey = spec.idempotencyKey,
                category = spec.category,
                scheduledAt = Instant.ofEpochMilli(spec.scheduledAtMillis),
                expiresAt = Instant.ofEpochMilli(spec.expiresAtMillis),
                minimumInterval = Duration.ofSeconds(spec.minimumIntervalSeconds),
                quietHours = quiet,
            ),
            now = now,
            deliveredKeys = policyLedger.deliveredKeys(),
            lastDeliveredAt = policyLedger.lastDelivered(spec.category),
            lastDeliveredAnyAt = policyLedger.lastDeliveredAny(),
            globalQuietHours = globalPolicyState.quietHours(),
        )
        return when (decision) {
            is NotificationPolicyDecision.Drop -> {
                store.remove(spec.id, owner)
                ReminderExecutionOutcome.DROPPED
            }
            is NotificationPolicyDecision.Defer -> {
                session.requireVerifiedOwner(owner)
                schedule.defer(owner, spec.id, decision.until.toEpochMilli())
                ReminderExecutionOutcome.DEFERRED
            }
            NotificationPolicyDecision.Deliver -> {
                session.requireVerifiedOwner(owner)
                if (!poster.post(owner, spec)) {
                    return ReminderExecutionOutcome.PERMISSION_REQUIRED
                }
                ledger.recordDelivered(spec.idempotencyKey, spec.category, now, owner)
                session.requireVerifiedOwner(owner)
                store.remove(spec.id, owner)
                ReminderExecutionOutcome.DELIVERED
            }
        }
    }
}

private class LocalNotificationPoster(
    private val context: Context,
    private val partition: String,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun canPost(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    fun post(owner: Long, spec: LocalReminderSpec): Boolean {
        if (!canPost()) return false
        val destination = NotificationDeepLinks.decode(spec.destinationToken)
            ?: throw IllegalArgumentException("Unsupported notification destination")
        val channel = NotificationChannels.channelIdForCategory(spec.category)
            ?: throw IllegalArgumentException("Unsupported notification category")
        val id = notificationId(spec.idempotencyKey)
        val pending = PendingIntent.getActivity(
            context,
            id,
            NotificationDeepLinks.activityIntent(context, owner, destination),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_athar)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            manager.notify(ownerTag(owner), id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelOwner(owner: Long) {
        val system = context.getSystemService(NotificationManager::class.java)
        system.activeNotifications
            .filter { it.tag == ownerTag(owner) }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun ownerTag(owner: Long) = "athar-owner-$partition-$owner"

    private fun notificationId(key: String): Int =
        (key.hashCode() and 0x7fffffff).coerceAtLeast(1)
}
