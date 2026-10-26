package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.athar.app.nightprayer.*
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/** In-memory Room only; no real session, key reset, server or private user data. */
class NightConflictStorageTest {
    private class Harness:AutoCloseable {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        val dao=db.dao();val cipher=PrivateCipher();val accounts=mutableMapOf<Pair<Long,String>,JsonObject>()
        var reject:Int?=null
        val writes=mutableListOf<JsonObject>()
        val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
        val transport=object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                if(path=="auth/login"){
                    val actor=if(body["email"]=="other@example.test")2 else 1
                    return JsonParser.parseString("""{"user":{"id":$actor,"email":"fixture@example.test","email_verified":true},"session":{"id":"fixture","access_token":"actor-$actor","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                }
                val owner=bearer!!.substringAfter("actor-").toLong()
                if(method=="GET")return accounts[owner to path.substringAfterLast('/')]?.deepCopy()?:throw ApiException(404,"not_found","")
                assertEquals("night-prayer/sync",path)
                val operations=Gson().toJsonTree(body["operations"]).asJsonArray;val results=JsonArray()
                operations.forEach{
                    val op=it.asJsonObject;val id=op["id"].asString;val data=op["data"].asJsonObject
                    writes+=op.deepCopy();val old=accounts[owner to id]
                    val status=reject?:if(data["expected_revision"].asInt!=(old?.get("revision")?.asInt?:0)||(old?.get("deleted")?.asBoolean==true&&op["operation"].asString=="put"))409 else null
                    val result=JsonObject().apply{addProperty("id",id)}
                    if(status!=null){result.addProperty("status","rejected");result.add("error",JsonObject().apply{addProperty("status",status)})}
                    else{
                        val revision=(old?.get("revision")?.asInt?:0)+1
                        val saved=if(op["operation"].asString=="delete")JsonObject()else data.deepCopy().apply{remove("mutation_id");remove("expected_revision")}
                        saved.addProperty("id",id);saved.addProperty("revision",revision);saved.addProperty("deleted",op["operation"].asString=="delete")
                        accounts[owner to id]=saved
                        result.addProperty("status","applied");result.add("result",JsonObject().apply{addProperty("revision",revision)})
                    }
                    results.add(result)
                }
                return JsonObject().apply{add("results",results);add("changes",JsonArray());addProperty("next_cursor",1);addProperty("has_more",false)}
            }
        }
        val session=SessionRepository(transport,store)
        val repo=NightPrayerRepository(db,session,cipher)
        suspend fun login(){session.login("reader@example.test","Fixture123!")}
        fun body(count:Int=12)=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":$count,"note":"Private fixture note"}""").asJsonObject
        fun account(id:String,count:Int=10,revision:Int=1,deleted:Boolean=false){accounts[1L to id]=(if(deleted)JsonObject()else body(count)).apply{addProperty("id",id);addProperty("revision",revision);addProperty("deleted",deleted)}}
        suspend fun conflict(id:String=UUID.randomUUID().toString()):String{account(id);repo.save(body(),id);repo.sync();assertTrue(dao.hasNightConflict(1,id));return id}
        override fun close(){db.close()}
    }

