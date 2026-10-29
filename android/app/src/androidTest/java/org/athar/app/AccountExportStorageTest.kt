package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.SessionRepository
import org.athar.app.books.*
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

class AccountExportStorageTest {
    @Test fun snapshotIncludesUnsyncedProtectedStateAndAllMutationPagesButNeverPrivateExemptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = object : TokenStore {
            var value: StoredSession? = null
            override suspend fun load() = value
            override suspend fun save(session: StoredSession) { value = session }
            override suspend fun clear() { value = null }
        }
        val transport = object : ApiTransport {
            override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                check(path == "auth/login")
                val id = if (body["email"] == "two@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
            }
        }
        val session = SessionRepository(transport, store); session.login("one@example.test", "Fixture123!")
        val cipher = PrivateCipher(); val namespace = "123456abcdef"
        val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
        val reading = ReadingRepository(db.dao(), cipher, session, { index }, automaticSync = false)
        val lessons = LessonRepository(db.dao(), cipher, session, automaticSync = false)
        val counters = CounterRepository(db.dao(), cipher, session, automaticSync = false)
        val night = NightPrayerRepository(db, session, cipher)
        val snapshot = AccountLocalSnapshot(namespace, db, cipher, session, lessons, night, reading, counters)
        try {
            reading.save(114, 6, 1); lessons.save(7, 10, 20, expectedOwner = 1)
            db.dao().cache(CachedDocument(1, "adhkar:9", cipher.seal(Gson().toJson(CounterState(9, 3, count = 2, pending = true, day = LocalDate.now().toString()))), 123))
            val digest = "a".repeat(64)
            val page = CachedDocument(1, BookReadingPayload.key(7, digest), cipher.seal(BookReadingPayload.encode(BookScope(namespace, 1), 7, digest, 19)), 123)
            db.dao().cache(page)
            db.dao().cache(CachedDocument(1, "book-info:7", cipher.seal("""{"namespace":"$namespace","owner":1,"book":7,"title":"عنوان خاص 😀"}"""), 123))
            db.dao().cache(CachedDocument(2, "book-info:7", "other-owner-unreadable-canary", 123))
            val activityId=UUID.randomUUID().toString()
            val activity=CachedDocument(1,"activity-completion:$activityId",cipher.seal(BookCompletionEvent(namespace,1,7,activityId,digest,LocalDate.now()).json().toString()),123)
            db.dao().cache(activity)
            db.dao().cache(activity.copy(userId=2,payloadCipher="other-owner-unreadable-activity"))
            val exemption = PrivateExemptions(1, "unreadable-private-exemption-canary")
            db.dao().exemptions(exemption)
            val id = UUID.randomUUID().toString()
            repeat(55) { number -> night.save(JsonObject().apply {
                addProperty("night_date", "2026-01-01"); addProperty("timezone", "Africa/Cairo")
                addProperty("entry_type", "manual"); addProperty("verse_count", 10); addProperty("note", "Private pending note $number")
            }, id, 1) }
            night.delete(id, 1)
            val device = snapshot.read(1)
            assertEquals(56, device.getAsJsonArray("device_night_mutations").size())
            assertTrue(device.getAsJsonArray("device_night_sessions")[0].asJsonObject["deleted"].asBoolean)
            assertTrue(device.getAsJsonObject("device_reading_position")["pending"].asBoolean)
            assertEquals(1, device.getAsJsonArray("device_lesson_progress").size())
            assertEquals(2, device.getAsJsonArray("device_adhkar_counters")[0].asJsonObject["count"].asInt)
            assertEquals(19, device.getAsJsonArray("device_book_positions")[0].asJsonObject["page"].asInt)
            assertEquals("عنوان خاص 😀", device.getAsJsonArray("device_book_inventory")[0].asJsonObject["title"].asString)
            assertEquals(activityId,device.getAsJsonArray("device_book_activity")[0].asJsonObject["event_id"].asString)
            assertEquals(1,device.getAsJsonArray("device_book_activity").size())
            assertFalse(device.toString().contains("exemption")); assertFalse(device.toString().contains("payloadCipher"))
            assertEquals(exemption, db.dao().exemptions(1))
            val broken = page.copy(payloadCipher = "unreadable-protected-book")
            db.dao().cache(broken)
            assertTrue(runCatching { snapshot.read(1) }.isFailure)
            assertEquals(broken, db.dao().cache(1, page.cacheKey))
            db.dao().cache(page)
            val unreadableActivity=activity.copy(payloadCipher="unreadable-owned-activity")
            db.dao().cache(unreadableActivity);assertTrue(runCatching{snapshot.read(1)}.isFailure)
            assertEquals(unreadableActivity,db.dao().cache(1,activity.cacheKey));db.dao().cache(activity)
            session.login("two@example.test", "Fixture123!")
            assertTrue(runCatching { snapshot.read(1) }.isFailure)
            assertEquals(page, db.dao().cache(1, page.cacheKey))
        } finally { reading.close(); lessons.close(); counters.close(); db.close() }
    }
}
