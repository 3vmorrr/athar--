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
class NightRejectionStorageTest {
    private class Harness(val db:AppDatabase=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()):AutoCloseable {
        val dao=db.dao();val cipher=PrivateCipher();val accounts=mutableMapOf<Pair<Long,String>,JsonObject>()
        var reject:Int?=null
        val rejections=mutableMapOf<String,Int>();var malformed=false
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
                    val status=rejections[id]?:reject?:if(data["expected_revision"].asInt!=(old?.get("revision")?.asInt?:0)||(old?.get("deleted")?.asBoolean==true&&op["operation"].asString=="put"))409 else null
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
                return JsonObject().apply{add("results",if(malformed)JsonArray()else results);add("changes",JsonArray());addProperty("next_cursor",1);addProperty("has_more",false)}
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

    @Test fun rejectedQueueSurvivesActualDatabaseCloseAndReopen()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>();val name="rejection-fixture-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        val id=UUID.randomUUID().toString()
        try{
            val original=Harness(open()).use{h->h.login();h.repo.save(h.body(),id);h.rejections[id]=422;h.repo.sync();h.dao.sessionMutations(1,id)}
            Harness(open()).use{h->h.login();assertEquals(original,h.dao.sessionMutations(1,id));assertEquals(422,h.repo.reviewRejection(id).status);assertFalse(h.repo.hasPending(1))}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun permanentRejectionDoesNotRollBackHealthyAcknowledgementsOrBlockLaterWork()=runBlocking {
        Harness().use{h->h.login();val blocked=UUID.randomUUID().toString();val healthy=UUID.randomUUID().toString()
            h.repo.save(h.body(),blocked);h.repo.save(h.body(14),blocked);h.repo.save(h.body(20),healthy)
            val bytes=h.dao.sessionMutations(1,blocked);h.rejections[blocked]=422;h.repo.sync()
            val retained=h.dao.sessionMutations(1,blocked)
            assertEquals(bytes.map{it.payloadCipher},retained.map{it.payloadCipher});assertEquals(422,retained.first().rejectedStatus)
            assertFalse(h.dao.hasNightConflict(1,blocked));assertFalse(h.dao.night(1,healthy)!!.dirty)
            assertEquals(1,h.writes.count{it["id"].asString==blocked});assertFalse(h.repo.hasPending(1))
            val restarted=NightPrayerRepository(h.db,h.session,h.cipher)
            assertEquals(422,restarted.observeRejections(1).first().single().status)
            val next=UUID.randomUUID().toString();restarted.save(h.body(30),next);restarted.sync()
            assertFalse(h.dao.night(1,next)!!.dirty);assertEquals(retained,h.dao.sessionMutations(1,blocked))
        }
    }
    @Test fun explicitRetryKeepsCiphertextAndMutationIdsAndRepairRequiresCurrentValidatedReview()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);h.rejections[id]=422;h.repo.sync()
            val review=h.repo.reviewRejection(id);val bytes=h.dao.sessionMutations(1,id)
            assertTrue(runCatching{h.repo.save(h.body(),id)}.isFailure);assertTrue(runCatching{h.repo.delete(id)}.isFailure)
            assertTrue(runCatching{h.repo.recoverRejection(review,h.body(0))}.isFailure);assertEquals(bytes,h.dao.sessionMutations(1,id))
            h.repo.recoverRejection(review)
            assertEquals(bytes.map{it.copy(rejectedStatus=null)},h.dao.sessionMutations(1,id))
            assertTrue(runCatching{h.repo.recoverRejection(review,h.body(25))}.exceptionOrNull() is NightConflictChanged)
            h.repo.sync();val fresh=h.repo.reviewRejection(id);h.repo.recoverRejection(fresh,h.body(25))
            val repaired=h.dao.sessionMutations(1,id).single();assertNotEquals(bytes.single().mutationId,repaired.mutationId)
            assertEquals(0,repaired.expectedRevision);h.rejections.clear();h.repo.sync();assertEquals(25,h.accounts[1L to id]!!["verse_count"].asInt)
        }
    }
    @Test fun mixedTemporaryRejectionsCommitHealthyResultsAndPreserveRetryableBytes()=runBlocking {
        for(status in listOf(429,503,408,401))Harness().use{h->h.login();val id=UUID.randomUUID().toString();val healthy=UUID.randomUUID().toString()
            h.repo.save(h.body(),id);h.repo.save(h.body(20),healthy);val bytes=h.dao.sessionMutations(1,id);h.rejections[id]=status
            assertEquals(status,(runCatching{h.repo.sync()}.exceptionOrNull() as ApiException).status)
            assertEquals(bytes,h.dao.sessionMutations(1,id));assertFalse(h.dao.night(1,healthy)!!.dirty)
            assertTrue(h.repo.observeRejections(1).first().isEmpty());assertTrue(h.repo.hasPending(1))
            h.rejections.clear();h.repo.sync();assertFalse(h.repo.hasPending(1))
        }
    }
    @Test fun rejectedDeletionIsReviewableAndUnreadableOrFormerOwnerCannotReplaceBytes()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);h.repo.sync();h.repo.delete(id)
            h.rejections[id]=404;h.repo.sync();val review=h.repo.reviewRejection(id);assertTrue(review.local.deleted);assertFalse(canRepairNight(review))
            val bytes=h.dao.sessionMutations(1,id)
            h.db.openHelper.writableDatabase.execSQL("UPDATE outbox SET payloadCipher=? WHERE userId=? AND sessionId=?",arrayOf("damaged fixture",1,id))
            val damaged=h.dao.sessionMutations(1,id)
            assertTrue(runCatching{h.repo.reviewRejection(id)}.exceptionOrNull() is NightRetainedUnreadable)
            assertTrue(runCatching{h.repo.recoverRejection(review)}.isFailure);assertEquals(damaged,h.dao.sessionMutations(1,id))
            h.db.openHelper.writableDatabase.execSQL("UPDATE outbox SET payloadCipher=? WHERE userId=? AND sessionId=?",arrayOf(bytes.single().payloadCipher,1,id))
            h.session.login("other@example.test","Fixture123!")
            assertTrue(runCatching{h.repo.recoverRejection(review)}.isFailure);assertTrue(runCatching{h.repo.reviewRejection(id,1)}.isFailure)
            assertEquals(bytes,h.dao.sessionMutations(1,id));assertTrue(h.repo.observeRejections(2).first().isEmpty())
        }
    }
    @Test fun malformedBatchResponseCannotAcknowledgeOrAdvanceCursor()=runBlocking {
        Harness().use{h->h.login();val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);val bytes=h.dao.sessionMutations(1,id)
            h.malformed=true;assertTrue(runCatching{h.repo.sync()}.isFailure)
            assertEquals(bytes,h.dao.sessionMutations(1,id));assertNull(h.dao.cache(1,"night-sync-cursor"))
        }
    }
    @Test fun moreThanFiftyRejectedSessionsDoNotStarveHealthyQueue()=runBlocking {
        Harness().use{h->h.login()
            repeat(51){val id=UUID.randomUUID().toString();h.repo.save(h.body(),id);h.dao.sessionMutations(1,id).forEach{h.dao.markRejected(1,it.mutationId,422)}}
            val healthy=UUID.randomUUID().toString();h.repo.save(h.body(),healthy);h.repo.sync()
            assertEquals(listOf(healthy),h.writes.map{it["id"].asString});assertEquals(51,h.repo.observeRejections(1).first().size)
        }
    }
}
