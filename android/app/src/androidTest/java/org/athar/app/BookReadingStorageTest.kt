package org.athar.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.athar.app.auth.SessionRepository
import org.athar.app.books.*
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookReadingStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Login : ApiTransport {
        override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
            check(path == "auth/login")
            val id = if (body["email"] == "two@example.test") 2 else 1
            return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
        }
    }
    @Test fun encryptedPositionSurvivesReopenAndPreservesUnreadableOrRevokedOwnerState() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "qa-book-progress-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(root.name + "-" + name, mode)
        }
        val db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).build()
        val cipher = PrivateCipher(); val session = SessionRepository(Login(), Store())
        val scope = BookScope("0123456789ab", 1); val digest = "a".repeat(64)
        try {
            session.login("one@example.test", "Fixture123!")
            var reading = BookReadingStore(context, scope.namespace, db, cipher, session)
            assertEquals(0, reading.read(scope, 7, digest, 100))
            reading.save(scope, 7, digest, 100, 19)
            val row = checkNotNull(db.dao().cache(1, BookReadingPayload.key(7, digest)))
            assertFalse(row.payloadCipher.contains("\"page\":19"))
            reading = BookReadingStore(context, scope.namespace, db, cipher, session)
            assertEquals(19, reading.read(scope, 7, digest, 100))
            assertEquals(0, reading.read(scope, 7, "b".repeat(64), 100))
            session.login("two@example.test", "Fixture123!")
            assertTrue(runCatching { reading.read(scope, 7, digest, 100) }.isFailure)
            val two = scope.copy(owner = 2)
            assertEquals(0, reading.read(two, 7, digest, 100))
            val broken = CachedDocument(2, BookReadingPayload.key(7, digest), cipher.seal("{}"), 123)
            db.dao().cache(broken)
            assertTrue(runCatching { reading.read(two, 7, digest, 100) }.isFailure)
            assertTrue(runCatching { reading.save(two, 7, digest, 100, 0) }.isFailure)
            assertEquals(broken, db.dao().cache(2, broken.cacheKey))
            BookFiles(File(root, "books"), two).revoke()
            db.dao().deleteUser(2)
            assertTrue(runCatching { reading.save(two, 7, digest, 100, 1) }.isFailure)
            assertNull(db.dao().cache(2, broken.cacheKey))
            assertEquals(row, db.dao().cache(1, row.cacheKey))
        } finally {
            db.close(); context.deleteSharedPreferences(root.name + "-" + LEGACY_BOOK_READER_PREFERENCES)
            check(root.parentFile == base.cacheDir && root.name.startsWith("qa-book-progress-")); root.deleteRecursively()
        }
    }

    @Test fun queuedWriteRechecksTheActorInsideTheActualDatabaseTransaction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val session = SessionRepository(Login(), Store()); session.login("one@example.test", "Fixture123!")
        val scope = BookScope("0123456789ab", 1)
        val reading = BookReadingStore(context, scope.namespace, db, PrivateCipher(), session)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val holder = launch(Dispatchers.Default) { db.withTransaction { entered.complete(Unit); release.await() } }
        try {
            entered.await()
            val write = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { runCatching { reading.save(scope, 7, "a".repeat(64), 100, 19) } }
            session.login("two@example.test", "Fixture123!")
            release.complete(Unit); holder.join()
            assertTrue(write.await().isFailure)
            assertNull(db.dao().cache(1, BookReadingPayload.key(7, "a".repeat(64))))
        } finally { release.complete(Unit); holder.join(); db.close() }
    }
}
