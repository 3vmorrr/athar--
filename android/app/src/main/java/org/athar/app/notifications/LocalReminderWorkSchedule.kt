package org.athar.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max

class LocalReminderWorkSchedule(
    context: Context,
    private val partition: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)

    init {
        require(partition.matches(Regex("[a-f0-9]{12}")))
    }

    fun enqueue(owner: Long, reminderId: String, atMillis: Long): Operation =
        manager.enqueueUniqueWork(
            name(owner, reminderId),
            ExistingWorkPolicy.REPLACE,
            request(owner, reminderId, atMillis),
        )

    fun defer(owner: Long, reminderId: String, atMillis: Long): Operation =
        manager.enqueueUniqueWork(
            name(owner, reminderId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(owner, reminderId, atMillis),
        )

    fun cancel(owner: Long, reminderId: String): Operation =
        manager.cancelUniqueWork(name(owner, reminderId))

    fun cancelOwner(owner: Long): Operation {
        require(owner > 0)
        return manager.cancelAllWorkByTag(ownerTag(owner))
    }

    private fun request(owner: Long, reminderId: String, atMillis: Long) =
        OneTimeWorkRequestBuilder<LocalReminderWorker>()
            .setInputData(
                workDataOf(
                    LocalReminderWorker.ACTOR to owner,
                    LocalReminderWorker.ENVIRONMENT to partition,
                    LocalReminderWorker.REMINDER_ID to reminderId,
                ),
            )
            .addTag(ownerTag(owner))
            .setInitialDelay(max(0L, atMillis - now()), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

    private fun name(owner: Long, reminderId: String): String {
        require(owner > 0)
        require(LocalReminderSpec.validId(reminderId))
        return "athar-reminder-$partition-$owner-${digest(reminderId)}"
    }

    private fun ownerTag(owner: Long) = "athar-reminders-$partition-$owner"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
