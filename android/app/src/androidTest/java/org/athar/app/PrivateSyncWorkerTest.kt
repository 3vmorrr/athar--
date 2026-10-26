package org.athar.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.athar.app.core.data.*
import org.athar.app.core.sync.*
import org.junit.*
import org.junit.Assert.*

/** Actual Worker/Room/Keystore/HTTP integration, confined to the generated sync actor. */
class PrivateSyncWorkerTest {
    @Test fun workerRejectsWrongOwnershipAndFlushesOnlyItsOwnDurableCounter()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        // Stop only this test application's foreground helpers so the Worker proves its own dispatch.
        graph.counters.close();graph.reading.close();graph.lessons.close()
        graph.session.restore();graph.session.login("native.sync@example.test","NativeFixture123!")
        val owner=requireNotNull(graph.session.userId);val dao=graph.database.dao()
        val original=dao.cache(owner,"adhkar:1");val originalExemptions=dao.exemptions(owner)
        val before=graph.session.request("GET","me/adhkar-progress",query=mapOf("item_id" to "1")).asJsonArray.firstOrNull()?.asJsonObject
        val revision=before?.get("revision")?.asInt?:0
        val count=if(before?.get("current_count")?.asInt==1)2 else 1
        val dirty=CachedDocument(owner,"adhkar:1",graph.cipher.seal(Gson().toJson(CounterState(1,3,count,revision,localVersion=1,pending=true))),1)
        val localOnly=PrivateExemptions(owner,graph.cipher.seal("[\"2026-10-24\"]"))
        fun worker(actor:Long=owner,environment:String=graph.partition)=TestListenableWorkerBuilder<PrivateSyncWorker>(app).setInputData(workDataOf(PrivateSyncWorker.ACTOR to actor,PrivateSyncWorker.ENVIRONMENT to environment)).build()
        try {
            dao.cache(dirty);dao.exemptions(localOnly)
            assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "invalid_environment")),worker(environment="000000000000").doWork())
            assertEquals(dirty,dao.cache(owner,"adhkar:1"))
            assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "auth_required")),worker(actor=owner+1000).doWork())
            assertEquals(dirty,dao.cache(owner,"adhkar:1"))
            // Use the installed WorkManager scheduler, not a direct successful doWork invocation.
            val schedule=PrivateSyncSchedule(app,graph.partition)
            schedule.enqueue(owner).result.get(10,java.util.concurrent.TimeUnit.SECONDS)
            val manager=WorkManager.getInstance(app)
            val network=app.getSystemService(android.net.ConnectivityManager::class.java)
            val connected=network.activeNetwork?.let{network.getNetworkCapabilities(it)}?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
            if(InstrumentationRegistry.getArguments().getString("requireValidatedScheduler")=="true"){
                val validated=network.activeNetwork?.let{network.getNetworkCapabilities(it)}?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
                assertTrue("This gate requires actual connected, validated OS scheduling; direct Worker fallback is insufficient",connected&&validated)
            }
            val finished=if(connected)withTimeout(60_000){
                while(true){
                    val info=manager.getWorkInfosForUniqueWork(schedule.name(owner)).get(10,java.util.concurrent.TimeUnit.SECONDS).lastOrNull()
                    if(info?.state?.isFinished==true)return@withTimeout info
                    delay(250)
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }else null
            if(finished!=null){
                assertEquals(finished.outputData.toString(),WorkInfo.State.SUCCEEDED,finished.state)
                println("OS_CONNECTED_SCHEDULER_SUCCEEDED: installed WorkManager dispatched the owned fixture job")
            }
            else {
                delay(3000)
                val queued=manager.getWorkInfosForUniqueWork(schedule.name(owner)).get(10,java.util.concurrent.TimeUnit.SECONDS).last()
                assertEquals(WorkInfo.State.ENQUEUED,queued.state)
                assertEquals(dirty,dao.cache(owner,"adhkar:1"))
                schedule.cancel(owner)
                println("Network constraint retained queued work; USB HTTP worker dispatch tested separately, OS network-restoration gate remains open.")
                assertEquals(ListenableWorker.Result.success(),worker().doWork())
            }
            val acknowledged=Gson().fromJson(graph.cipher.open(requireNotNull(dao.cache(owner,"adhkar:1")).payloadCipher),CounterState::class.java)
            assertFalse(acknowledged.pending);assertEquals(count,acknowledged.count);assertTrue(acknowledged.revision>revision)
            val remote=graph.session.request("GET","me/adhkar-progress",query=mapOf("item_id" to "1")).asJsonArray.single().asJsonObject
            assertEquals(count,remote["current_count"].asInt)
            assertEquals(localOnly,dao.exemptions(owner))
            val unreadable=dirty.copy(payloadCipher="damaged worker fixture")
            dao.cache(unreadable)
            assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "action_required")),worker().doWork())
            assertEquals(unreadable,dao.cache(owner,"adhkar:1"));assertEquals(localOnly,dao.exemptions(owner))
        }finally{
            PrivateSyncSchedule(app,graph.partition).cancel(owner)
            if(original==null)dao.removeCache(owner,"adhkar:1")else dao.cache(original)
            if(originalExemptions==null)dao.deleteExemptions(owner)else dao.exemptions(originalExemptions)
            graph.session.logout()
        }
    }
}
