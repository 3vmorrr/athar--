package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.nightprayer.NightPrayerRepository
import org.athar.app.quran.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** All corruption is confined to an in-memory DB; never invalidates the installed app's key. */
class ProtectedStorageTest {
    private fun tamper(value:String):String {
        val bytes=android.util.Base64.decode(value,android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        return android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
    }
    private suspend fun fixture(block:suspend (AppDatabase,SessionRepository,PrivateCipher)->Unit) {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
        val transport=object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
                if(path=="auth/login")return JsonParser.parseString("""{"user":{"id":1,"email":"fixture@example.test","email_verified":true},"session":{"id":"fixture","access_token":"fixture","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                throw java.io.IOException("Offline fixture; never sends private records")
            }
        }
        val session=SessionRepository(transport,store)
        try{session.login("fixture@example.test","Fixture123!");block(db,session,PrivateCipher())}finally{db.close()}
    }
    @Test fun counterCorruptionIsNotZeroAndCannotBeOverwrittenByATap()=runBlocking {
        fixture { db,session,cipher ->
            val repo=CounterRepository(db.dao(),cipher,session,automaticSync=false)
            try {
                repo.tap(1,3)
                val original=requireNotNull(db.dao().cache(1,"adhkar:1"))
                for(payload in listOf("damaged",tamper(original.payloadCipher),cipher.seal("""{"item":1,"target":0}"""),cipher.seal("""{"item":1,"target":3,"count":1,"revision":0,"localVersion":1}"""))){
                    val damaged=original.copy(payloadCipher=payload);db.dao().cache(damaged)
                    assertEquals(ProtectedRead.Unreadable,repo.observe(1,1,3).protectedReads().first())
                    assertTrue(runCatching{repo.tap(1,3)}.isFailure)
                    assertEquals(damaged,db.dao().cache(1,"adhkar:1"))
                    assertTrue(repo.observe(2,1,3).protectedReads().first() is ProtectedRead.Ready)
                }
                db.dao().cache(original)
                val recovered=repo.observe(1,1,3).protectedReads().first() as ProtectedRead.Ready
                assertEquals(1,recovered.value.count);assertTrue(recovered.value.pending)
            }finally{repo.close()}
        }
    }
    @Test fun invalidReadingMarkerRemainsRetainedAndRetryCanReadRestoredFixture()=runBlocking {
        fixture { db,session,cipher ->
            val context=ApplicationProvider.getApplicationContext<android.content.Context>()
            val index=QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use{it.readText()})
            val repo=ReadingRepository(db.dao(),cipher,session,{index},automaticSync=false)
            try {
                repo.save(1,4);val original=requireNotNull(db.dao().cache(1,"quran-reading-position"))
                for(payload in listOf("damaged",tamper(original.payloadCipher),cipher.seal("{}"))){
                    val damaged=original.copy(payloadCipher=payload);db.dao().cache(damaged)
                    assertEquals(ProtectedRead.Unreadable,repo.observe(1).protectedReads().first())
                    assertTrue(runCatching{repo.save(1,5)}.isFailure)
                    assertEquals(damaged,db.dao().cache(1,"quran-reading-position"))
                }
                db.dao().cache(original)
                assertEquals(4,requireNotNull((repo.observe(1).protectedReads().first() as ProtectedRead.Ready).value).ayah)
            }finally{repo.close()}
        }
    }
    @Test fun prayerAndExemptionFailuresPreserveCiphertextAndPendingOperations()=runBlocking {
        fixture { db,session,cipher ->
            val repo=NightPrayerRepository(db,session,cipher);val id=UUID.randomUUID().toString()
            val body=JsonParser.parseString("""{"night_date":"2026-10-25","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":10,"note":"Private fixture"}""").asJsonObject
            repo.save(body,id)
            val original=requireNotNull(db.dao().night(1,id));val pending=db.dao().pending(1)
            val damaged=original.copy(payloadCipher="damaged");db.dao().night(damaged)
            assertEquals(ProtectedRead.Unreadable,repo.observe(1).protectedReads().first())
            assertTrue(runCatching{repo.save(body,id)}.isFailure)
            assertEquals(damaged,db.dao().night(1,id));assertEquals(pending,db.dao().pending(1))
            db.dao().night(original)
            assertTrue(repo.observe(1).protectedReads().first() is ProtectedRead.Ready)
            for(inconsistent in listOf(original.copy(verseCount=11),original.copy(measuredSeconds=45))){
                db.dao().night(inconsistent)
                assertEquals(ProtectedRead.Unreadable,repo.observe(1).protectedReads().first())
                assertTrue(runCatching{repo.save(body,id)}.isFailure)
                assertEquals(inconsistent,db.dao().night(1,id));assertEquals(pending,db.dao().pending(1))
            }
            db.dao().night(original)
            repo.localExemptions(setOf(LocalDate.of(2026,10,24)))
            val exemption=requireNotNull(db.dao().exemptions(1));val bad=exemption.copy(encryptedPayload="damaged")
            db.dao().exemptions(bad)
            assertTrue(runCatching{repo.localExemptions()}.isFailure)
            assertTrue(runCatching{repo.localExemptions(emptySet())}.isFailure)
            assertEquals(bad,db.dao().exemptions(1));assertEquals(pending,db.dao().pending(1))
            db.dao().exemptions(exemption)
            assertEquals(setOf(LocalDate.of(2026,10,24)),repo.localExemptions())
        }
    }
}
