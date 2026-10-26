package org.athar.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.athar.app.core.sync.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Split-stage OS acceptance. Neither the retained-state nor verification stage invokes sync/doWork. */
class OsSyncLifecycleTest {
    private val args get()=InstrumentationRegistry.getArguments()
    private val app get()=ApplicationProvider.getApplicationContext<AtharApplication>()
    private val graph get()=app.graph
    private val receipt get()=File(app.filesDir,"os-lifecycle-fixture.json")
    private fun guard(){
        Assume.assumeTrue(args.getString("osLifecycle")=="true")
        require(args.getString("nativeApiSmoke")=="true")
        require(BuildConfig.API_BASE_URL=="http://127.0.0.1:8004/api/v1/")
        require(graph.apiBaseUrl==BuildConfig.API_BASE_URL)
        graph.reading.close();graph.counters.close();graph.lessons.close()
    }
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun read()=JsonParser.parseString(receipt.readText()).asJsonObject.also{require(it["fixture_only"].asBoolean&&it["partition"].asString==graph.partition)}
    private suspend fun hashes(owner:Long,id:String)=JsonObject().apply{
        val dao=graph.database.dao()
        for((label,key) in listOf("reading" to "quran-reading-position","counter" to "adhkar:1","lesson" to "lesson-progress:2"))addProperty(label,digest(requireNotNull(dao.cache(owner,key)).payloadCipher))
        addProperty("night",digest(requireNotNull(dao.night(owner,id)).payloadCipher))
        addProperty("exemptions",digest(requireNotNull(dao.exemptions(owner)).encryptedPayload))
        addProperty("outbox",digest(Gson().toJson(dao.sessionMutations(owner,id))))
    }
    private fun networkConnected():Boolean {
        val manager=app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return manager.activeNetwork?.let{manager.getNetworkCapabilities(it)}?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }

    @Test fun prepare()=runBlocking {
        guard()
        val email=requireNotNull(args.getString("lossFixtureEmail"));require(email.matches(Regex("native\\.loss\\.[0-9a-f]{12}@example\\.test")))
        val stage=requireNotNull(args.getString("lifecycleStage"));require(stage in setOf("process","reconnect","reboot"))
        val seconds=args.getString("lifecycleDelaySeconds")?.toLong()?:90L;require(seconds in 30..300)
        graph.session.restore();graph.session.login(email,"NativeFixture123!")
        val owner=requireNotNull(graph.session.userId);val id=UUID.randomUUID().toString();val dao=graph.database.dao()
        assertTrue(dao.allNights(owner).isEmpty());assertNull(dao.cache(owner,"quran-reading-position"))
        graph.reading.save(1,2,owner)
        graph.counters.tap(1,3,expectedOwner=owner)
        graph.lessons.save(2,30,100,expectedOwner=owner)
        graph.nightPrayer.save(JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":12,"note":"OS lifecycle fixture"}""").asJsonObject,id,owner)
        graph.nightPrayer.localExemptions(setOf(LocalDate.of(2026,10,23)),owner)
        val request=OneTimeWorkRequestBuilder<PrivateSyncWorker>()
            .setInputData(workDataOf(PrivateSyncWorker.ACTOR to owner,PrivateSyncWorker.ENVIRONMENT to graph.partition))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setInitialDelay(seconds,TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        val name=PrivateSyncSchedule(app,graph.partition).name(owner)
        val manager=WorkManager.getInstance(app)
        manager.enqueueUniqueWork(name,ExistingWorkPolicy.KEEP,request).result.get(10,TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED,requireNotNull(manager.getWorkInfoById(request.id).get(10,TimeUnit.SECONDS)).state)
        receipt.writeText(JsonObject().apply{
            addProperty("fixture_only",true);addProperty("email",email);addProperty("owner",owner);addProperty("night_id",id)
            addProperty("partition",graph.partition);addProperty("work_id",request.id.toString());addProperty("work_name",name)
            addProperty("stage",stage);addProperty("setup_pid",android.os.Process.myPid());addProperty("setup_time",System.currentTimeMillis());addProperty("delay_seconds",seconds)
            add("hashes",hashes(owner,id))
        }.toString())
        assertTrue(graph.reading.hasPending(owner));assertTrue(graph.counters.hasPending(owner));assertTrue(graph.lessons.hasPending(owner));assertTrue(graph.nightPrayer.hasPending(owner))
        // Keep only this generated actor signed in for the actual ordinary background startup.
    }

    @Test fun retainedWithoutNetwork()=runBlocking {
        guard();val saved=read();assertEquals("reconnect",saved["stage"].asString)
        assertFalse("No qualifying network may be present during this gate",networkConnected())
        graph.session.restore();val owner=saved["owner"].asLong;assertEquals(owner,graph.session.userId)
        assertTrue(System.currentTimeMillis()>=saved["setup_time"].asLong+saved["delay_seconds"].asLong*1000)
        val work=requireNotNull(WorkManager.getInstance(app).getWorkInfoById(UUID.fromString(saved["work_id"].asString)).get(10,TimeUnit.SECONDS))
        assertEquals(WorkInfo.State.ENQUEUED,work.state);assertEquals(0,work.runAttemptCount)
        assertEquals(saved["hashes"],hashes(owner,saved["night_id"].asString))
        assertEquals(1,graph.database.dao().sessionMutations(owner,saved["night_id"].asString).size)
    }

    @Test fun verifyDelivered()=runBlocking {
        guard();val saved=read();val owner=saved["owner"].asLong;val id=saved["night_id"].asString
        val work=requireNotNull(WorkManager.getInstance(app).getWorkInfoById(UUID.fromString(saved["work_id"].asString)).get(10,TimeUnit.SECONDS))
        assertEquals("The exact persisted request must finish before verification begins",WorkInfo.State.SUCCEEDED,work.state)
        graph.session.restore();assertEquals(owner,graph.session.userId)
        assertNotEquals(saved["setup_pid"].asInt,android.os.Process.myPid())
        assertFalse(graph.reading.hasPending(owner));assertFalse(graph.counters.hasPending(owner));assertFalse(graph.lessons.hasPending(owner));assertFalse(graph.nightPrayer.hasPending(owner))
        assertEquals(2,graph.reading.saved(owner)!!.ayah)
        assertEquals(30,graph.lessons.saved(owner,2)!!.positionSeconds);assertFalse(graph.lessons.saved(owner,2)!!.complete)
        val night=requireNotNull(graph.database.dao().night(owner,id));assertEquals(12,night.verseCount);assertEquals(1,night.serverRevision);assertFalse(night.dirty)
        assertTrue(graph.database.dao().sessionMutations(owner,id).isEmpty())
        assertEquals(saved["hashes"].asJsonObject["exemptions"].asString,digest(requireNotNull(graph.database.dao().exemptions(owner)).encryptedPayload))
        assertEquals(setOf(LocalDate.of(2026,10,23)),graph.nightPrayer.localExemptions(owner))
        File(app.filesDir,"os-lifecycle-result.json").writeText(JsonObject().apply{
            addProperty("fixture_only",true);addProperty("stage",saved["stage"].asString);addProperty("work_id",work.id.toString())
            addProperty("state",work.state.name);addProperty("attempt",work.runAttemptCount);addProperty("verification_pid",android.os.Process.myPid())
        }.toString())
        PrivateSyncSchedule(app,graph.partition).cancel(owner)
        graph.session.logout()
    }
}
