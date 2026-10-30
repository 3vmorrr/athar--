package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.athar.app.nightprayer.*
import org.athar.app.quran.QuranIndex
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.UUID

class NightReadingStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "night-reader-fixture-${UUID.randomUUID()}.db"
        val namespace = "123456abcdef"; val cipher = PrivateCipher(); val tokens = Store()
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val index = QuranIndex.fromJson(context.assets.open("quran/index.json").bufferedReader().use { it.readText() })
        private val api = object : ApiTransport {
            override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                paths.add(path); check(path == "auth/login")
                val id = if (body["email"] == "two@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
            }
        }
        private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open(); var session = SessionRepository(api, tokens)
        fun nights() = NightPrayerRepository(db, session, cipher)
        fun plans() = NightVersePlanRepository(namespace, db, cipher, session, nights(), index = { index })
        fun reader() = NightReadingRepository(namespace, db, cipher, session, nights())
        suspend fun login(owner: Long) = session.login(if (owner == 2L) "two@example.test" else "one@example.test", "Fixture123!")
        suspend fun plan(owner: Long, count: Int = 100): NightPlanState = plans().save(session.lease(owner), index.target(1, 1, count), 0)
        fun scope(state: NightPlanState) = state.plan!!.let { NightReadingScope(state.owner, namespace, "plan", it.id, it.startIndex, it.endIndex) }
        fun body(start: Int, end: Int) = JsonObject().apply {
            addProperty("night_date", LocalDate.now(ZoneOffset.UTC).toString()); addProperty("timezone", "UTC")
            addProperty("boundary_source", "manual"); addProperty("entry_type", "range")
            addProperty("start_index", start); addProperty("end_index", end); addProperty("verse_count", end - start + 1); addProperty("note", "fixture")
        }
        fun reopen() { db.close(); db = open(); session = SessionRepository(api, tokens) }
        override fun close() { db.close(); check(name.startsWith("night-reader-fixture-")); context.deleteDatabase(name) }
    }
    @Test fun privateMarkerAndClearSurviveReopenWithoutTouchingActualQuranKhatmahOrHistory() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val scope = f.scope(f.plan(1)); val reader = f.reader()
            val quran = CachedDocument(1, "quran-reading-position", "actual-quran-key-unreadable-canary", 1)
            val khatmah = CachedDocument(1, "private:khatmah-journal:v3", "actual-khatmah-key-unreadable-canary", 1)
            f.db.dao().cache(quran); f.db.dao().cache(khatmah)
            assertEquals(1, reader.read(lease, scope).initialIndex)
            val saved = reader.save(lease, scope, 7, 0)
            assertEquals(7, saved.position.index); assertEquals(1L, saved.position.version)
            assertEquals(quran, f.db.dao().cache(1, quran.cacheKey)); assertEquals(khatmah, f.db.dao().cache(1, khatmah.cacheKey))
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
            f.reopen(); f.login(1); val cold = f.reader(); val newLease = f.session.lease(1)
            assertEquals(saved, cold.read(newLease, scope))
            val cleared = cold.clear(newLease, scope, 1); assertNull(cleared.position.index); assertEquals(2L, cleared.position.version)
            assertEquals(409, (runCatching { cold.save(newLease, scope, 8, 1) }.exceptionOrNull() as ApiException).status)
            assertTrue(f.paths.all { it == "auth/login" }); assertEquals(quran, f.db.dao().cache(1, quran.cacheKey))
        } finally { f.close() }
    }
    @Test fun sessionReaderUsesAnIndependentScopeAndDeletedHistoryCannotBeResurrected() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val id = "12345678-1234-1234-8234-123456789abc"
            f.nights().save(f.body(7, 8), id, 1)
            val scope = NightReadingScope(1, f.namespace, "session", id, 7, 8); val reader = f.reader()
            val before = f.db.dao().night(1, id); val mutations = f.db.dao().allMutations(1)
            reader.save(lease, scope, 8, 0)
            assertEquals(before, f.db.dao().night(1, id)); assertEquals(mutations, f.db.dao().allMutations(1))
            val marker = f.db.dao().cache(1, scope.key)
            f.nights().delete(id, 1)
            assertEquals(404, (runCatching { reader.save(lease, scope, 7, 1) }.exceptionOrNull() as ApiException).status)
            assertTrue(f.db.dao().night(1, id)!!.deleted); assertEquals(marker, f.db.dao().cache(1, scope.key))
        } finally { f.close() }
    }
    @Test fun replacingOrClearingAPlanRemovesOnlyItsOwnedMarkerAndFencesOldCallbacks() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val plans = f.plans(); val state = f.plan(1); val oldScope = f.scope(state); val reader = f.reader()
            reader.save(lease, oldScope, 7, 0)
            val next = plans.save(lease, f.index.target(2, 1, 10), state.version)
            assertNull(f.db.dao().cache(1, oldScope.key))
            assertEquals(409, (runCatching { reader.save(lease, oldScope, 8, 0) }.exceptionOrNull() as ApiException).status)
            val newScope = f.scope(next); reader.save(lease, newScope, newScope.start, 0)
            plans.clear(lease, next.version); assertNull(f.db.dao().cache(1, newScope.key))
            assertTrue(runCatching { reader.save(lease, newScope, newScope.start, 0) }.isFailure)
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
        } finally { f.close() }
    }
    @Test fun unreadableMarkerCannotBeErasedByClearingItsPlan() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val state = f.plan(1); val scope = f.scope(state)
            val broken = CachedDocument(1, scope.key, "broken-protected-marker", 1); f.db.dao().cache(broken)
            val planBytes = f.db.dao().cache(1, NightPlanCodec.KEY)
            assertTrue(runCatching { f.plans().clear(lease, state.version) }.isFailure)
            assertEquals(planBytes, f.db.dao().cache(1, NightPlanCodec.KEY)); assertEquals(broken, f.db.dao().cache(1, scope.key))
        } finally { f.close() }
    }
    @Test fun editedSessionRangeNeedsAnExplicitReviewedMarkerReset() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val id = UUID.randomUUID().toString(); val reader = f.reader()
            f.nights().save(f.body(1, 7), id, 1); val old = NightReadingScope(1, f.namespace, "session", id, 1, 7)
            val saved = reader.save(lease, old, 3, 0)
            f.nights().save(f.body(2, 8), id, 1); val current = old.copy(start = 2, end = 8)
            assertEquals(409, (runCatching { reader.save(lease, old, 4, 1) }.exceptionOrNull() as ApiException).status)
            val changed = runCatching { reader.read(lease, current) }.exceptionOrNull() as NightPositionRangeChanged
            assertEquals(saved.position, changed.retained)
            val reset = reader.resetChangedRange(lease, current, changed.retained.version)
            assertEquals(2L, reset.position.version); assertNull(reset.position.index)
            assertEquals(8, reader.save(lease, current, 8, 2).position.index)
        } finally { f.close() }
    }
    @Test fun twoReaderInstancesCannotOverwriteOneObservedMarkerVersion() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val scope = f.scope(f.plan(1)); val reader = f.reader()
            val outcomes = listOf(7, 8).map { position -> async(Dispatchers.Default) { runCatching { reader.save(lease, scope, position, 0) } } }.awaitAll()
            assertEquals(1, outcomes.count { it.isSuccess }); assertEquals(1, outcomes.count { (it.exceptionOrNull() as? ApiException)?.status == 409 })
            assertEquals(1L, reader.read(lease, scope).position.version)
        } finally { f.close() }
    }
    @Test fun foreignMarkerCannotBeReadWrittenOrPartiallyExported() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val a = f.scope(f.plan(1)); f.reader().save(f.session.lease(1), a, 7, 0); val aBytes = f.db.dao().cache(1, a.key)!!
            f.login(2); val b = f.scope(f.plan(2)); val swapped = aBytes.copy(userId = 2, cacheKey = b.key); f.db.dao().cache(swapped)
            val lease = f.session.lease(2); val reader = f.reader()
            assertTrue(runCatching { reader.read(lease, b) }.isFailure); assertTrue(runCatching { reader.save(lease, b, 8, 0) }.isFailure)
            assertTrue(runCatching { retainedNightReading(2, f.namespace, f.db.dao(), f.cipher) }.isFailure)
            assertEquals(swapped, f.db.dao().cache(2, b.key)); assertEquals(aBytes, f.db.dao().cache(1, a.key))
        } finally { f.close() }
    }
    @Test fun generationAndDeletionFencesProtectMarkersAndPurgeLeavesAnotherActorUntouched() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); val b = f.scope(f.plan(2)); f.reader().save(f.session.lease(2), b, 7, 0); val bBytes = f.db.dao().cache(2, b.key)
            f.login(1); val scope = f.scope(f.plan(1)); val old = f.session.lease(1); f.login(1); val reader = f.reader()
            assertTrue(runCatching { reader.save(old, scope, 7, 0) }.isFailure); assertNull(f.db.dao().cache(1, scope.key))
            val lease = f.session.lease(1); reader.save(lease, scope, 7, 0)
            AccountDeletionStore(f.namespace, f.db, f.cipher).prepare(1) { f.session.requireLease(lease) }
            assertTrue(runCatching { reader.clear(lease, scope, 1) }.exceptionOrNull() is AccountDeletionPending)
            f.db.dao().deleteUser(1); assertNull(f.db.dao().cache(1, scope.key)); assertEquals(bBytes, f.db.dao().cache(2, b.key))
        } finally { f.close() }
    }
    @Test fun onlyCurrentPlanReceiptsAndCurrentRecordedRangesAffectContinuation() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val reader = f.reader(); val plans = f.plans()
            val first = f.plan(1); val firstScope = f.scope(first)
            f.nights().save(f.body(1, 200), UUID.randomUUID().toString(), 1) // Unrelated older history.
            assertEquals(1, reader.read(lease, firstScope).firstUncovered)
            plans.record(lease, first.version, first.plan!!, UUID.randomUUID().toString(), LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, "")
            assertTrue(reader.read(lease, firstScope).recordedComplete)
            val next = plans.save(lease, f.index.target(1, 1, 200), first.version); val nextScope = f.scope(next)
            assertEquals(1, reader.read(lease, nextScope).firstUncovered)
            val operation = UUID.randomUUID().toString()
            plans.record(lease, next.version, next.plan!!, operation, LocalDate.now(ZoneOffset.UTC), ZoneOffset.UTC, "")
            assertNull(reader.read(lease, nextScope).firstUncovered)
            f.nights().save(f.body(1, 50), operation, 1)
            assertEquals(51, reader.read(lease, nextScope).firstUncovered)
            f.nights().delete(operation, 1); assertEquals(1, reader.read(lease, nextScope).firstUncovered)
        } finally { f.close() }
    }
    @Test fun sourceChangeTerminatesObservationInsteadOfPublishingAFakeEmptyMarker() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val state = f.plan(1); val scope = f.scope(state); val started = CompletableDeferred<Unit>()
            val watch = async { runCatching { f.reader().observe(lease, scope).collect { started.complete(Unit) } } }
            try {
                withTimeout(10000) { started.await() }; f.plans().clear(lease, state.version)
                val result = withTimeout(10000) { watch.await() }
                assertTrue(result.exceptionOrNull() is ApiException)
            } finally { watch.cancelAndJoin() }
        } finally { f.close() }
    }
}