    @Test fun conflictFlagsSurviveRepositoryRestartAndDoNotStarveLaterHealthyWork()=runBlocking {
        Harness().use{h->h.login()
            val blocked=(1..50).map{UUID.randomUUID().toString()}
            for(id in blocked)h.repo.save(h.body(),id)
            h.dao.pending(1).forEach{h.dao.markConflict(1,it.mutationId)}
            val healthy=UUID.randomUUID().toString();h.repo.save(h.body(20),healthy)
            assertTrue(h.repo.hasPending(1));h.repo.sync()
            assertEquals(listOf(healthy),h.writes.map{it["id"].asString})
            assertFalse(h.repo.hasPending(1));assertEquals(50,h.repo.observeConflicts(1).first().size)
            assertEquals(50,NightPrayerRepository(h.db,h.session,h.cipher).observeConflicts(1).first().size)
            assertEquals(20,h.accounts[1L to healthy]!!["verse_count"].asInt)
        }
    }
    @Test fun changedReviewCannotOverwriteAndExplicitDeviceChoiceRebasesWithANewMutation()=runBlocking {
        Harness().use{h->h.login();val id=h.conflict();val review=h.repo.reviewConflict(id)
            val retained=h.dao.night(1,id);val queued=h.dao.sessionMutations(1,id)
            assertTrue(runCatching{h.repo.save(h.body(33),id)}.isFailure)
            assertTrue(runCatching{h.repo.delete(id)}.isFailure)
            assertEquals(retained,h.dao.night(1,id));assertEquals(queued,h.dao.sessionMutations(1,id))
            h.account(id,14,2)
            assertTrue(runCatching{h.repo.resolveConflict(review,NightConflictChoice.DEVICE)}.exceptionOrNull() is NightConflictChanged)
            assertEquals(retained,h.dao.night(1,id));assertEquals(queued,h.dao.sessionMutations(1,id))
            h.repo.resolveConflict(h.repo.reviewConflict(id),NightConflictChoice.DEVICE)
            val next=h.dao.sessionMutations(1,id).single()
            assertEquals(2,next.expectedRevision);assertNotEquals(queued.single().mutationId,next.mutationId)
            assertFalse(next.conflict);h.repo.sync()
            assertEquals(12,h.accounts[1L to id]!!["verse_count"].asInt);assertEquals(3,h.accounts[1L to id]!!["revision"].asInt)
            assertFalse(h.dao.night(1,id)!!.dirty)
        }
    }
    @Test fun accountChoiceTouchesOnlyTheReviewedSession()=runBlocking {
        Harness().use{h->h.login();val id=h.conflict();val other=UUID.randomUUID().toString();h.repo.save(h.body(30),other)
            val retained=h.dao.night(1,other);val pending=h.dao.sessionMutations(1,other)
            h.repo.resolveConflict(h.repo.reviewConflict(id),NightConflictChoice.ACCOUNT)
            assertEquals(10,h.dao.night(1,id)!!.verseCount);assertFalse(h.dao.night(1,id)!!.dirty)
            assertTrue(h.dao.sessionMutations(1,id).isEmpty())
            assertEquals(retained,h.dao.night(1,other));assertEquals(pending,h.dao.sessionMutations(1,other))
        }
    }
    @Test fun deletedAccountSessionCanOnlyBePreservedAsANewCopy()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.account(id,revision=2,deleted=true)
            h.repo.save(h.body(),id);h.repo.sync();val newId=h.repo.resolveConflict(h.repo.reviewConflict(id),NightConflictChoice.DEVICE)
            assertNotEquals(id,newId);assertTrue(h.dao.night(1,id)!!.deleted)
            assertFalse(h.cipher.open(h.dao.night(1,id)!!.payloadCipher).contains("Private fixture note"))
            assertEquals(0,h.dao.sessionMutations(1,newId).single().expectedRevision);h.repo.sync()
            assertTrue(h.accounts[1L to id]!!["deleted"].asBoolean);assertEquals(12,h.accounts[1L to newId]!!["verse_count"].asInt)
        }
    }
    @Test fun pendingDeletionRemainsReviewableAndMatchingTombstonesDoNotResurrect()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);h.repo.sync();h.repo.delete(id)
            h.account(id,revision=2,deleted=true);h.repo.sync()
            assertTrue(h.repo.observeConflicts(1).first().single().local.deleted)
            h.repo.resolveConflict(h.repo.reviewConflict(id),NightConflictChoice.DEVICE)
            assertTrue(h.dao.night(1,id)!!.deleted);assertFalse(h.dao.night(1,id)!!.dirty)
            assertTrue(h.dao.sessionMutations(1,id).isEmpty())
        }
    }
    @Test fun unreadableOutboxAndAccountSwitchRetainTheOriginalPendingBytes()=runBlocking {
        Harness().use{h->h.login();val id=h.conflict();val before=h.dao.sessionMutations(1,id).single()
            h.db.openHelper.writableDatabase.execSQL("UPDATE outbox SET payloadCipher=? WHERE userId=? AND mutationId=?",arrayOf("damaged fixture",1,before.mutationId))
            val damaged=h.dao.sessionMutations(1,id)
            assertTrue(runCatching{h.repo.reviewConflict(id)}.exceptionOrNull() is NightRetainedUnreadable)
            assertEquals(damaged,h.dao.sessionMutations(1,id))
            h.db.openHelper.writableDatabase.execSQL("UPDATE outbox SET payloadCipher=? WHERE userId=? AND mutationId=?",arrayOf(before.payloadCipher,1,before.mutationId))
            val review=h.repo.reviewConflict(id);h.session.login("other@example.test","Fixture123!")
            assertTrue(runCatching{h.repo.resolveConflict(review,NightConflictChoice.ACCOUNT)}.isFailure)
            assertTrue(runCatching{h.repo.save(h.body(),id,expectedOwner=1)}.isFailure)
            assertTrue(runCatching{h.repo.delete(id,expectedOwner=1)}.isFailure)
            assertTrue(runCatching{h.repo.reviewConflict(id,expectedOwner=1)}.isFailure)
            assertEquals(listOf(before),h.dao.sessionMutations(1,id));assertNull(h.dao.night(2,id))
        }
    }
    @Test fun temporaryServerRejectionRemainsRetryableInsteadOfBecomingAConflict()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);h.reject=503
            val before=h.dao.sessionMutations(1,id)
            assertEquals(503,(runCatching{h.repo.sync()}.exceptionOrNull() as ApiException).status)
            assertEquals(before,h.dao.sessionMutations(1,id));assertFalse(h.dao.hasNightConflict(1,id));assertTrue(h.repo.hasPending(1))
            h.reject=null;h.repo.sync();assertFalse(h.repo.hasPending(1));assertEquals(12,h.accounts[1L to id]!!["verse_count"].asInt)
        }
    }
}
