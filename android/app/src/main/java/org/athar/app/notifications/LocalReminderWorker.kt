package org.athar.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import org.athar.app.core.network.ApiException

class LocalReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as AtharApplication).graph
        val owner = inputData.getLong(ACTOR, -1)
        val environment = inputData.getString(ENVIRONMENT)
        val reminderId = inputData.getString(REMINDER_ID)
        if (
            owner <= 0 ||
            environment == null ||
            !environment.matches(Regex("[a-f0-9]{12}")) ||
            reminderId == null ||
            !LocalReminderSpec.validId(reminderId)
        ) return Result.failure(workDataOf("reason" to "invalid_input"))

        if (environment != graph.partition) {
            LocalReminderWorkSchedule(applicationContext, environment).cancelOwner(owner)
            return Result.failure(workDataOf("reason" to "invalid_environment"))
        }

        try {
            if (graph.session.state.value is AuthState.Restoring) graph.session.restore()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return Result.failure(workDataOf("reason" to "session_unavailable"))
        }

        val outcome = try {
            graph.reminders.execute(owner, reminderId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ApiException) {
            if (failure.status in listOf(401, 403)) ReminderExecutionOutcome.AUTH_REQUIRED
            else ReminderExecutionOutcome.ACTION_REQUIRED
        } catch (_: Exception) {
            ReminderExecutionOutcome.ACTION_REQUIRED
        }

        return when (outcome) {
            ReminderExecutionOutcome.DELIVERED,
            ReminderExecutionOutcome.DROPPED,
            ReminderExecutionOutcome.DEFERRED,
            ReminderExecutionOutcome.MISSING -> Result.success()
            ReminderExecutionOutcome.PERMISSION_REQUIRED ->
                Result.success(workDataOf("reason" to "permission_required"))
            ReminderExecutionOutcome.AUTH_REQUIRED -> {
                LocalReminderWorkSchedule(applicationContext, graph.partition).cancelOwner(owner)
                Result.failure(workDataOf("reason" to "auth_required"))
            }
            ReminderExecutionOutcome.ACTION_REQUIRED ->
                Result.failure(workDataOf("reason" to "retained_data_unreadable"))
        }
    }

    companion object {
        const val ACTOR = "actor_id"
        const val ENVIRONMENT = "environment"
        const val REMINDER_ID = "reminder_id"
    }
}
