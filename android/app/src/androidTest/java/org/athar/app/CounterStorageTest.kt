package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AppDatabase
import org.athar.app.core.data.CounterRepository
import org.athar.app.core.network.ApiException
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CounterStorageTest {
    private class Store:TokenStore {
        var value:StoredSession?=null
        override suspend fun load()=value
        override suspend fun save(session:StoredSession){value=session}
        override suspend fun clear(){value=null}
    }
    private class Server:ApiTransport {
        var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val eventDays=mutableMapOf<String,String>()
        var count=2;var revision=7;var offline=false;var loseReply=false;var accepted=0;var reads=0
        val events=mutableSetOf<String>()
        override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
            if(path=="auth/login") {
                val user=if(body["email"]=="other@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$user,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$user","access_token":"a$user","refresh_token":"r$user","expires_in":900,"scope":"full"}}""")
            }
            if(offline)throw IOException("Offline fixture")
            if(path=="me/activity") {assertEquals("a1",bearer);events+=body["event_id"].toString();eventDays[body["event_id"].toString()]=body["activity_date"].toString();return JsonObject()}
            assertEquals("me/adhkar-progress",path)
            if(method=="GET") {
                assertEquals("1",query["item_id"]);reads++
                return if(bearer=="a2") JsonArray() else JsonParser.parseString("""[{"item_key":"curated:1","current_count":$count,"repeat_count":3,"revision":$revision,"progress_date":"$day"}]""")
            }
            assertEquals("PUT",method);assertEquals("a1",bearer)
            if(body["expected_revision"]!=revision)throw ApiException(409,"conflict","")
            count=if(body["progress_date"].toString()<day)0 else body["current_count"] as Int;revision++;accepted++
            if(loseReply){loseReply=false;throw IOException("Committed response lost")}
            return JsonObject().apply {addProperty("revision",revision);addProperty("current_count",count);addProperty("repeat_count",3);addProperty("progress_date",day)}
        }
    }
    @Test fun coldDeviceUsesRemoteRevisionAndLostReplyDoesNotCreateFalseConflict()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val server=Server();val session=SessionRepository(server,Store());val cipher=PrivateCipher()
        session.login("reader@example.test","Fixture123!")
        var repository=CounterRepository(db.dao(),cipher,session,automaticSync=false)
        try {
            val reset=repository.tap(1,3,reset=true)
            assertEquals(7,reset.revision);assertEquals(0,reset.count);assertEquals(1,server.reads)
            repository.sync();assertEquals(8,server.revision);assertTrue(server.events.isEmpty())
            repository.tap(1,3);server.loseReply=true
            try{repository.sync();fail("Expected a lost reply")}catch(_:IOException){}
            val pending=repository.observe(1,1,3).first()
            assertTrue(pending.pending);assertEquals(8,pending.revision)
            val stored=requireNotNull(db.dao().cache(1,"adhkar:1"))
            assertFalse(stored.payloadCipher.contains("\"revision\":8"));assertTrue(cipher.open(stored.payloadCipher).contains("\"revision\":8"))
            repository.close();repository=CounterRepository(db.dao(),cipher,session,automaticSync=false)
            repository.sync()
            val recovered=repository.observe(1,1,3).first()
            assertFalse(recovered.pending);assertFalse(recovered.conflict);assertEquals(9,recovered.revision);assertEquals(2,server.accepted)
            server.count=2;server.revision=10;repository.refresh(1,3)
            assertEquals(2,repository.observe(1,1,3).first().count)
            repository.tap(1,3);server.count=1;server.revision=11;repository.sync()
            val conflicted=repository.observe(1,1,3).first()
            assertTrue(conflicted.conflict);assertEquals(3,conflicted.count);assertEquals(1,server.events.size)
            repository.useServer(1,3);repository.sync()
            assertEquals(1,repository.observe(1,1,3).first().count);assertEquals(2,server.accepted);assertEquals(1,server.events.size)
            session.login("other@example.test","Fixture123!");repository.refresh(1,3)
            assertEquals(0,repository.observe(2,1,3).first().count);assertEquals(1,repository.observe(1,1,3).first().count)
        }finally{repository.close();db.close()}
    }
    @Test fun offlineFirstTapAndRefreshPreservePendingIntentUntilExplicitConflictChoice()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        val server=Server();val session=SessionRepository(server,Store());session.login("reader@example.test","Fixture123!")
        val repository=CounterRepository(db.dao(),PrivateCipher(),session,automaticSync=false)
        try {
            server.offline=true;repository.tap(1,3)
            server.offline=false;repository.refresh(1,3)
            assertEquals(1,repository.observe(1,1,3).first().count);assertTrue(repository.observe(1,1,3).first().pending)
            repository.sync();assertTrue(repository.observe(1,1,3).first().conflict)
            try{repository.tap(1,3);fail("Conflict must block another mutation")}catch(_:IllegalStateException){}
            repository.useServer(1,3)
            assertEquals(2,repository.observe(1,1,3).first().count);assertFalse(repository.observe(1,1,3).first().conflict)
            assertTrue(server.events.isEmpty());assertEquals(0,server.accepted)
        }finally{repository.close();db.close()}
    }
    @Test fun priorDayPendingHistoryKeepsItsDateUntilAcknowledgedBeforeRollover()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        val server=Server().apply { day="2026-10-28" };val session=SessionRepository(server,Store());session.login("reader@example.test","Fixture123!")
        val cipher=PrivateCipher();val now=java.time.Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val yesterday=now-86_400_000;val event=java.util.UUID.randomUUID().toString()
        // A real encrypted legacy record, without a day field, derives its original day from its row timestamp.
        val legacy="""{"item":1,"target":3,"count":2,"revision":7,"localVersion":1,"pending":true,"conflict":false,"events":["$event"]}"""
        db.dao().cache(org.athar.app.core.data.CachedDocument(1,"adhkar:1",cipher.seal(legacy),yesterday))
        val repository=CounterRepository(db.dao(),cipher,session,automaticSync=false,clock={now})
        try {
            assertEquals("2026-10-27",repository.observe(1,1,3).first().day)
            try { repository.tap(1,3);fail("Old pending day must block a new tap") } catch(_:IllegalStateException) {}
            repository.sync()
            val ready=repository.observe(1,1,3).first()
            assertEquals("2026-10-27",server.eventDays[event]);assertEquals("2026-10-28",ready.day)
            assertEquals(0,ready.count);assertFalse(ready.pending);assertTrue(ready.events.isEmpty())
        } finally { repository.close();db.close() }
    }

    @Test fun explicitServerChoiceCannotDiscardUnsentPriorDayCompletion()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        val server=Server().apply { day="2026-10-28" };val session=SessionRepository(server,Store());session.login("reader@example.test","Fixture123!")
        val cipher=PrivateCipher();val now=java.time.Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val event=java.util.UUID.randomUUID().toString()
        val json="""{"item":1,"target":3,"count":3,"revision":6,"localVersion":1,"pending":true,"conflict":true,"day":"2026-10-27","events":["$event"]}"""
        val encrypted=cipher.seal(json)
        db.dao().cache(org.athar.app.core.data.CachedDocument(1,"adhkar:1",encrypted,now))
        val repository=CounterRepository(db.dao(),cipher,session,automaticSync=false,clock={now})
        try {
            assertTrue(repository.hasPending(1));assertEquals(1,repository.pendingCompletionCount(1,1))
            server.offline=true
            try { repository.useServer(1,3);fail("History delivery failure must preserve conflict") } catch(_:IOException) {}
            assertEquals(encrypted,db.dao().cache(1,"adhkar:1")?.payloadCipher)
            server.offline=false;repository.useServer(1,3)
            val ready=repository.observe(1,1,3).first()
            assertEquals("2026-10-27",server.eventDays[event]);assertTrue(ready.events.isEmpty())
            assertFalse(ready.conflict);assertEquals(2,ready.count);assertEquals("2026-10-28",ready.day)
        } finally { repository.close();db.close() }
    }

    @Test fun invalidExplicitDayAndClockRollbackNeverEraseEncryptedCounter()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        val server=Server();val session=SessionRepository(server,Store());session.login("reader@example.test","Fixture123!")
        val cipher=PrivateCipher();val now=java.time.Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()
        val repository=CounterRepository(db.dao(),cipher,session,automaticSync=false,clock={now})
        try {
            for(day in listOf("2026-02-30","2026-10-29")) {
                val json="""{"item":1,"target":3,"count":2,"revision":7,"localVersion":0,"pending":false,"conflict":false,"day":"$day","events":[]}"""
                val encrypted=cipher.seal(json);db.dao().cache(org.athar.app.core.data.CachedDocument(1,"adhkar:1",encrypted,now))
                if(day=="2026-02-30") assertTrue(runCatching { repository.observe(1,1,3).first() }.isFailure)
                else {
                    assertEquals(2,repository.observe(1,1,3).first().count)
                    try { repository.tap(1,3);fail("Clock rollback must block a new tap") } catch(_:IllegalStateException) {}
                }
                assertEquals(encrypted,db.dao().cache(1,"adhkar:1")?.payloadCipher)
            }
        } finally { repository.close();db.close() }
    }

}
