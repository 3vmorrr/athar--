package org.athar.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState

class ReminderReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACCEPTED_ACTIONS) return
        val graph = (context.applicationContext as? AtharApplication)?.graph ?: return
        ReminderReconcileSchedule(context, graph.partition).enqueue()
    }

    companion object {
        val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

class ReminderReconcileSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)

    init {
        require(partition.matches(Regex("[a-f0-9]{12}")))
    }

    fun enqueue() = manager.enqueueUniqueWork(
        "athar-reminder-reconcile-$partition",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<ReminderReconcileWorker>()
            .setInputData(workDataOf(ReminderReconcileWorker.ENVIRONMENT to partition))
            .build(),
    )
}

class ReminderReconcileWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val environment = inputData.getString(ENVIRONMENT)
        if (environment == null || !environment.matches(Regex("[a-f0-9]{12}"))) {
            return Result.failure(workDataOf("reason" to "invalid_environment"))
        }
        val graph = (applicationContext as AtharApplication).graph
        if (environment != graph.partition) {
            return Result.failure(workDataOf("reason" to "stale_environment"))
        }

        try {
            if (graph.session.state.value is AuthState.Restoring) graph.session.restore()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return Result.failure(workDataOf("reason" to "session_unavailable"))
        }

        val owner = (graph.session.state.value as? AuthState.SignedIn)?.user?.id
            ?: return Result.success()
        return try {
            graph.reminders.reconcile(owner)
            Result.success()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            Result.failure(workDataOf("reason" to "reconcile_failed_closed"))
        }
    }

    companion object {
        const val ENVIRONMENT = "environment"
    }
}
