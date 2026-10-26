package org.athar.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import android.content.Context
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AppDatabase
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.athar.app.nightprayer.NightPrayerRepository
import org.athar.app.quran.QuranRepository
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NativeStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun lessonCheckpointsSurviveOfflineAndCompletionIsExplicit() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        try {
            var offline=true;var calls=0;val eventIds=mutableSetOf<String>()
            val store=object:TokenStore{var value:StoredSession?=null;override suspend fun load()=value;override suspend fun save(session:StoredSession){value=session};override suspend fun clear(){value=null}}
            val transport=object:ApiTransport{
                override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement{
                    if(path=="auth/login")return JsonParser.parseString("""{"user":{"id":1,"email":"reader@example.test","email_verified":true},"session":{"id":"s","access_token":"a","refresh_token":"r","expires_in":900,"scope":"full"}}""")
                    assertEquals("me/lesson-progress",path);if(offline)throw java.io.IOException("offline fixture")
                    calls++;if(body["is_complete"]==true)eventIds+=body["event_id"].toString();return JsonObject()
                }
            }
            val session=SessionRepository(transport,store);session.login("reader@example.test","Fixture123!")
            val cipher=PrivateCipher();val first=org.athar.app.learning.LessonRepository(db.dao(),cipher,session)
            first.save(7,42,100);first.close()
            val restored=org.athar.app.learning.LessonRepository(db.dao(),cipher,session)
            assertEquals(42,restored.saved(1,7)!!.positionSeconds);assertFalse(restored.saved(1,7)!!.complete)
            try{restored.sync();fail("Expected offline fixture")}catch(_:java.io.IOException){}
            assertTrue(restored.saved(1,7)!!.pending);assertNull(restored.saved(2,7))
            offline=false;restored.sync();assertFalse(restored.saved(1,7)!!.pending)
            restored.save(7,95,100,true);restored.sync();restored.sync()
            assertEquals(1,eventIds.size);assertTrue(restored.saved(1,7)!!.complete);assertTrue(calls>=2)
            restored.close()
        }finally{db.close()}
    }
    @Test fun playerOnlyAcceptsApprovedHttpsMediaOrigins() {
        fun approved(url: String) = org.athar.app.media.PlaybackService.approvedMedia(android.net.Uri.parse(url))
        assertTrue(approved("https://cdn.mp3quran.net/audio/reader/r1/001.mp3"))
        assertTrue(approved("https://backup.qurango.net/radio/reader"))
        assertFalse(approved("https://mp3quran.net.attacker.invalid/a.mp3"))
        assertFalse(approved("http://cdn.mp3quran.net/a.mp3"))
        assertFalse(approved("https://user:pass@cdn.mp3quran.net/a.mp3"))
        assertFalse(approved("https://127.0.0.1/a.mp3"))
    }
    @Test fun bundledQuranLoadsAllOriginalVersesAndArabicSearchOffline() = runBlocking {
        val data = QuranRepository(context).data()
        assertEquals(6236, data.verses.size)
        assertEquals(7, data.surah(1).size)
        assertEquals(6, data.surah(114).size)
        assertTrue(data.search("الحمد لله").isNotEmpty())
        assertTrue(data.section("page", 604).isNotEmpty())
    }
    @Test fun offlineNightEditsEncryptPrivateNotesAndSyncInOrderWithoutExemptions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val bodies = mutableListOf<Map<String, Any?>>()
            val store = object : TokenStore {
                var value: StoredSession? = null
                override suspend fun load() = value
                override suspend fun save(session: StoredSession) { value = session }
                override suspend fun clear() { value = null }
            }
            val transport = object : ApiTransport {
                override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                    if (path == "auth/login") return JsonParser.parseString("""{"user":{"id":1,"email":"reader@example.test","display_name":"Reader","username":"reader","role":"user","email_verified":true},"session":{"id":"s","access_token":"a","refresh_token":"r","expires_in":900,"scope":"full"}}""")
                    assertEquals("night-prayer/sync", path)
                    bodies += body
                    val operations = Gson().toJsonTree(body["operations"]).asJsonArray
                    val results = JsonArray()
                    operations.forEach { element -> val op = element.asJsonObject; val expected = op["data"].asJsonObject["expected_revision"].asInt; results.add(JsonObject().apply { addProperty("id", op["id"].asString); addProperty("status", "applied"); add("result", JsonObject().apply { addProperty("revision", expected + 1) }) }) }
                    return JsonObject().apply { add("results", results); add("changes", JsonArray()); addProperty("next_cursor", 2); addProperty("has_more", false) }
                }
            }
            val session = SessionRepository(transport, store)
            session.login("reader@example.test", "NativeTest123!")
            val repository = NightPrayerRepository(db, session, PrivateCipher())
            val id = UUID.randomUUID().toString()
            val payload = JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"Africa/Cairo","boundary_source":"manual","entry_type":"manual","verse_count":10,"note":"private test note"}""").asJsonObject
            repository.save(payload, id)
            payload.addProperty("verse_count", 12); repository.save(payload, id)
            assertEquals(listOf(0, 1), db.dao().pending(1).map { it.expectedRevision })
            assertFalse(db.dao().night(1, id)!!.payloadCipher.contains("private test note"))
            repository.localExemptions(setOf(LocalDate.of(2026, 10, 23)))
            assertEquals(0, repository.sync())
            assertTrue(db.dao().pending(1).isEmpty())
            assertEquals(2, db.dao().night(1, id)!!.serverRevision)
            assertFalse(db.dao().night(1, id)!!.dirty)
            assertFalse(Gson().toJson(bodies).contains("2026-10-23"))
            assertEquals(setOf(LocalDate.of(2026, 10, 23)), repository.localExemptions())
            assertNull(db.dao().night(2, id))
            db.dao().deleteUser(1)
            assertNull(db.dao().exemptions(1)); assertTrue(db.dao().allNights(1).isEmpty())
        } finally { db.close() }
    }
    @Test fun adhkarCounterSurvivesRepositoryRestartAndQueuesEveryCompletedCycle() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val events = mutableSetOf<String>()
            var revision = 0
            val store = object : TokenStore {
                var value: StoredSession? = null
                override suspend fun load() = value
                override suspend fun save(session: StoredSession) { value = session }
                override suspend fun clear() { value = null }
            }
            val transport = object : ApiTransport {
                override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                    if (path == "auth/login") return JsonParser.parseString("""{"user":{"id":1,"email":"reader@example.test","role":"user","email_verified":true},"session":{"id":"s","access_token":"a","refresh_token":"r","expires_in":900,"scope":"full"}}""")
                    if (path == "me/activity") { events += body["event_id"].toString(); return JsonObject() }
                    assertEquals("me/adhkar-progress", path)
                    if (method == "GET") return JsonArray()
                    revision++
                    return JsonObject().apply { addProperty("revision", revision) }
                }
            }
            val session = SessionRepository(transport, store); session.login("reader@example.test", "NativeTest123!")
            val cipher = PrivateCipher()
            val repository = org.athar.app.core.data.CounterRepository(db.dao(), cipher, session, automaticSync = false)
            repeat(3) { repository.tap(4, 3) }
            repository.tap(4, 3, reset = true)
            repeat(3) { repository.tap(4, 3) }
            repository.close()
            val restored = org.athar.app.core.data.CounterRepository(db.dao(), cipher, session, automaticSync = false)
            assertEquals(3, restored.observe(1, 4, 3).first().count)
            restored.sync()
            assertEquals(2, events.size)
            assertTrue(restored.observe(1, 4, 3).first().events.isEmpty())
            assertFalse(restored.observe(1, 4, 3).first().pending)
            assertEquals(0, restored.observe(2, 4, 3).first().count)
            restored.close()
        } finally { db.close() }
    }
}
