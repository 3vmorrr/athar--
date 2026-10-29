package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.junit.Assert.*
import org.junit.Test

class SavedCollectionStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Server : ApiTransport {
        var delayed = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
            if (path == "auth/login") {
                val owner = if (body["email"] == "other@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$owner,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$owner","access_token":"a$owner","refresh_token":"r$owner","expires_in":900,"scope":"full"}}""")
            }
            if (delayed) { started.complete(Unit); release.await() }
            return JsonArray()
        }
    }
    @Test fun invalidationDropsEveryPageInBothLanguagesOnlyForCapturedOwner() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val server = Server(); val session = SessionRepository(server, Store()); val cipher = PrivateCipher()
        session.login("reader@example.test", "Fixture123!")
        val repository = ContentRepository(session, db.dao(), cipher)
        try {
            val keys = listOf("ar:quran/bookmarks?", "ar:quran/bookmarks?page=1", "en:quran/bookmarks?page=2", "ar:me/favorites?page=1", "book-page:1:abc")
            for (owner in listOf(1L, 2L)) for (key in keys) db.dao().cache(CachedDocument(owner, key, cipher.seal("[]"), System.currentTimeMillis()))
            repository.invalidate("quran/bookmarks", 1)
            for (key in keys) {
                assertEquals(!key.contains("quran/bookmarks?"), db.dao().cache(1, key) != null)
                assertNotNull(db.dao().cache(2, key))
            }
        } finally { db.close() }
    }
    @Test fun inFlightOldGetCannotRepopulateInvalidatedPages() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val server = Server(); val session = SessionRepository(server, Store())
        session.login("reader@example.test", "Fixture123!")
        val repository = ContentRepository(session, db.dao(), PrivateCipher())
        try {
            server.delayed = true
            val pending = async { repository.get("quran/bookmarks", mapOf("paged" to "1"), expectedOwner = 1) }
            withTimeout(10000) { server.started.await() }
            repository.invalidate("quran/bookmarks", 1)
            server.release.complete(Unit); pending.await()
            assertTrue(db.dao().caches(1, "ar:quran/bookmarks?%").isEmpty())
        } finally { server.release.complete(Unit); db.close() }
    }
    @Test fun capturedOwnerRejectsLaterAccountAndNeverCachesDelayedResponse() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val server = Server(); val session = SessionRepository(server, Store())
        session.login("reader@example.test", "Fixture123!")
        val repository = ContentRepository(session, db.dao(), PrivateCipher())
        try {
            server.delayed = true
            val pending = async { runCatching { repository.get("me/favorites", expectedOwner = 1) } }
            withTimeout(10000) { server.started.await() }
            session.login("other@example.test", "Fixture123!")
            server.release.complete(Unit)
            assertTrue(pending.await().isFailure)
            assertTrue(db.dao().caches(1, "ar:me/favorites?%").isEmpty())
            assertTrue(runCatching { repository.get("me/favorites", expectedOwner = 1) }.isFailure)
        } finally { server.release.complete(Unit); db.close() }
    }
}
