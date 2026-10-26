package org.athar.app.notifications

import android.content.Context
import androidx.work.*
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionLifecycleHooks
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.network.ApiException

interface DevicePushTokenSource {
    suspend fun currentToken(): String?
}

object NoopDevicePushTokenSource : DevicePushTokenSource {
    override suspend fun currentToken(): String? = null
}

object PushInstallationContract {
    fun valid(value: String): Boolean = value.matches(Regex("[a-f0-9]{32}"))
    fun generate(): String {
        val bytes=ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

class PushInstallationIdStore(
    context: Context,
    private val environment: String,
) {
    private val preferences=context.applicationContext
        .getSharedPreferences("athar-push-installations",Context.MODE_PRIVATE)

    init { require(environment.matches(Regex("[a-f0-9]{12}"))) }

    @Synchronized
    fun id(): String {
        val key="installation-"+environment
        val current=preferences.getString(key,null)
        if(current!=null&&PushInstallationContract.valid(current))return current
        val generated=PushInstallationContract.generate()
        check(preferences.edit().putString(key,generated).commit())
        return generated
    }
}

object PushDeviceSyncPolicy {
    fun shouldRetry(status: Int, runAttemptCount: Int): Boolean =
        runAttemptCount < 5 && (status == 408 || status == 425 || status == 429 || status >= 500)
}

class PushDeviceLifecycleManager(
    context: Context,
    private val partition: String,
    private val session: SessionRepository,
    private val store: PushDeviceTokenStore,
    private val source: DevicePushTokenSource = NoopDevicePushTokenSource,
) : SessionLifecycleHooks {
    private val app=context.applicationContext
    private val installation=PushInstallationIdStore(app,partition)
    private val work=WorkManager.getInstance(app)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)

    init {
        require(partition.matches(Regex("[a-f0-9]{12}")))
        scope.launch {
            var previousOwner:Long?=null
            session.state.collectLatest { state ->
                val owner=(state as? AuthState.SignedIn)?.user?.id
                if(owner==null){
                    previousOwner?.let(::cancel)
                    previousOwner=null
                    return@collectLatest
                }
                if(previousOwner!=null&&previousOwner!=owner)cancel(requireNotNull(previousOwner))
                previousOwner=owner
                reconcile(owner)
            }
        }
    }

    suspend fun reconcile(owner: Long) {
        session.requireVerifiedOwner(owner)
        try {
            source.currentToken()?.let { store.save(owner,it) }
        } catch (failure:CancellationException) {
            throw failure
        } catch (_:Exception) {
            // A retained encrypted token can still be retried without provider access.
        }
        when(val state=store.read(owner)){
            is ProtectedRead.Ready -> if(state.value!=null)schedule(owner)
            ProtectedRead.Loading -> Unit
            ProtectedRead.Unreadable -> Unit
        }
    }

    suspend fun onTokenRotated(token: String) {
        val owner=session.userId ?: return
        session.requireVerifiedOwner(owner)
        store.save(owner,token)
        schedule(owner)
    }

    suspend fun sync(owner: Long) {
        session.requireVerifiedOwner(owner)
        val token=when(val state=store.read(owner)){
            is ProtectedRead.Ready -> state.value ?: return
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("push_token_unreadable")
        }
        session.request(
            "PUT",
            "me/push-devices/"+installation.id(),
            mapOf(
                "provider" to token.provider,
                "environment" to partition,
                "token" to token.token,
            ),
            expectedUser=owner,
        )
    }

    override suspend fun beforeLogout(owner: Long) {
        try {
            session.requireVerifiedOwner(owner)
            session.request(
                "DELETE",
                "me/push-devices/"+installation.id(),
                expectedUser=owner,
            )
        } finally {
            cancel(owner)
            store.clear(owner)
        }
    }

    private fun schedule(owner: Long) {
        val request=OneTimeWorkRequestBuilder<PushDeviceSyncWorker>()
            .setInputData(workDataOf(
                PushDeviceSyncWorker.ACTOR to owner,
                PushDeviceSyncWorker.ENVIRONMENT to partition,
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .addTag(tag(owner))
            .build()
        work.enqueueUniqueWork(
            "push-device-sync-$partition-$owner-" + installation.id(),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancel(owner: Long) {
        if(owner>0)work.cancelAllWorkByTag(tag(owner))
    }

    private fun tag(owner: Long)="push-device-sync-$partition-$owner"
}

class PushDeviceSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val owner=inputData.getLong(ACTOR,-1)
        val environment=inputData.getString(ENVIRONMENT)
        if(owner<=0||environment==null||!environment.matches(Regex("[a-f0-9]{12}"))){
            return Result.failure(workDataOf("reason" to "invalid_input"))
        }
        val graph=(applicationContext as AtharApplication).graph
        if(environment!=graph.partition)return Result.failure(workDataOf("reason" to "stale_environment"))
        return try {
            if(graph.session.state.value is AuthState.Restoring)graph.session.restore()
            graph.session.requireVerifiedOwner(owner)
            graph.pushDevices.sync(owner)
            Result.success()
        } catch (failure:CancellationException) {
            throw failure
        } catch (failure:ApiException) {
            if(PushDeviceSyncPolicy.shouldRetry(failure.status,runAttemptCount))Result.retry()
            else Result.failure(workDataOf("reason" to failure.code))
        } catch (_:IOException) {
            if(runAttemptCount<5)Result.retry() else Result.failure(workDataOf("reason" to "network_unavailable"))
        } catch (_:Exception) {
            if(runAttemptCount<3)Result.retry() else Result.failure(workDataOf("reason" to "sync_failed"))
        }
    }

    companion object {
        const val ACTOR="actor_id"
        const val ENVIRONMENT="environment"
    }
}
