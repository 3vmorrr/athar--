package org.athar.app.core.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Uses existing WorkManager infrastructure; no alarm, broadcast API or always-running service. */
class PrivateSyncSchedule(context:Context,private val partition:String) {
    private val manager=WorkManager.getInstance(context.applicationContext)
    init{require(partition.matches(Regex("[a-f0-9]{12}")))}
    fun name(owner:Long,periodic:Boolean=false):String {require(owner>0);return "athar-private-sync-$partition-$owner"+if(periodic)"-maintenance" else ""}
    private fun data(owner:Long)=workDataOf(PrivateSyncWorker.ACTOR to owner,PrivateSyncWorker.ENVIRONMENT to partition)
    private fun constraints()=BackgroundWorkConstraints.forBudget(BackgroundWorkBudgets.PRIVATE_SYNC)
    fun enqueue(owner:Long):Operation=manager.enqueueUniqueWork(name(owner),ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<PrivateSyncWorker>().setInputData(data(owner)).setConstraints(constraints()).setInitialDelay(2,TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    fun maintain(owner:Long):Operation=manager.enqueueUniquePeriodicWork(name(owner,true),ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<PrivateSyncWorker>(
            BackgroundWorkBudgets.PRIVATE_SYNC.periodicHours!!,
            TimeUnit.HOURS,
            BackgroundWorkBudgets.PRIVATE_SYNC.flexHours!!,
            TimeUnit.HOURS,
        ).setInputData(data(owner)).setConstraints(constraints()).setInitialDelay(
            BackgroundWorkBudgets.PRIVATE_SYNC.periodicHours!!,
            TimeUnit.HOURS,
        ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    fun cancel(owner:Long){manager.cancelUniqueWork(name(owner));manager.cancelUniqueWork(name(owner,true))}
}
