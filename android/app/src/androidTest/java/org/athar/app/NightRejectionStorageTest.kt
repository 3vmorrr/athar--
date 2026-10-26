package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AppDatabase
import org.athar.app.core.network.ApiException
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.PrivateCipher
import org.athar.app.core.security.StoredSession
import org.athar.app.core.security.TokenStore
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Durable rejection recovery uses only in-memory Room and fake accounts. */
class NightRejectionStorageTest {
    private class Harness:AutoCloseable {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        val dao=db.dao()
        val cipher=PrivateCipher()
        val accounts=mutableMapOf<Pair<Long,String>,JsonObject>()
        val rejects=mutableMapOf<String,Int>()
        val store=object:TokenStore{
            var value:StoredSession?=null
            override suspend fun load()=value
            override suspend fun save(session:StoredSession){value=session}
            override suspend fun clear(){value=null}
        }
        val transport=object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                if(path=="auth/login"){
                    val actor=if(body["email"]=="other@example.test")2 else 1
                    return JsonParser.parseString("""{"user":{"id":$actor,"email":"fixture@example.test","email_verified":true},"session":{"id":"fixture","access_token":"actor-$actor","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                }
                val owner=bearer!!.substringAfter("actor-").toLong()
                if(method=="GET")return accounts[owner to path.substringAfterLast('/')]?.deepCopy()?:throw ApiException(404,"not_found","")
                assertEquals("night-prayer/sync",path)
                val operations=Gson().toJsonTree(body["operations"]).asJsonArray
                val results=JsonArray()
                operations.forEach{
                    val op=it.asJsonObject
                    val id=op["id"].asString
                    val data=op["data"].asJsonObject
                    val old=accounts[owner to id]
                    val forced=rejects[id]
                    val conflict=data["expected_revision"].asInt!=(old?.get("revision")?.asInt?:0)||(old?.get("deleted")?.asBoolean==true&&op["operation"].asString=="put")
                    val status=forced?:if(conflict)409 else null
                    val result=JsonObject().apply{addProperty("id",id)}
                    if(status!=null){
                        result.addProperty("status","rejected")
                        result.add("error",JsonObject().apply{
                            addProperty("status",status)
                            addProperty("code",when(status){404->"not_found";422->"invalid_fields";429->"rate_limited";503->"service_unavailable";else->"conflict"})
                        })
                    }else{
                        val revision=(old?.get("revision")?.asInt?:0)+1
                        val saved=if(op["operation"].asString=="delete")JsonObject()else data.deepCopy().apply{remove("mutation_id");remove("expected_revision")}
                        saved.addProperty("id",id)
                        saved.addProperty("revision",revision)
                        saved.addProperty("deleted",op["operation"].asString=="delete")
                        accounts[owner to id]=saved
                        result.addProperty("status","applied")
                        result.add("result",JsonObject().apply{addProperty("revision",revision)})
                    }
                    results.add(result)
                }
                return JsonObject().apply{
                    add("results",results)
                    add("changes",JsonArray())
                    addProperty("next_cursor",1)
                    addProperty("has_more",false)
                }
            }
        }
        val session=SessionRepository(transport,store)
        val repo=NightPrayerRepository(db,session,cipher)
        suspend fun login(email:String="reader@example.test"){session.login(email,"Fixture123!")}
        fun body(count:Int=12)=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":$count,"note":"Private rejection fixture"}""").asJsonObject
        fun account(id:String,count:Int=10,revision:Int=1,deleted:Boolean=false){
            accounts[1L to id]=(if(deleted)JsonObject()else body(count)).apply{
                addProperty("id",id);addProperty("revision",revision);addProperty("deleted",deleted)
            }
        }
        override fun close(){db.close()}
    }

    @Test fun permanent422SurvivesRestartAndDoesNotStarveHealthySessions()=runBlocking {
        Harness().use{h->
            h.login()
            val rejected=UUID.randomUUID().toString()
            val healthy=UUID.randomUUID().toString()
            h.repo.save(h.body(),rejected)
            h.repo.save(h.body(20),healthy)
            val original=h.dao.sessionMutations(1,rejected)
            h.rejects[rejected]=422
            h.repo.sync()
            assertEquals(original,h.dao.sessionMutations(1,rejected))
            assertEquals(422,h.repo.observeRejections(1).first().single().status)
            assertEquals(20,h.accounts[1L to healthy]!!["verse_count"].asInt)
            assertFalse(h.repo.hasPending(1))
            val restarted=NightPrayerRepository(h.db,h.session,h.cipher)
            assertEquals(rejected,restarted.observeRejections(1).first().single().local.id)
        }
    }

