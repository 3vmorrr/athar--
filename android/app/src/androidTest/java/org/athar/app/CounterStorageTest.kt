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
        var count=2;var revision=7;var offline=false;var loseReply=false;var accepted=0;var reads=0
        val events=mutableSetOf<String>()
        override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
            if(path=="auth/login") {
                val user=if(body["email"]=="other@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$user,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$user","access_token":"a$user","refresh_token":"r$user","expires_in":900,"scope":"full"}}""")
            }
            if(offline)throw IOException("Offline fixture")
            if(path=="me/activity") {assertEquals("a1",bearer);events+=body["event_id"].toString();return JsonObject()}
            assertEquals("me/adhkar-progress",path)
            if(method=="GET") {
                assertEquals("1",query["item_id"]);reads++
                return if(bearer=="a2") JsonArray() else JsonParser.parseString("""[{"item_key":"curated:1","current_count":$count,"repeat_count":3,"revision":$revision}]""")
            }
            assertEquals("PUT",method);assertEquals("a1",bearer)
            if(body["expected_revision"]!=revision)throw ApiException(409,"conflict","")
            count=body["current_count"] as Int;revision++;accepted++
            if(loseReply){loseReply=false;throw IOException("Committed response lost")}
            return JsonObject().apply {addProperty("revision",revision)}
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
}
