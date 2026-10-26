package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class UpcomingCacheStorageTest {
    @Test fun cachedEventsAreEncryptedLocaleAndOwnerScopedAnd404CannotResurrectThem()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            var language="ar";var offline=false;var removed=false
            val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
            val transport=object:ApiTransport{
                override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                    if(path=="auth/login"){
                        val id=if(body["email"]=="reader@example.test")1 else 2
                        return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"fixture","access_token":"fixture","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                    }
                    if(path=="auth/logout")return JsonObject()
                    if(offline)throw IOException("offline fixture")
                    if(removed)throw ApiException(404,"not_found","")
                    return JsonParser.parseString("""{"id":5,"title":"private fixture $language","status":"live"}""")
                }
            }
            val session=SessionRepository(transport,store,language={language})
            session.login("reader@example.test","Fixture123!")
            val repo=ContentRepository(session,db.dao(),PrivateCipher(),language={language})
            assertFalse(repo.get("upcoming-lessons/5").offline)
            val raw=db.dao().caches(1,"%upcoming-lessons%")
            assertEquals(1,raw.size);assertFalse(raw.single().payloadCipher.contains("private fixture"))
            offline=true;assertTrue(repo.get("upcoming-lessons/5").offline)
            language="en";assertTrue(runCatching{repo.get("upcoming-lessons/5")}.isFailure)
            language="ar";session.login("other@example.test","Fixture123!")
            assertTrue(runCatching{repo.get("upcoming-lessons/5")}.isFailure)
            session.login("reader@example.test","Fixture123!")
            assertTrue(repo.get("upcoming-lessons/5").offline)
            offline=false;removed=true
            try{repo.get("upcoming-lessons/5");fail("Expected authoritative removal")}catch(failure:ApiException){assertEquals(404,failure.status)}
            assertTrue(db.dao().caches(1,"%upcoming-lessons%").isEmpty())
            offline=true;assertTrue(runCatching{repo.get("upcoming-lessons/5")}.isFailure)
        }finally{db.close()}
    }
}
