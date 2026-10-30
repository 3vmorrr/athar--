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

class NightTimerStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "night-timer-fixture-${UUID.randomUUID()}.db"
        val namespace = "123456abcdef"
        val cipher = PrivateCipher(); val tokens = Store()
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
        var clock = Instant.parse("2026-10-29T23:59:50Z")
        val contextWindow = NightContext(LocalDate.parse("2026-10-29"), ZoneOffset.UTC, Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-30T05:00:00Z"))
        val identity = NightIdentity(contextWindow.date, true, contextWindow)
        fun repository(sync: (Long) -> Unit = {}) = NightTimerRepository(namespace, db, cipher, session, nights(sync), now = { clock })
        suspend fun start(owner: Long = 1) = repository().start(session.lease(owner), 0, identity, "EGYPTIAN")
        suspend fun finished(): NightTimerFrame {
            val repo = repository(); val lease = session.lease(1); val first = start()
            clock = clock.plusSeconds(5)
            val checkpoint = repo.checkpoint(lease, first.version, first.timer!!, first.timer.checkpoint(5000, clock))
            return repo.finish(lease, checkpoint.version, checkpoint.timer!!)
        }
        fun entry() = JsonParser.parseString("""{"entry_type":"manual","verse_count":10,"note":"fixture timer note"}""").asJsonObject
        suspend fun login(owner: Long) = session.login(if (owner == 2L) "two@example.test" else "one@example.test", "Fixture123!")
        suspend fun row(owner: Long) = db.dao().cache(owner, NightTimerCodec.KEY)
        fun reopen() { db.close(); db = open(); session = SessionRepository(transport, tokens) }
        override fun close() { db.close(); check(name.startsWith("night-timer-fixture-")); context.deleteDatabase(name) }
    }
    @Test fun runningCheckpointRecoversPausedWithoutAddingWallTimeOrWritingHistory() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            val quran = CachedDocument(1, "quran-reading-position", "quran-private-canary", 1)
            val khatmah = CachedDocument(1, "private:khatmah-journal:v3", "khatmah-private-canary", 1)
            f.db.dao().cache(quran); f.db.dao().cache(khatmah)
            val first = f.start(); f.clock = f.clock.plusSeconds(5)
            repo.checkpoint(lease, first.version, first.timer!!, first.timer.checkpoint(5000, f.clock))
            f.reopen(); f.login(1); f.clock = f.clock.plusSeconds(600)
            val cold = f.repository(); val currentLease = f.session.lease(1); val recovered = cold.restore(currentLease)
            assertEquals(5000L, recovered.timer!!.millis); assertEquals("recovered", recovered.timer.reason)
            assertEquals("paused", recovered.timer.phase); assertEquals(3L, recovered.version)
            val resumed = cold.resume(currentLease, recovered.version, recovered.timer)
            assertEquals(5000L, resumed.timer!!.millis)
            val cleared = cold.clear(currentLease, resumed.version); assertNull(cleared.timer)
            assertEquals(quran, f.db.dao().cache(1, quran.cacheKey)); assertEquals(khatmah, f.db.dao().cache(1, khatmah.cacheKey))
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
            f.reopen(); f.login(1); assertEquals(cleared, f.repository().read(f.session.lease(1)))
        } finally { f.close() }
    }
    @Test fun concurrentStartsAndStaleClearCannotReplaceTheObservedTimer() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository()
            val outcomes = (1..2).map { async(Dispatchers.Default) { runCatching { repo.start(lease, 0, f.identity, "EGYPTIAN") } } }.awaitAll()
            assertEquals(1, outcomes.count { it.isSuccess }); assertEquals(1, outcomes.count { (it.exceptionOrNull() as? ApiException)?.status == 409 })
            val first = repo.read(lease); val before = f.row(1)
            assertEquals(409, (runCatching { repo.clear(lease, 0) }.exceptionOrNull() as ApiException).status)
            assertEquals(409, (runCatching { repo.start(lease, first.version, f.identity, "EGYPTIAN") }.exceptionOrNull() as ApiException).status)
            assertEquals(before, f.row(1))
        } finally { f.close() }
    }
    @Test fun foreignTimerCannotBeReadOverwrittenRestoredClearedOrExported() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); f.start(); val a = f.row(1)!!
            f.login(2); val swapped = a.copy(userId = 2); f.db.dao().cache(swapped)
            val repo = f.repository(); val lease = f.session.lease(2)
            assertTrue(runCatching { repo.read(lease) }.isFailure); assertTrue(runCatching { repo.restore(lease) }.isFailure)
            assertTrue(runCatching { repo.start(lease, 0, f.identity, "EGYPTIAN") }.isFailure)
            assertTrue(runCatching { repo.clear(lease, 1) }.isFailure)
            assertTrue(runCatching { retainedNightTimers(2, f.namespace, f.db.dao(), f.cipher) }.isFailure)
            assertEquals(ProtectedRead.Unreadable, repo.observe(lease).first { it != ProtectedRead.Loading })
            assertEquals(swapped, f.row(2)); assertEquals(a, f.row(1))
        } finally { f.close() }
    }
    @Test fun corruptTimerNeverBecomesAnEmptyWritableTimer() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val broken = CachedDocument(1, NightTimerCodec.KEY, "broken-private-timer", 1); f.db.dao().cache(broken)
            val lease = f.session.lease(1); val repo = f.repository()
            assertTrue(runCatching { repo.restore(lease) }.isFailure)
            assertTrue(runCatching { repo.start(lease, 0, f.identity, "EGYPTIAN") }.isFailure)
            assertTrue(runCatching { repo.clear(lease, 0) }.isFailure)
            assertEquals(broken, f.row(1)); assertTrue(f.db.dao().allNights(1).isEmpty())
        } finally { f.close() }
    }
    @Test fun capturedGenerationAndDeletionFenceProtectAllTimerWritesAndPurgePreservesB() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.start(2); val b = f.row(2)
            f.login(1); val old = f.session.lease(1); val first = f.start(); f.login(1)
            val repo = f.repository(); val before = f.row(1)
            assertTrue(runCatching { repo.clear(old, first.version) }.isFailure)
            assertTrue(runCatching { repo.restore(old) }.isFailure); assertEquals(before, f.row(1))
            val lease = f.session.lease(1); AccountDeletionStore(f.namespace, f.db, f.cipher).prepare(1) { f.session.requireLease(lease) }
            assertTrue(runCatching { repo.restore(lease) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { repo.clear(lease, first.version) }.exceptionOrNull() is AccountDeletionPending)
            f.db.dao().deleteUser(1); assertNull(f.row(1)); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun atomicRecordingRetriesCannotDuplicateResurrectDeletedHistoryOrClearALaterTimer() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository(); val finished = f.finished(); val timer = finished.timer!!
            assertTrue(f.db.dao().allNights(1).isEmpty())
            val cleared = repo.record(lease, finished.version, timer, f.entry()); assertNull(cleared.timer)
            val row = f.db.dao().night(1, timer.id)!!; assertEquals(5, row.measuredSeconds); assertEquals(10, row.verseCount)
            val before = f.db.dao().allMutations(1); assertEquals(1, before.size)
            assertEquals(cleared, repo.record(lease, finished.version, timer, f.entry()))
            assertEquals(before, f.db.dao().allMutations(1))
            val next = repo.start(lease, cleared.version, f.identity, "EGYPTIAN")
            assertEquals(next, repo.record(lease, finished.version, timer, f.entry()))
            f.nights().delete(timer.id, 1); val deleted = f.db.dao().night(1, timer.id); val mutations = f.db.dao().allMutations(1)
            repo.record(lease, finished.version, timer, f.entry())
            assertEquals(deleted, f.db.dao().night(1, timer.id)); assertEquals(mutations, f.db.dao().allMutations(1)); assertEquals(next, repo.read(lease))
            assertEquals(409, (runCatching { repo.record(lease, finished.version, timer, f.entry().apply { addProperty("note", "different") }) }.exceptionOrNull() as ApiException).status)
            val exported = retainedNightTimers(1, f.namespace, f.db.dao(), f.cipher)
            assertEquals(1, exported.getAsJsonArray("device_night_timer_records").size())
            assertFalse(exported.getAsJsonArray("device_night_timer_records").toString().contains("fixture timer note"))
        } finally { f.close() }
    }
    @Test fun dispatchFailureRollsBackTimerCleanupHistoryOutboxAndReceiptTogether() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val finished = f.finished(); val before = f.row(1); val timer = finished.timer!!
            val repo = f.repository(sync = { throw IOException("fixture dispatch failure") })
            assertTrue(runCatching { repo.record(lease, finished.version, timer, f.entry()) }.exceptionOrNull() is IOException)
            assertNull(f.db.dao().night(1, timer.id)); assertTrue(f.db.dao().allMutations(1).isEmpty())
            assertNull(f.db.dao().cache(1, NightTimerCodec.RECORD_PREFIX + timer.id)); assertEquals(before, f.row(1))
        } finally { f.close() }
    }
    @Test fun actorSwitchAfterHistoryWriteRollsBackAllTimerRecordingAndPreservesB() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.start(2); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1); val finished = f.finished(); val before = f.row(1); val timer = finished.timer!!
            val repo = f.repository(sync = { runBlocking { f.login(2) } })
            assertTrue(runCatching { repo.record(lease, finished.version, timer, f.entry()) }.isFailure)
            assertEquals(2L, f.session.userId); assertNull(f.db.dao().night(1, timer.id)); assertTrue(f.db.dao().allMutations(1).isEmpty())
            assertNull(f.db.dao().cache(1, NightTimerCodec.RECORD_PREFIX + timer.id)); assertEquals(before, f.row(1)); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun foreignOrCorruptRecordingReceiptCannotPermitTimerCleanup() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val finished = f.finished(); val before = f.row(1); val timer = finished.timer!!
            val receipt = NightTimerCodec.receipt(2, f.namespace, timer, timer.body(f.entry()))
            val foreign = CachedDocument(1, NightTimerCodec.RECORD_PREFIX + timer.id, f.cipher.seal(NightTimerCodec.encode(receipt)), 1)
            f.db.dao().cache(foreign)
            assertTrue(runCatching { f.repository().record(lease, finished.version, timer, f.entry()) }.isFailure)
            assertEquals(foreign, f.db.dao().cache(1, foreign.cacheKey)); assertEquals(before, f.row(1)); assertTrue(f.db.dao().allNights(1).isEmpty())
        } finally { f.close() }
    }
    @Test fun clockRollbackPausesAtVerifiedEndpointAndCannotSubmitFutureMeasuredHistory() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repository(); val first = f.start()
            f.clock = f.clock.plusSeconds(5)
            val checked = repo.checkpoint(lease, first.version, first.timer!!, first.timer.checkpoint(5000, f.clock))
            f.clock = f.clock.minusSeconds(3600)
            val pause = checked.timer!!.copy(phase = "paused", reason = "clock")
            val paused = repo.checkpoint(lease, checked.version, checked.timer, pause)
            assertEquals(5000L, paused.timer!!.millis)
            val finished = repo.finish(lease, paused.version, paused.timer)
            assertTrue(runCatching { repo.record(lease, finished.version, finished.timer!!, f.entry()) }.isFailure)
            assertEquals(finished, repo.read(lease)); assertTrue(f.db.dao().allNights(1).isEmpty())
        } finally { f.close() }
    }
}
