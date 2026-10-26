package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository
import org.athar.app.quran.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** Fake actors in an in-memory database; no real profile/password/OTP or key mutation. */
class OwnedMutationStorageTest {
    private class Fixture:AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val dao=db.dao();val cipher=PrivateCipher()
        var requests=0
        var verified=true
        var respond:suspend(String)->JsonElement={throw java.io.IOException("Offline fixture")}
        val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
        val session=SessionRepository(object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                if(path=="auth/login"){
                    val actor=if(body["email"]=="other@example.test")2 else 1
                    val scope=if(verified)"full"else"verification"
                    return JsonParser.parseString("""{"user":{"id":$actor,"email":"fixture@example.test","email_verified":$verified},"session":{"id":"fixture","access_token":"actor-$actor","refresh_token":"fixture","expires_in":900,"scope":"$scope"}}""")
                }
                requests++;return respond(path)
            }
        },store)
        val index=QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use{it.readText()})
        val reading=ReadingRepository(dao,cipher,session,{index},automaticSync=false)
        val counters=CounterRepository(dao,cipher,session,automaticSync=false)
        val night=NightPrayerRepository(db,session,cipher)
        val lessons=LessonRepository(dao,cipher,session,automaticSync=false)
        suspend fun login(other:Boolean=false)=session.login(if(other)"other@example.test"else"fixture@example.test","Fixture123!")
        override fun close(){reading.close();counters.close();lessons.close();db.close()}
    }
    @Test fun queuedOldActorActionsCannotTouchEitherAccountsRetainedData()=runBlocking{
        Fixture().use{f->
            f.login();f.reading.save(1,4);f.counters.tap(1,3)
            val id=UUID.randomUUID().toString()
            val body=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","entry_type":"manual","verse_count":12}""").asJsonObject
            f.night.save(body,id);f.night.localExemptions(setOf(LocalDate.of(2026,10,24)));f.lessons.save(2,42,100)
            val cached=f.dao.caches(1,"%").toSet();val nights=f.dao.allNights(1);val pending=f.dao.pending(1);val exemptions=f.dao.exemptions(1)
            f.login(true);val beforeRequests=f.requests
            val results=listOf(
                runCatching{f.reading.save(1,5,1)},runCatching{f.reading.refresh(1)},runCatching{f.reading.resolve(true,1)},
                runCatching{f.counters.tap(1,3,expectedOwner=1)},runCatching{f.counters.tap(1,3,true,1)},runCatching{f.counters.useServer(1,3,1)},runCatching{f.counters.refresh(1,3,1)},
                runCatching{f.night.save(body,id,1)},runCatching{f.night.delete(id,1)},runCatching{f.night.reviewConflict(id,1)},
                runCatching{f.night.localExemptions(1)},runCatching{f.night.localExemptions(emptySet(),1)},
                runCatching{f.lessons.save(2,0,100,true,1)},
                runCatching{ContentRepository(f.session,f.dao,f.cipher).invalidate("me/favorites",1)}
            )
            assertTrue(results.all{it.isFailure});assertEquals(beforeRequests,f.requests)
            assertEquals(cached,f.dao.caches(1,"%").toSet());assertEquals(nights,f.dao.allNights(1));assertEquals(pending,f.dao.pending(1));assertEquals(exemptions,f.dao.exemptions(1))
            assertTrue(f.dao.caches(2,"%").isEmpty());assertTrue(f.dao.allNights(2).isEmpty());assertTrue(f.dao.pending(2).isEmpty());assertNull(f.dao.exemptions(2))
        }
    }
    @Test fun sameActorPendingStateCannotModifyRetainedPrivateRecords()=runBlocking{
        Fixture().use{f->
            f.login();f.reading.save(1,4);f.counters.tap(1,3);f.lessons.save(2,42,100)
            f.night.localExemptions(setOf(LocalDate.of(2026,10,24)))
            val original=f.dao.caches(1,"%").toSet();val exemptions=f.dao.exemptions(1)
            f.verified=false;f.login();val requests=f.requests
            val results=listOf(runCatching{f.reading.save(1,5,1)},runCatching{f.counters.tap(1,3,expectedOwner=1)},
                runCatching{f.lessons.save(2,0,100,true,1)},runCatching{f.night.localExemptions(emptySet(),1)},
                runCatching{f.reading.sync(1)},runCatching{f.counters.sync(1)},runCatching{f.lessons.sync(1)})
            assertTrue(results.all{it.isFailure});assertEquals(requests,f.requests)
            assertEquals(original,f.dao.caches(1,"%").toSet());assertEquals(exemptions,f.dao.exemptions(1))
            assertTrue(f.session.state.value is org.athar.app.auth.AuthState.Pending)
        }
    }
    @Test fun readingOwnerIsCapturedBeforeSuspendingForTheCanonicalIndex()=runBlocking{
        Fixture().use{f->
            f.login();val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val reader=ReadingRepository(f.dao,f.cipher,f.session,{entered.complete(Unit);release.await();f.index},automaticSync=false)
            try{
                val save=async(Dispatchers.Default){runCatching{reader.save(1,4)}}
                withTimeout(5_000){entered.await()};f.login(true);release.complete(Unit)
                assertTrue(save.await().isFailure)
                assertNull(f.dao.cache(1,"quran-reading-position"));assertNull(f.dao.cache(2,"quran-reading-position"))
            }finally{release.complete(Unit);reader.close()}
        }
    }
    @Test fun switchedActorDoesNotConsumeTheOldPendingCounterAfterASuspendedResponse()=runBlocking{
        Fixture().use{f->
            f.login();f.counters.tap(1,3)
            val original=f.dao.cache(1,"adhkar:1")
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            f.respond={entered.complete(Unit);release.await();JsonParser.parseString("""{"revision":1}""")}
            val sync=async(Dispatchers.Default){runCatching{f.counters.sync(1)}}
            withTimeout(5_000){entered.await()};f.login(true);release.complete(Unit)
            assertTrue(sync.await().isFailure);assertEquals(original,f.dao.cache(1,"adhkar:1"));assertNull(f.dao.cache(2,"adhkar:1"))
        }
    }
}
