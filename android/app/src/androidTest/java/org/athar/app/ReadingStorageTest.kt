package org.athar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AppDatabase
import org.athar.app.core.network.ApiException
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.quran.*

class ReadingStorageTest {
    @Test fun resumeSurvivesOfflineRetriesAndRequiresAnExplicitConflictChoice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
        val store = object : TokenStore {
            var value: StoredSession? = null
            override suspend fun load() = value
            override suspend fun save(session: StoredSession) { value = session }
            override suspend fun clear() { value = null }
        }
        var offline = true
        var loseAcknowledgement = false
        var remote: SavedReadingPosition? = null
        var writes = 0
        val transport = object : ApiTransport {
            override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                if (path == "auth/login") {
                    val id = if (body["email"] == "other@example.test") 2 else 1
                    return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
                }
                assertEquals("No activity or completion may be inferred", "quran/position", path)
                if (offline) throw java.io.IOException("offline fixture")
                if (method == "PUT") {
                    if ((body["expected_revision"] as Number).toInt() != (remote?.revision ?: 0)) throw ApiException(409, "conflict", "fixture")
                    writes++
                    remote = SavedReadingPosition((body["surah"] as Number).toInt(), (body["ayah"] as Number).toInt(), (remote?.revision ?: 0) + 1)
                    if (loseAcknowledgement) { loseAcknowledgement = false; throw java.io.IOException("response lost fixture") }
                }
                return remote?.let { JsonObject().apply { addProperty("surah", it.surah); addProperty("ayah", it.ayah); addProperty("revision", it.revision) } } ?: JsonNull.INSTANCE
            }
        }
        val session = SessionRepository(transport, store)
        session.login("reader@example.test", "Fixture123!")
        val cipher = PrivateCipher()
        var repo = ReadingRepository(db.dao(), cipher, session, index = { index }, automaticSync = false)
        try {
            repo.save(114, 6)
            try { repo.save(114, 7); fail("Noncanonical ayah accepted") } catch (_: IllegalArgumentException) { }
            assertFalse(db.dao().cache(1, "quran-reading-position")!!.payloadCipher.contains("\"surah\""))
            repo.close()
            repo = ReadingRepository(db.dao(), cipher, session, index = { index }, automaticSync = false)
            assertEquals(6, repo.saved(1)!!.ayah)
            assertTrue(repo.saved(1)!!.pending)
            try { repo.sync(); fail("Expected offline failure") } catch (_: java.io.IOException) { }
            assertTrue(repo.saved(1)!!.pending)

            offline = false
            loseAcknowledgement = true
            try { repo.sync(); fail("Expected lost acknowledgement") } catch (_: java.io.IOException) { }
            assertEquals(1, writes)
            repo.sync()
            assertEquals("Lost acknowledgement retry must not repeat the mutation", 1, writes)
            assertFalse(repo.saved(1)!!.pending)
            assertEquals(1, repo.saved(1)!!.revision)

            repo.save(1, 2)
            remote = SavedReadingPosition(114, 5, 10)
            repo.sync()
            assertTrue(repo.saved(1)!!.conflict)
            assertEquals(1, repo.saved(1)!!.surah)
            assertEquals(114, remote!!.surah)
            repo.refresh()
            assertTrue("Refresh must not overwrite the pending local choice", repo.saved(1)!!.conflict)
            repo.resolve(useRemote = true)
            assertEquals(5, repo.saved(1)!!.ayah)
            assertFalse(repo.saved(1)!!.pending)

            repo.save(1, 1)
            remote = SavedReadingPosition(114, 4, 11)
            repo.sync()
            assertTrue(repo.saved(1)!!.conflict)
            repo.resolve(useRemote = false)
            assertTrue(repo.saved(1)!!.pending)
            repo.sync()
            assertEquals(12, remote!!.revision)
            assertEquals(1, remote!!.surah)
            assertEquals(1, remote!!.ayah)
            assertFalse(repo.saved(1)!!.conflict)

            repo.save(1, 3)
            session.login("other@example.test", "Fixture123!")
            assertNull(repo.saved(2))
            val before = writes
            repo.sync()
            assertEquals(before, writes)
            assertTrue(repo.saved(1)!!.pending)
        } finally { repo.close(); db.close() }
    }
}
