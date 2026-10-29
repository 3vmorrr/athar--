package org.athar.app.core.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.AtharApplication
import org.athar.app.auth.SessionRepository
import org.athar.app.books.*
import org.athar.app.core.network.ApiException
import org.athar.app.core.sync.PrivateSyncSchedule
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class DeletionUiStatus(val busy: Boolean = false, val error: String? = null)

internal class AccountDeletionCoordinator(
    context: Context, private val namespace: String, private val database: AppDatabase,
    private val session: SessionRepository, private val store: AccountDeletionStore,
    private val persistentWork: Boolean = true,
) {
    private val context = context.applicationContext
    private val initialization = Mutex()
    private var initialized = false
    private val mutable = MutableStateFlow<Map<Long, DeletionUiStatus>>(emptyMap())
    val status = mutable.asStateFlow()
    private fun state(owner: Long, value: DeletionUiStatus) { mutable.update { it + (owner to value) } }
    suspend fun initialize() = withContext(Dispatchers.IO) { initialization.withLock {
        if (!initialized) {
            for (owner in legacyRevokedBookOwners(context, namespace)) database.withTransaction {
                if (database.dao().deletion(owner) == null) database.dao().deletion(LocalAccountDeletion(owner, UUID.randomUUID().toString(), DeletionPhase.CLEANED.wire, null, System.currentTimeMillis()))
            }
            store.initialize(); initialized = true
        }
    } }
    private suspend fun cancelOwned(owner: Long) = withContext(Dispatchers.IO) {
        if (persistentWork) {
            val manager = WorkManager.getInstance(context)
            val schedule = PrivateSyncSchedule(context, namespace)
            listOf(manager.cancelUniqueWork(schedule.name(owner)), manager.cancelUniqueWork(schedule.name(owner, true)), manager.cancelAllWorkByTag(BookDownloads.tag(BookScope(namespace, owner))))
                .forEach { it.result.get() }
        }
    }
    private suspend fun purge(owner: Long) = withContext(Dispatchers.IO) {
        cancelOwned(owner)
        bookFiles(context, BookScope(namespace, owner)).revoke()
        database.dao().deleteUser(owner)
    }
    private fun workName(owner: Long) = "account-deletion:$namespace:$owner"
    private suspend fun schedule(owner: Long): Unit = withContext(Dispatchers.IO) {
        if (!persistentWork) return@withContext
        val work = OneTimeWorkRequestBuilder<AccountDeletionRecoveryWorker>()
            .setInputData(workDataOf("namespace" to namespace, "owner" to owner))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(owner), ExistingWorkPolicy.KEEP, work).result.get()
    }
    private val workflow = AccountDeletionWorkflow(object : AccountDeletionActions {
        override suspend fun current(owner: Long) = store.intent(owner)
        override suspend fun prepare(owner: Long): AccountDeletionIntent {
            val lease = session.deletionLease(owner)
            return store.prepare(owner) { session.requireDeletionLease(lease) }
        }
        override suspend fun transition(value: AccountDeletionIntent, next: DeletionPhase) = store.transition(value, next)
        override suspend fun pause(value: AccountDeletionIntent) {
            withContext(Dispatchers.IO) { bookFiles(context, BookScope(namespace, value.owner)).pauseDeletion(value.requestId) }
            cancelOwned(value.owner)
        }
        override suspend fun undoRejected(value: AccountDeletionIntent) {
            withContext(Dispatchers.IO) { bookFiles(context, BookScope(namespace, value.owner)).resumeDeletion(value.requestId) }
            store.removeRejected(value)
            if (persistentWork) WorkManager.getInstance(context).cancelUniqueWork(workName(value.owner))
        }
        override suspend fun deleteRemote(value: AccountDeletionIntent, password: String) = session.submitDeletion(value, password)
        override suspend fun proof(value: AccountDeletionIntent) = session.deletionProof(value)
        override suspend fun purgeOwned(value: AccountDeletionIntent) = purge(value.owner)
        override suspend fun forgetCredentials(owner: Long) = session.forgetDeletedOwner(owner)
        override suspend fun acknowledge(value: AccountDeletionIntent) = session.acknowledgeDeletion(value)
        override suspend fun forgetCapability(value: AccountDeletionIntent) = store.forgetCapability(value)
        override suspend fun schedule(owner: Long) = this@AccountDeletionCoordinator.schedule(owner)
    })
    private fun error(failure: Exception) = when (failure) {
        is AccountDeletionUnreadable -> "local_data_unreadable"
        is ApiException -> failure.code
        else -> "account_deletion_pending"
    }
    suspend fun delete(owner: Long, password: String) {
        initialize(); state(owner, DeletionUiStatus(busy = true))
        try { workflow.start(owner, password); state(owner, DeletionUiStatus()) }
        catch (cancelled: CancellationException) { state(owner, DeletionUiStatus(error = "account_deletion_pending")); throw cancelled }
        catch (failure: Exception) { state(owner, DeletionUiStatus(error = error(failure))); throw failure }
    }
    suspend fun recover(owner: Long): DeletionRecoveryResult {
        initialize(); state(owner, DeletionUiStatus(busy = true))
        try {
            val row = database.dao().deletion(owner)
            if (row != null && row.payloadCipher == null) {
                require(row.state == DeletionPhase.CLEANED.wire)
                purge(owner); session.forgetDeletedOwner(owner)
                state(owner, DeletionUiStatus()); return DeletionRecoveryResult.COMPLETE
            }
            val result = workflow.recover(owner)
            state(owner, DeletionUiStatus(error = when (result) {
                DeletionRecoveryResult.COMPLETE -> null
                DeletionRecoveryResult.RETRY -> "account_deletion_pending"
                DeletionRecoveryResult.REAUTH_REQUIRED -> "account_deletion_reauth"
            }))
            return result
        } catch (cancelled: CancellationException) { state(owner, DeletionUiStatus(error = "account_deletion_pending")); throw cancelled }
        catch (failure: Exception) { state(owner, DeletionUiStatus(error = error(failure))); throw failure }
    }
    suspend fun recoverAll() {
        initialize()
        for (row in database.dao().allDeletions()) {
            try {
                if (row.payloadCipher != null) schedule(row.userId)
                val outcome = recover(row.userId)
                if (outcome == DeletionRecoveryResult.RETRY) schedule(row.userId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { schedule(row.userId) }
        }
    }
}

class AccountDeletionRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as AtharApplication).graph
        val owner = inputData.getLong("owner", -1)
        if (owner <= 0 || inputData.getString("namespace") != graph.partition) return Result.failure()
        return try { when (graph.accountDeletion.recover(owner)) {
            DeletionRecoveryResult.COMPLETE, DeletionRecoveryResult.REAUTH_REQUIRED -> Result.success()
            DeletionRecoveryResult.RETRY -> Result.retry()
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AccountDeletionUnreadable) { Result.failure() }
        catch (_: Exception) { Result.retry() }
    }
}
