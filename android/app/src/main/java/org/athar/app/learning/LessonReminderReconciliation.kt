package org.athar.app.learning

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.network.ApiException

class LessonReminderReconcileSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val input = workDataOf(
            LessonReminderReconcileWorker.ACTOR to owner,
            LessonReminderReconcileWorker.ENVIRONMENT to partition,
        )
        manager.enqueueUniqueWork(
            "lesson-reminder-reconcile-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<LessonReminderReconcileWorker>()
                .setInputData(input)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(ownerTag(owner))
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            "lesson-reminder-reconcile-periodic-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<LessonReminderReconcileWorker>(15, TimeUnit.MINUTES)
                .setInputData(input)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(ownerTag(owner))
                .build(),
        )
    }

    fun cancel(owner: Long) {
        if (owner > 0) manager.cancelAllWorkByTag(ownerTag(owner))
    }

    private fun ownerTag(owner: Long) = "lesson-reminder-reconcile-$partition-$owner"
}

class LessonReminderLifecycleCoordinator(
    context: Context,
    partition: String,
    private val session: SessionRepository,
    choices: LessonReminderChoiceStore,
    reminders: LessonReminderManager,
) {
    private val schedule = LessonReminderReconcileSchedule(context, partition)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Keep references constructor-visible: worker uses AppGraph; coordinator only owns lifecycle scheduling.
        @Suppress("UNUSED_VARIABLE") val ownedChoices = choices
        @Suppress("UNUSED_VARIABLE") val ownedReminders = reminders
        scope.launch {
            var previous: Long? = null
            session.state.collect { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (state !is AuthState.Restoring && owner != previous) {
                    previous?.let(schedule::cancel)
                    previous = owner
                    owner?.let(schedule::schedule)
                }
            }
        }
    }
}

class LessonReminderReconcileWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getLong(ACTOR, -1)
        val environment = inputData.getString(ENVIRONMENT)
        if (owner <= 0 || environment == null || !environment.matches(Regex("[a-f0-9]{12}"))) {
            return Result.failure(workDataOf("reason" to "invalid_input"))
        }
        val graph = (applicationContext as AtharApplication).graph
        if (environment != graph.partition) return Result.failure(workDataOf("reason" to "stale_environment"))

        try {
            if (graph.session.state.value is AuthState.Restoring) graph.session.restore()
            graph.session.requireVerifiedOwner(owner)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            LessonReminderReconcileSchedule(applicationContext, graph.partition).cancel(owner)
            return Result.failure(workDataOf("reason" to "auth_required"))
        }

        val choices = try {
            graph.lessonReminderChoices.all(owner)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return Result.failure(workDataOf("reason" to "choice_state_unreadable"))
        }

        var transientFailure = false
        for (choice in choices) {
            try {
                graph.session.requireVerifiedOwner(owner)
                val data = graph.session.request(
                    "GET",
                    "upcoming-lessons/" + choice.lessonId,
                    expectedUser = owner,
                )
                graph.lessonReminders.reconcileAuthoritative(UpcomingLesson.from(data), owner)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ApiException) {
                when (failure.status) {
                    404 -> {
                        try {
                            graph.lessonReminders.disable(choice.lessonId, owner)
                        } catch (cleanup: CancellationException) {
                            throw cleanup
                        } catch (_: Exception) {
                            return Result.failure(workDataOf("reason" to "cleanup_failed_closed"))
                        }
                    }
                    401, 403 -> {
                        LessonReminderReconcileSchedule(applicationContext, graph.partition).cancel(owner)
                        return Result.failure(workDataOf("reason" to "auth_required"))
                    }
                    else -> transientFailure = true
                }
            } catch (_: Exception) {
                transientFailure = true
            }
        }

        return if (transientFailure && runAttemptCount < 4) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val ACTOR = "actor_id"
        const val ENVIRONMENT = "environment"
    }
}
