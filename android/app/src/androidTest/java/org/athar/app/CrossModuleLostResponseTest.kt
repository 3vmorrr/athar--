package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository
import org.athar.app.quran.*
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

/** Real HTTP writes commit before the test transport loses their acknowledgement. */
class CrossModuleLostResponseTest {
    @Test fun committedResponsesCanBeLostAcrossDiskReopenWithoutLostIntentOrDuplicateActivity()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("nativeApiSmoke")=="true")
        val email=requireNotNull(args.getString("lossFixtureEmail"))
        require(email.matches(Regex("native\\.loss\\.[0-9a-f]{12}@example\\.test")))
        val host=args.getString("nativeFixtureHost")?:"10.0.2.2";require(host in setOf("127.0.0.1","10.0.2.2"))
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val delegate=RetrofitTransport("http://$host:8004/api/v1/")
        var lose:String?=null;var afterCommit:(suspend()->Unit)?=null
        val sentNight=mutableListOf<JsonElement>()
        val transport=object:ApiTransport {
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
                val result=delegate.request(method,path,body,query,bearer,language)
                if(path=="night-prayer/sync")sentNight+=Gson().toJsonTree(body["operations"])
                if("$method $path"==lose){lose=null;val hook=afterCommit;afterCommit=null;hook?.invoke();throw IOException("Committed fixture response deliberately lost")}
                return result
            }
        }
        val store=object:TokenStore {
            var value:StoredSession?=null
            override suspend fun load()=value
            override suspend fun save(session:StoredSession){value=session}
            override suspend fun clear(){value=null}
        }
        val session=SessionRepository(transport,store);session.login(email,"NativeFixture123!")
        val owner=requireNotNull(session.userId);val cipher=PrivateCipher();val quran=QuranRepository(context)
        val name="loss-fixture-${UUID.randomUUID()}.db"
        fun database()=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
        var db=database()
        fun reading()=ReadingRepository(db.dao(),cipher,session,{quran.data().index},automaticSync=false)
        fun lessons()=LessonRepository(db.dao(),cipher,session,automaticSync=false)
        fun counters()=CounterRepository(db.dao(),cipher,session,automaticSync=false)
        fun nights()=NightPrayerRepository(db,session,cipher)
        var reading=reading();var lessons=lessons();var counters=counters();var nights=nights()
        fun reopen(){reading.close();lessons.close();counters.close();db.close();db=database();reading=reading();lessons=lessons();counters=counters();nights=nights()}
        suspend fun lost(block:suspend()->Unit){try{block();fail("Expected deliberate post-commit loss")}catch(failure:IOException){assertEquals("Committed fixture response deliberately lost",failure.message)}}
        suspend fun stats():Map<String,Int> {
            val rows=session.request("GET","me/export").asJsonObject["account_daily_stats"].asJsonArray
            return listOf("quran_sessions","lesson_completions","adhkar_completions").associateWith{key->rows.sumOf{it.asJsonObject[key].asInt}}
        }
        val nightId=UUID.randomUUID().toString()
        try{
            val baseline=stats()
            reading.refresh();reading.save(1,2);lose="PUT quran/position";lost{reading.sync()}
            val readingBytes=requireNotNull(db.dao().cache(owner,"quran-reading-position"))
            reopen();assertEquals(readingBytes,db.dao().cache(owner,"quran-reading-position"));assertTrue(reading.saved(owner)!!.pending)
            repeat(3){reading.sync()};assertFalse(reading.saved(owner)!!.pending);assertFalse(reading.saved(owner)!!.conflict)
            val revision=reading.saved(owner)!!.revision;assertEquals(1,revision)
            assertNull(reading.saved(owner+1))

            lessons.save(2,30,100);lose="PUT me/lesson-progress";lost{lessons.sync()}
            reopen();assertEquals(30,lessons.saved(owner,2)!!.positionSeconds);assertFalse(lessons.saved(owner,2)!!.complete)
            repeat(3){lessons.sync()};assertFalse(lessons.saved(owner,2)!!.pending);assertEquals(baseline["lesson_completions"],stats()["lesson_completions"])
            lessons.save(2,90,100,true);val event=lessons.saved(owner,2)!!.eventId
            lose="PUT me/lesson-progress";afterCommit={lessons.save(2,95,100)};lost{lessons.sync()}
            reopen();assertEquals(event,lessons.saved(owner,2)!!.eventId);assertEquals(95,lessons.saved(owner,2)!!.positionSeconds)
            assertTrue(lessons.saved(owner,2)!!.pending);repeat(3){lessons.sync()}
            assertTrue(lessons.saved(owner,2)!!.complete);assertFalse(lessons.saved(owner,2)!!.pending)
            assertEquals(95,session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).asJsonObject["position_seconds"].asInt)
            assertEquals(baseline.getValue("lesson_completions")+1,stats().getValue("lesson_completions"))

            counters.tap(1,3,reset=true);counters.sync();repeat(3){counters.tap(1,3)}
            val completion=counters.observe(owner,1,3).first().events.single()
            lose="POST me/activity";lost{counters.sync()};reopen()
            assertEquals(listOf(completion),counters.observe(owner,1,3).first().events)
            repeat(3){counters.sync()};val complete=counters.observe(owner,1,3).first()
            assertFalse(complete.pending);assertFalse(complete.conflict);assertTrue(complete.events.isEmpty());assertEquals(3,complete.count)
            assertEquals(baseline.getValue("adhkar_completions")+1,stats().getValue("adhkar_completions"))
            counters.tap(1,3,reset=true);counters.sync();counters.tap(1,3)
            lose="PUT me/adhkar-progress";lost{counters.sync()};reopen();repeat(3){counters.sync()}
            val counter=counters.observe(owner,1,3).first();assertEquals(1,counter.count);assertFalse(counter.pending);assertFalse(counter.conflict)

            fun body(count:Int)=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":$count,"note":"Lost-response fixture"}""").asJsonObject
            nights.localExemptions(setOf(LocalDate.of(2026,10,23)))
            nights.save(body(10),nightId);nights.save(body(12),nightId)
            val queue=db.dao().sessionMutations(owner,nightId)
            lose="POST night-prayer/sync";lost{nights.sync()};assertEquals(queue,db.dao().sessionMutations(owner,nightId))
            reopen();assertEquals(queue,db.dao().sessionMutations(owner,nightId));repeat(3){nights.sync()}
            assertTrue(db.dao().sessionMutations(owner,nightId).isEmpty());assertFalse(db.dao().night(owner,nightId)!!.dirty)
            val remote=session.request("GET","night-prayer/sessions/$nightId").asJsonObject
            assertEquals(12,remote["verse_count"].asInt);assertEquals(2,remote["revision"].asInt)
            nights.delete(nightId);lose="POST night-prayer/sync";lost{nights.sync()};reopen()
            assertTrue(db.dao().night(owner,nightId)!!.deleted);assertTrue(db.dao().night(owner,nightId)!!.dirty)
            repeat(3){nights.sync()};assertFalse(db.dao().night(owner,nightId)!!.dirty)
            assertEquals(3,session.request("GET","night-prayer/sessions/$nightId").asJsonObject["revision"].asInt)
            assertEquals(setOf(LocalDate.of(2026,10,23)),nights.localExemptions())
            assertFalse(Gson().toJson(sentNight).contains("2026-10-23"))
            assertEquals(baseline.getValue("quran_sessions"),stats().getValue("quran_sessions"))
            assertEquals(baseline.getValue("adhkar_completions")+1,stats().getValue("adhkar_completions"))
            assertEquals(baseline.getValue("lesson_completions")+1,stats().getValue("lesson_completions"))
        }finally{
            reading.close();lessons.close();counters.close();db.close();context.deleteDatabase(name);session.logout()
        }
    }
}
