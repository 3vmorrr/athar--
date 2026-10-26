package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository
import org.junit.Assert.*
import org.junit.Test

class LessonRecoveryStorageTest {
    private suspend fun fixture(block:suspend(AppDatabase,SessionRepository,PrivateCipher,LessonRepository)->Unit){
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
        val transport=object:ApiTransport{
            override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                if(path=="auth/login"){
                    val actor=if(body["email"]=="other@example.test")2 else 1
                    return JsonParser.parseString("""{"user":{"id":$actor,"email":"fixture@example.test","email_verified":true},"session":{"id":"fixture","access_token":"fixture","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                }
                throw java.io.IOException("Offline fixture")
            }
        }
        val session=SessionRepository(transport,store);session.login("fixture@example.test","Fixture123!")
        val cipher=PrivateCipher();val repo=LessonRepository(db.dao(),cipher,session,automaticSync=false)
        try{block(db,session,cipher,repo)}finally{repo.close();db.close()}
    }
    @Test fun unreadableCheckpointsNeverBecomeZeroOrGetReplaced()=runBlocking{
        fixture{db,session,cipher,repo->
            repo.save(2,42,100)
            val original=requireNotNull(db.dao().cache(1,"lesson-progress:2"))
            val body=JsonParser.parseString(cipher.open(original.payloadCipher)).asJsonObject
            val missing=body.deepCopy().apply{remove("complete")}
            val impossible=body.deepCopy().apply{addProperty("positionSeconds",101)}
            val wrong=body.deepCopy().apply{addProperty("lessonId",3)}
            val badBoolean=body.deepCopy().apply{addProperty("pending","true")}
            val badCompletion=body.deepCopy().apply{addProperty("complete",true);addProperty("eventId","invalid")}
            val altered=android.util.Base64.decode(original.payloadCipher,android.util.Base64.NO_WRAP)
            altered[altered.lastIndex]=(altered.last().toInt() xor 1).toByte()
            val damagedTag=android.util.Base64.encodeToString(altered,android.util.Base64.NO_WRAP)
            for(payload in listOf("damaged",damagedTag,cipher.seal("{}"),cipher.seal(missing.toString()),cipher.seal(impossible.toString()),cipher.seal(wrong.toString()),cipher.seal(badBoolean.toString()),cipher.seal(badCompletion.toString()))){
                val damaged=original.copy(payloadCipher=payload);db.dao().cache(damaged)
                assertEquals(ProtectedRead.Unreadable,repo.observe(1,2).protectedReads().first())
                assertTrue(runCatching{repo.save(2,0,100,true)}.isFailure)
                assertTrue(runCatching{repo.sync()}.isFailure)
                assertTrue(runCatching{retainedAccountData(1,session,repo,NightPrayerRepository(db,session,cipher))}.isFailure)
                assertEquals(damaged,db.dao().cache(1,"lesson-progress:2"))
                assertNull(repo.saved(2,2))
            }
            db.dao().cache(original)
            val recovered=(repo.observe(1,2).protectedReads().first() as ProtectedRead.Ready).value!!
            assertEquals(42,recovered.positionSeconds);assertTrue(recovered.pending);assertFalse(recovered.complete)
            val export=retainedAccountData(1,session,repo,NightPrayerRepository(db,session,cipher))
            assertEquals(42,export["device_lesson_progress"].asJsonArray[0].asJsonObject["positionSeconds"].asInt)
        }
    }
    @Test fun capturedOwnerAndMalformedNightPreventAnUnsafeExport()=runBlocking{
        fixture{db,session,cipher,repo->
            repo.save(2,42,100)
            val original=db.dao().cache(1,"lesson-progress:2")
            db.dao().night(LocalNightSession(1,"fixture","2026-10-24",12,null,"damaged",1,0,false,true,1))
            assertTrue(runCatching{retainedAccountData(1,session,repo,NightPrayerRepository(db,session,cipher))}.isFailure)
            session.login("other@example.test","Fixture123!")
            assertTrue(runCatching{repo.save(2,0,100,true,expectedOwner=1)}.isFailure)
            assertTrue(runCatching{retainedAccountData(1,session,repo,NightPrayerRepository(db,session,cipher))}.isFailure)
            assertEquals(original,db.dao().cache(1,"lesson-progress:2"));assertNull(db.dao().cache(2,"lesson-progress:2"))
        }
    }
}
