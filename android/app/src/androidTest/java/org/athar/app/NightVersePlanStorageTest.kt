package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.athar.app.nightprayer.*
import org.athar.app.quran.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.*
import java.util.UUID

class NightVersePlanStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "night-plan-fixture-${UUID.randomUUID()}.db"
        val namespace = "123456abcdef"
        val cipher = PrivateCipher(); val tokens = Store()
        val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
        val transport = object : ApiTransport {
            override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                check(path == "auth/login")
                val id = if (body["email"] == "two@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
            }
        }
        private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open(); var session = SessionRepository(transport, tokens)
        fun nights(sync: (Long) -> Unit = {}) = NightPrayerRepository(db, session, cipher, sync)
        fun repository(indexSource: suspend () -> QuranIndex = { index }, sync: (Long) -> Unit = {}) = NightVersePlanRepository(namespace, db, cipher, session, nights(sync), indexSource)
        suspend fun login(owner: Long) = session.login(if (owner == 2L) "two@example.test" else "one@example.test", "Fixture123!")
        suspend fun row(owner: Long) = db.dao().cache(owner, NightPlanCodec.KEY)
        fun reopen() { db.close(); db = open(); session = SessionRepository(transport, tokens) }
        override fun close() { db.close(); check(name.startsWith("night-plan-fixture-")); context.deleteDatabase(name) }
    }
    @Test fun savedAndClearedPlanSurviveRoomReopenWithoutFabricatingAnyHistoryOrProgress() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            val position = CachedDocument(1, "reading-position", "unreadable-canary-position", 1)
            val khatmah = CachedDocument(1, "private:khatmah-journal:v3", "unreadable-canary-khatmah", 1)
            f.db.dao().cache(position); f.db.dao().cache(khatmah)
            val saved = repo.save(lease, f.index.target(1, 7, 100), 0)
            assertFalse(f.row(1)!!.payloadCipher.contains("start_index"))
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
            f.db.dao().trimContentCache(1, Long.MAX_VALUE / 2, ContentCachePolicy(maxEntries = 1, maxBytes = 64, maxEntryBytes = 64, maxAgeMillis = 1))
            assertEquals(saved, repo.read(lease)); assertEquals(position, f.db.dao().cache(1, position.cacheKey)); assertEquals(khatmah, f.db.dao().cache(1, khatmah.cacheKey))
            f.reopen(); f.login(1); val cold = f.repository(); val newLease = f.session.lease(1)
            assertEquals(saved, cold.read(newLease))
            val cleared = cold.clear(newLease, saved.version); assertNull(cleared.plan)
            assertEquals(2L, cleared.version)
            assertEquals(409, (runCatching { cold.save(newLease, f.index.target(1, 1, 10), saved.version) }.exceptionOrNull() as ApiException).status)
            f.reopen(); f.login(1); assertEquals(cleared, f.repository().read(f.session.lease(1)))
        } finally { f.close() }
    }
    @Test fun concurrentFormsCannotBothCommitTheSameObservedVersion() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            val results = listOf(100, 101).map { count -> async(Dispatchers.Default) { runCatching { repo.save(lease, f.index.target(1, 1, count), 0) } } }.awaitAll()
            assertEquals(1, results.count { it.isSuccess }); assertEquals(1, results.count { (it.exceptionOrNull() as? ApiException)?.status == 409 })
            assertEquals(1L, repo.read(lease).version)
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
        } finally { f.close() }
    }
    @Test fun foreignUnreadableFramesCannotBeOverwrittenClearedOrPartiallyExported() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); f.repository().save(f.session.lease(1), f.index.target(1, 1, 10), 0)
            val a = f.row(1)!!; f.login(2); val swapped = a.copy(userId = 2); f.db.dao().cache(swapped)
            val repo = f.repository(); val lease = f.session.lease(2)
            assertEquals(ProtectedRead.Unreadable, repo.observe(lease).first())
            assertTrue(runCatching { repo.save(lease, f.index.target(1, 1, 20), 1) }.isFailure)
            assertTrue(runCatching { repo.clear(lease, 1) }.isFailure)
            assertTrue(runCatching { retainedNightPlans(2, f.namespace, f.db.dao(), f.cipher) }.isFailure)
            assertEquals(swapped, f.row(2)); assertEquals(a, f.row(1))
        } finally { f.close() }
    }
    @Test fun capturedGenerationBeforeAndDuringSaveCannotBorrowAnotherLogin() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repository().save(f.session.lease(2), f.index.target(2, 1, 20), 0); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            f.login(1) // Same owner, different generation.
            assertTrue(runCatching { repo.save(lease, f.index.target(1, 1, 10), 0) }.isFailure); assertNull(f.row(1))
            val current = f.session.lease(1)
            val switching = f.repository(indexSource = { f.login(2); f.index })
            assertTrue(runCatching { switching.save(current, f.index.target(1, 1, 10), 0) }.isFailure)
            assertNull(f.row(1)); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun confirmedRecordingIsAtomicAndRetryCannotDuplicateOrResurrectDeletedHistory() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            val state = repo.save(lease, f.index.target(1, 7, 100), 0); val plan = state.plan!!
            val operation = UUID.randomUUID().toString(); val day = LocalDate.now(ZoneOffset.UTC)
            repo.record(lease, state.version, plan, operation, day, ZoneOffset.UTC, "private-note-canary")
            repo.record(lease, state.version, plan, operation, day, ZoneOffset.UTC, "private-note-canary")
            assertEquals(1, f.db.dao().allNights(1).size); assertEquals(1, f.db.dao().allMutations(1).size)
            assertEquals(100, f.db.dao().night(1, operation)!!.verseCount)
            val export = retainedNightPlans(1, f.namespace, f.db.dao(), f.cipher)
            assertFalse(export.toString().contains("private-note-canary")); assertEquals(1, export.getAsJsonArray("device_night_plan_records").size())
            f.nights().delete(operation, 1)
            repo.clear(lease, state.version)
            repo.record(lease, state.version, plan, operation, day, ZoneOffset.UTC, "private-note-canary")
            assertTrue(f.db.dao().night(1, operation)!!.deleted); assertEquals(2, f.db.dao().allMutations(1).size)
            assertEquals(409, (runCatching { repo.record(lease, state.version, plan, operation, day, ZoneOffset.UTC, "different intent") }.exceptionOrNull() as ApiException).status)
        } finally { f.close() }
    }
    @Test fun failedPostWriteDispatchRollsBackSessionOutboxAndReceiptTogether() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository(sync = { throw IOException("fixture dispatch failure") })
            val state = repo.save(lease, f.index.target(1, 1, 10), 0); val before = f.row(1)
            val operation = UUID.randomUUID().toString()
            assertTrue(runCatching { repo.record(lease, state.version, state.plan!!, operation, LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, "") }.exceptionOrNull() is IOException)
            assertNull(f.db.dao().night(1, operation)); assertTrue(f.db.dao().allMutations(1).isEmpty())
            assertNull(f.db.dao().cache(1, NightPlanCodec.RECORD_PREFIX + operation)); assertEquals(before, f.row(1))
        } finally { f.close() }
    }
    @Test fun actorSwitchAfterSessionWriteRollsBackAllADataAndLeavesBUntouched() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repository().save(f.session.lease(2), f.index.target(2, 1, 20), 0); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1)
            val repo = f.repository(sync = { runBlocking { f.login(2) } })
            val state = repo.save(lease, f.index.target(1, 1, 10), 0); val before = f.row(1)
            val operation = UUID.randomUUID().toString()
            assertTrue(runCatching { repo.record(lease, state.version, state.plan!!, operation, LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, "") }.isFailure)
            assertNull(f.db.dao().night(1, operation)); assertTrue(f.db.dao().allMutations(1).isEmpty())
            assertNull(f.db.dao().cache(1, NightPlanCodec.RECORD_PREFIX + operation)); assertEquals(before, f.row(1))
            assertEquals(2L, f.session.userId); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun deletionFenceBlocksPlanMutationsAndOwnedPurgePreservesAnotherActor() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repository().save(f.session.lease(2), f.index.target(2, 1, 20), 0); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1); val repo = f.repository(); val state = repo.save(lease, f.index.target(1, 1, 10), 0)
            val before = f.row(1); AccountDeletionStore(f.namespace, f.db, f.cipher).prepare(1) { f.session.requireLease(lease) }
            assertTrue(runCatching { repo.save(lease, f.index.target(1, 1, 20), state.version) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { repo.clear(lease, state.version) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { repo.record(lease, state.version, state.plan!!, UUID.randomUUID().toString(), LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, "") }.exceptionOrNull() is AccountDeletionPending)
            assertEquals(before, f.row(1)); f.db.dao().deleteUser(1); assertNull(f.row(1)); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun canonicalIndexMismatchCannotCreateAPlan() = runBlocking {
        val f = Fixture()
        try {
            f.login(1)
            val forged = QuranRange(QuranPosition(1, 1, 2), QuranPosition(1, 2, 3))
            assertTrue(runCatching { f.repository().save(f.session.lease(1), forged, 0) }.exceptionOrNull() is IllegalArgumentException)
            assertNull(f.row(1))
        } finally { f.close() }
    }
}