    @Test fun temporary429And503RemainRetryableAndAreNeverStoredAsPermanentRejections()=runBlocking {
        for(status in listOf(429,503)){
            Harness().use{h->
                h.login()
                val id=UUID.randomUUID().toString()
                h.repo.save(h.body(),id)
                val original=h.dao.sessionMutations(1,id)
                h.rejects[id]=status
                val failure=runCatching{h.repo.sync()}.exceptionOrNull()
                assertTrue(failure is ApiException)
                assertEquals(status,(failure as ApiException).status)
                assertEquals(original,h.dao.sessionMutations(1,id))
                assertTrue(h.repo.observeRejections(1).first().isEmpty())
                assertTrue(h.repo.hasPending(1))
                h.rejects.remove(id)
                h.repo.sync()
                assertFalse(h.repo.hasPending(1))
            }
        }
    }

    @Test fun retryKeepsOriginalBytesAndRepairNeedsFreshReview()=runBlocking {
        Harness().use{h->
            h.login()
            val id=UUID.randomUUID().toString()
            h.repo.save(h.body(),id)
            h.rejects[id]=422
            h.repo.sync()
            val original=h.dao.sessionMutations(1,id).single()
            var review=h.repo.reviewRejection(id)
            h.rejects.remove(id)
            h.repo.retryRejection(review)
            assertEquals(original,h.dao.sessionMutations(1,id).single())
            h.repo.sync()
            assertTrue(h.dao.sessionMutations(1,id).isEmpty())

            h.repo.save(h.body(13),id)
            h.rejects[id]=422
            h.repo.sync()
            val rejectedBytes=h.dao.sessionMutations(1,id)
            review=h.repo.reviewRejection(id)
            h.account(id,17,2)
            assertTrue(runCatching{h.repo.repairRejection(review,h.body(18))}.exceptionOrNull() is NightRejectionChanged)
            assertEquals(rejectedBytes,h.dao.sessionMutations(1,id))

            review=h.repo.reviewRejection(id)
            h.repo.repairRejection(review,h.body(18))
            val repaired=h.dao.sessionMutations(1,id).single()
            assertNotEquals(rejectedBytes.single().mutationId,repaired.mutationId)
            assertEquals(2,repaired.expectedRevision)
            h.rejects.remove(id)
            h.repo.sync()
            assertEquals(18,h.accounts[1L to id]!!["verse_count"].asInt)
        }
    }

    @Test fun missingRemoteDeleteRequiresExplicitAccountChoiceAndUnreadableMetadataKeepsBytes()=runBlocking {
        Harness().use{h->
            h.login()
            val id=UUID.randomUUID().toString()
            h.repo.save(h.body(),id)
            h.repo.sync()
            h.repo.delete(id)
            h.accounts.remove(1L to id)
            h.rejects[id]=404
            h.repo.sync()
            val before=h.dao.sessionMutations(1,id)
            val review=h.repo.reviewRejection(id)
            assertNull(review.account)
            assertTrue(review.rejection.local.deleted)
            h.repo.acceptAccountRejection(review)
            assertTrue(h.dao.sessionMutations(1,id).isEmpty())
            assertFalse(h.dao.night(1,id)!!.dirty)

            val broken=UUID.randomUUID().toString()
            h.repo.save(h.body(),broken)
            h.rejects[broken]=422
            h.repo.sync()
            val blocked=h.dao.sessionMutations(1,broken)
            val rejection=h.repo.observeRejections(1).first().single{it.local.id==broken}
            h.db.openHelper.writableDatabase.execSQL(
                "UPDATE cached_documents SET payloadCipher=? WHERE userId=? AND cacheKey=?",
                arrayOf("damaged rejection metadata",1,"night-rejection:${rejection.mutation.mutationId}")
            )
            assertTrue(runCatching{h.repo.reviewRejection(broken)}.exceptionOrNull() is NightRetainedUnreadable)
            assertEquals(blocked,h.dao.sessionMutations(1,broken))
            assertEquals(before.size,1)
        }
    }

    @Test fun accountSwitchCannotActOnAnotherOwnersRejectedBytes()=runBlocking {
        Harness().use{h->
            h.login()
            val id=UUID.randomUUID().toString()
            h.repo.save(h.body(),id)
            h.rejects[id]=422
            h.repo.sync()
            val review=h.repo.reviewRejection(id)
            val retained=h.dao.sessionMutations(1,id)
            h.login("other@example.test")
            assertTrue(runCatching{h.repo.retryRejection(review)}.isFailure)
            assertTrue(runCatching{h.repo.repairRejection(review,h.body(16))}.isFailure)
            assertTrue(runCatching{h.repo.acceptAccountRejection(review)}.isFailure)
            assertEquals(retained,h.dao.sessionMutations(1,id))
            assertNull(h.dao.night(2,id))
        }
    }
}
