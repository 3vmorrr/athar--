package org.athar.app.core.sync

import android.content.Context
import androidx.work.*
import org.athar.app.AppGraph
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import java.util.Locale
import kotlinx.coroutines.CancellationException

internal class GraphSyncOperations(private val graph:AppGraph):OwnedSyncOperations {
    override val owner:Long? get(){val id=graph.session.userId;return id?.takeIf{(graph.session.state.value as? AuthState.SignedIn)?.user?.id==it&&!graph.deletionStore.isBlocked(it)}}
    override suspend fun push(module:SyncModule,owner:Long){
        when(module){
            SyncModule.READING->graph.reading.sync(owner)
            SyncModule.COUNTERS->graph.counters.sync(owner)
            SyncModule.LESSONS->graph.lessons.sync(owner)
            SyncModule.NIGHT_PRAYER->graph.nightPrayer.sync(owner)
            SyncModule.ACTIVITY->graph.bookActivity.sync(owner)
        }
    }
    suspend fun hasPending(module:SyncModule,owner:Long):Boolean=when(module){
        SyncModule.READING->graph.reading.hasPending(owner)
        SyncModule.COUNTERS->graph.counters.hasPending(owner)
        SyncModule.LESSONS->graph.lessons.hasPending(owner)
        SyncModule.NIGHT_PRAYER->graph.nightPrayer.hasPending(owner)
        SyncModule.ACTIVITY->graph.bookActivity.hasPending(owner)
    }
    override suspend fun hasPending(owner:Long)=SyncModule.entries.any{hasPending(it,owner)}
}

class PrivateSyncWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        val graph=(applicationContext as AtharApplication).graph
        val actor=inputData.getLong(ACTOR,-1)
        val environment=inputData.getString(ENVIRONMENT)
        if(actor<=0||environment==null||!environment.matches(Regex("[a-f0-9]{12}")))return Result.failure(workDataOf("reason" to "invalid_environment"))
        if(environment!=graph.partition){PrivateSyncSchedule(applicationContext,environment).cancel(actor);return Result.failure(workDataOf("reason" to "invalid_environment"))}
        try{if(graph.session.state.value is AuthState.Restoring)graph.session.restore()}
        catch(failure:CancellationException){throw failure}
        catch(_:Exception){return Result.failure(workDataOf("reason" to "session_unavailable"))}
        val outcome=OwnedSyncRunner(GraphSyncOperations(graph)).run(actor)
        return when(outcome){
            SyncOutcome.COMPLETE->Result.success()
            SyncOutcome.RETRY->if(runAttemptCount+1<MAX_ATTEMPTS)Result.retry()else Result.failure(workDataOf("reason" to "retry_exhausted"))
            SyncOutcome.AUTH_REQUIRED->{PrivateSyncSchedule(applicationContext,graph.partition).cancel(actor);Result.failure(workDataOf("reason" to "auth_required"))}
            SyncOutcome.ACTION_REQUIRED->Result.failure(workDataOf("reason" to outcome.name.lowercase(Locale.ROOT)))
        }
    }
    companion object { const val ACTOR="actor_id";const val ENVIRONMENT="environment";const val MAX_ATTEMPTS=8 }
}
