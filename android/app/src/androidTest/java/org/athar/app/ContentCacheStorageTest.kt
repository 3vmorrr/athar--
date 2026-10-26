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

class ContentCacheStorageTest {
    @Test fun boundedContentEvictionPreservesPrivateAndOtherAccountRows()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        try {
            val dao=db.dao();val policy=ContentCachePolicy(2,100,80,1000)
            val private=CachedDocument(1,"adhkar:1","retained pending ciphertext",0)
            val other=CachedDocument(2,"ar:other","other account ciphertext",0)
            dao.cache(private);dao.cache(other)
            dao.storeContent(CachedDocument(1,"ar:one","a".repeat(30),100),100,policy)
            dao.storeContent(CachedDocument(1,"en:two","b".repeat(30),101),101,policy)
            dao.storeContent(CachedDocument(1,"ar:three","c".repeat(30),102),102,policy)
            assertNull(dao.cache(1,"ar:one"));assertEquals(2,dao.contentCacheEntries(1).size)
            assertEquals(private,dao.cache(1,private.cacheKey));assertEquals(other,dao.cache(2,other.cacheKey))
            dao.storeContent(CachedDocument(1,"ar:large","x".repeat(81),103),103,policy)
            assertNull(dao.cache(1,"ar:large"))
            dao.trimContentCache(1,2000,policy)
            assertTrue(dao.contentCacheEntries(1).isEmpty())
            assertEquals(private,dao.cache(1,private.cacheKey));assertEquals(other,dao.cache(2,other.cacheKey))
        }finally{db.close()}
    }
    @Test fun encryptedFallbackExpiresAndCorruptionCannotHideFreshContent()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        try {
            var offline=false;var clock=1000L;var beforeFailure:(suspend ()->Unit)?=null
            val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
            val transport=object:ApiTransport{
                override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                    if(path=="auth/login"){
                        val id=if(body["email"]=="other@example.test")2 else 1
                        return JsonParser.parseString("""{"user":{"id":$id,"email":"cache@example.test","email_verified":true},"session":{"id":"fixture","access_token":"fixture","refresh_token":"fixture","expires_in":900,"scope":"full"}}""")
                    }
                    if(offline){beforeFailure?.invoke();throw IOException("offline fixture")}
                    return JsonParser.parseString("""{"title":"fresh fixture"}""")
                }
            }
            val session=SessionRepository(transport,store);session.login("cache@example.test","Fixture123!")
            val repo=ContentRepository(session,db.dao(),PrivateCipher(),cachePolicy=ContentCachePolicy(maxAgeMillis=1000),now={clock})
            assertFalse(repo.get("books").offline);offline=true
            assertTrue(repo.get("books").offline)
            clock=2001;assertTrue(runCatching{repo.get("books")}.exceptionOrNull() is IOException)
            assertNull(db.dao().cache(1,"ar:books?"))
            db.dao().cache(CachedDocument(1,"ar:books?","damaged disposable fixture",clock))
            assertTrue(runCatching{repo.get("books")}.exceptionOrNull() is IOException)
            assertNull(db.dao().cache(1,"ar:books?"))
            offline=false;assertFalse(repo.get("books").offline)
            val retained=db.dao().cache(1,"ar:books?")
            beforeFailure={session.login("other@example.test","Fixture123!")};offline=true
            val switched=runCatching{repo.get("books")}.exceptionOrNull()
            assertTrue(switched is ApiException);assertEquals(401,(switched as ApiException).status)
            assertEquals(retained,db.dao().cache(1,"ar:books?"));assertNull(db.dao().cache(2,"ar:books?"))
        }finally{db.close()}
    }
}
