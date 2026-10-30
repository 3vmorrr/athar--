package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.UUID

class NightPreferencesStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "night-preferences-fixture-${UUID.randomUUID()}.db"
        val namespace = "123456abcdef"; val cipher = PrivateCipher(); val tokens = Store()
        val cloud = mutableMapOf(1L to NightPreferencesEnvelope(1, NightPreferenceValues("Riyadh", "Asia/Riyadh")))
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        var online = true; var getError: Int? = null; var malformed: JsonElement? = null; var wrongAcknowledgement = false
        var beforeGetResponse: (suspend () -> Unit)? = null
        var afterPut: (suspend () -> Unit)? = null
        val api = object : ApiTransport {
            override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
                calls.add("$method $path")
                if (path == "auth/login") {
                    val id = if (body["email"] == "two@example.test") 2 else 1
                    return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
                }
                check(path == "night-prayer/preferences"); if (!online) throw IOException("fixture offline")
                val owner = if (bearer == "a2") 2L else 1L
                if (method == "GET") {
                    getError?.let { throw ApiException(it, "fixture_get_error", "") }
                    val reply = malformed?.deepCopy() ?: (cloud[owner] ?: NightPreferencesEnvelope(0, NightPreferenceValues())).json()
                    beforeGetResponse?.invoke(); return reply
                }
                check(method == "PUT"); assertTrue(body["expected_revision"] is Long)
                val before = cloud[owner] ?: NightPreferencesEnvelope(0, NightPreferenceValues())
                val expected = (body["expected_revision"] as Number).toLong()
                if (expected != before.revision) throw ApiException(409, "conflict", "")
                val values = NightPreferenceValues(body["city"] as String, body["timezone"] as String,
                    (body["latitude"] as Number).toDouble(), (body["longitude"] as Number).toDouble(), body["calculation_method"] as String,
                    body["madhab"] as String, body["high_latitude_rule"] as String)
                val next = NightPreferencesEnvelope(expected + 1, values); cloud[owner] = next
                afterPut?.invoke()
                return if (wrongAcknowledgement) next.copy(revision = next.revision + 1).json() else next.json()
            }
        }
        private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open(); var session = SessionRepository(api, tokens)
        fun repo(changed: suspend () -> Unit = {}) = NightPreferencesRepository(namespace, db, cipher, session, onChanged = { changed() }, now = { Instant.parse("2026-10-30T04:00:00Z") })
        fun complete() = NightPreferenceValues("Cairo", "Africa/Cairo", 30.0444, 31.2357, "EGYPTIAN", "SHAFI", "MIDDLE_OF_THE_NIGHT")
        suspend fun login(owner: Long) = session.login(if (owner == 2L) "two@example.test" else "one@example.test", "Fixture123!")
        suspend fun row(owner: Long) = db.dao().cache(owner, NightPreferencesCodec.KEY)
        fun reopen() { db.close(); db = open(); session = SessionRepository(api, tokens) }
        override fun close() { db.close(); check(name.startsWith("night-preferences-fixture-")); context.deleteDatabase(name) }
    }
    @Test fun partialPreferencesCanBeCompletedAndReopenedOfflineWithoutInventingOrChangingHistory() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo()
            val quran = CachedDocument(1, "quran-reading-position", "private-quran-canary", 1)
            val khatmah = CachedDocument(1, "private:khatmah-journal:v3", "private-khatmah-canary", 1)
            f.db.dao().cache(quran); f.db.dao().cache(khatmah)
            val partial = repo.load(lease); assertNull(partial.snapshot.account.values.latitude); assertFalse(partial.offline)
            val saved = repo.save(lease, partial.snapshot.account, f.complete()); assertEquals(2L, saved.snapshot.account.revision)
            assertEquals(f.complete(), saved.snapshot.account.values)
            f.reopen(); f.login(1); f.online = false
            val offline = f.repo().load(f.session.lease(1)); assertTrue(offline.offline); assertEquals(saved.snapshot, offline.snapshot)
            assertEquals(quran, f.db.dao().cache(1, quran.cacheKey)); assertEquals(khatmah, f.db.dao().cache(1, khatmah.cacheKey))
            assertTrue(f.db.dao().allNights(1).isEmpty()); assertTrue(f.db.dao().allMutations(1).isEmpty())
        } finally { f.close() }
    }
    @Test fun delayedOlderGetCannotOverwriteAConfirmedNewerPreferenceRevision() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(); val original = repo.load(lease)
            val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            f.beforeGetResponse = { started.complete(Unit); release.await() }
            val old = async { repo.load(lease) }; started.await(); f.beforeGetResponse = null
            val saved = repo.save(lease, original.snapshot.account, f.complete()); release.complete(Unit)
            val late = old.await(); assertEquals(saved.snapshot.account, late.snapshot.account); assertTrue(late.newerCachedRevision)
            assertEquals(saved.snapshot.account, repo.cached(lease)!!.account)
        } finally { f.close() }
    }
    @Test fun staleServerRevisionAndMismatchedAcknowledgementRetainThePriorConfirmedCopy() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(); val first = repo.load(lease); val before = f.row(1)
            f.cloud[1L] = NightPreferencesEnvelope(2, f.complete().copy(city = "Other device"))
            assertEquals(409, (runCatching { repo.save(lease, first.snapshot.account, f.complete()) }.exceptionOrNull() as ApiException).status)
            assertEquals(before, f.row(1))
            val current = repo.load(lease); val currentBytes = f.row(1); f.wrongAcknowledgement = true
            assertTrue(runCatching { repo.save(lease, current.snapshot.account, f.complete()) }.exceptionOrNull() is IOException)
            assertEquals(currentBytes, f.row(1)); assertEquals(3L, f.cloud[1L]!!.revision)
        } finally { f.close() }
    }
    @Test fun capturedSameOwnerGenerationCannotBorrowReauthenticatedCredentials() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val old = f.session.lease(1); val original = f.repo().load(old); f.login(1)
            val before = f.row(1); val count = f.calls.size
            assertTrue(runCatching { f.repo().load(old) }.isFailure)
            assertTrue(runCatching { f.repo().save(old, original.snapshot.account, f.complete()) }.isFailure)
            assertEquals(count, f.calls.size); assertEquals(before, f.row(1))
        } finally { f.close() }
    }
    @Test fun corruptOfflineCopyIsRetainedAndOnlyReviewedCurrentAccountCopyCanReplaceIt() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo()
            val broken = CachedDocument(1, NightPreferencesCodec.KEY, "unreadable-private-preferences", 1); f.db.dao().cache(broken)
            f.online = false; assertTrue(runCatching { repo.load(lease) }.isFailure); assertEquals(broken, f.row(1))
            f.online = true; val offered = repo.load(lease); val review = offered.repair!!
            assertEquals(broken, f.row(1)); val calls = f.calls.size
            assertTrue(runCatching { repo.save(lease, offered.snapshot.account, f.complete()) }.isFailure)
            assertEquals(calls, f.calls.size)
            val repaired = repo.useAccountCopy(lease, review)
            assertNull(repaired.repair); assertEquals(offered.snapshot.account, repo.cached(lease)!!.account)
            assertFalse(f.calls.any { it == "PUT night-prayer/preferences" })
        } finally { f.close() }
    }
    @Test fun changedAccountOrChangedLocalBytesInvalidateAnOldRepairConfirmation() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo()
            val first = CachedDocument(1, NightPreferencesCodec.KEY, "first-broken-copy", 1); f.db.dao().cache(first)
            val proof = repo.load(lease).repair!!
            f.cloud[1L] = NightPreferencesEnvelope(2, f.complete())
            assertEquals(409, (runCatching { repo.useAccountCopy(lease, proof) }.exceptionOrNull() as ApiException).status)
            assertEquals(first, f.row(1)); val newProof = repo.load(lease).repair!!
            val changed = first.copy(payloadCipher = "second-broken-copy"); f.db.dao().cache(changed)
            assertEquals(409, (runCatching { repo.useAccountCopy(lease, newProof) }.exceptionOrNull() as ApiException).status)
            assertEquals(changed, f.row(1))
        } finally { f.close() }
    }
    @Test fun equalRevisionDivergenceNeedsExplicitReviewAndCannotBeSavedOver() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(); val first = repo.load(lease); val before = f.row(1)
            f.cloud[1L] = first.snapshot.account.copy(values = first.snapshot.account.values.copy(city = "Account copy"))
            val offered = repo.load(lease); assertNotNull(offered.repair); assertEquals(before, f.row(1))
            assertEquals(409, (runCatching { repo.save(lease, offered.snapshot.account, f.complete()) }.exceptionOrNull() as ApiException).status)
            assertEquals(before, f.row(1)); repo.useAccountCopy(lease, offered.repair!!)
            assertEquals(f.cloud[1L], repo.cached(lease)!!.account)
        } finally { f.close() }
    }
    @Test fun actorChangeDuringPutCannotPublishACopyIntoAnotherActorsStorage() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repo().load(f.session.lease(2)); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1); val first = f.repo().load(lease); val a = f.row(1)
            f.afterPut = { f.login(2) }
            assertTrue(runCatching { f.repo().save(lease, first.snapshot.account, f.complete()) }.isFailure)
            assertEquals(2L, f.session.userId); assertEquals(a, f.row(1)); assertEquals(b, f.row(2))
            assertEquals(f.complete(), f.cloud[1L]!!.values) // Already dispatched request may commit for A; never borrow B.
        } finally { f.close() }
    }
    @Test fun deletionFenceBlocksPutAndOwnedCleanupLeavesAnotherActorsCopyUntouched() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repo().load(f.session.lease(2)); val b = f.row(2)
            f.login(1); val lease = f.session.lease(1); val first = f.repo().load(lease); val calls = f.calls.size
            AccountDeletionStore(f.namespace, f.db, f.cipher).prepare(1) { f.session.requireLease(lease) }
            assertTrue(runCatching { f.repo().save(lease, first.snapshot.account, f.complete()) }.exceptionOrNull() is AccountDeletionPending)
            assertEquals(calls, f.calls.size); f.db.dao().deleteUser(1); assertNull(f.row(1)); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun foreignCopyAndMalformedServerDataNeverBecomeAnEmptyPreferenceOrPartialExport() = runBlocking {
        val f = Fixture()
        try {
            f.login(2); f.repo().load(f.session.lease(2)); val b = f.row(2)!!
            f.login(1); val lease = f.session.lease(1); val foreign = b.copy(userId = 1); f.db.dao().cache(foreign)
            assertTrue(runCatching { retainedNightPreferences(1, f.namespace, f.db.dao(), f.cipher) }.isFailure)
            val offered = f.repo().load(lease); assertNotNull(offered.repair); assertEquals(f.cloud[1L], offered.snapshot.account)
            assertEquals(foreign, f.row(1)); assertEquals(b, f.row(2))
            f.malformed = JsonParser.parseString("""{"revision":1}""")
            assertTrue(runCatching { f.repo().load(lease) }.isFailure); assertEquals(foreign, f.row(1))
            f.malformed = null; f.getError = 404
            assertEquals(404, (runCatching { f.repo().load(lease) }.exceptionOrNull() as ApiException).status)
            assertEquals(foreign, f.row(1))
        } finally { f.close() }
    }
    @Test fun postAcknowledgementFailureKeepsConfirmedCacheAndCannotRepeatTheOldPut() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(changed = { throw IOException("fixture invalidation failure") })
            val first = repo.load(lease)
            assertTrue(runCatching { repo.save(lease, first.snapshot.account, f.complete()) }.exceptionOrNull() is IOException)
            assertEquals(f.cloud[1L], repo.cached(lease)!!.account)
            val puts = f.calls.count { it == "PUT night-prayer/preferences" }
            assertEquals(409, (runCatching { repo.save(lease, first.snapshot.account, f.complete()) }.exceptionOrNull() as ApiException).status)
            assertEquals(puts, f.calls.count { it == "PUT night-prayer/preferences" })
        } finally { f.close() }
    }
    @Test fun lowerAccountRevisionCannotSilentlyRollbackTheCopyButCanBeExplicitlyReviewed() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(); val original = repo.load(lease)
            val saved = repo.save(lease, original.snapshot.account, f.complete()); val before = f.row(1)
            f.cloud[1L] = original.snapshot.account // Simulated account restore, not a device-clock guess.
            val result = repo.load(lease); assertTrue(result.newerCachedRevision); assertNotNull(result.repair)
            assertEquals(saved.snapshot.account, result.snapshot.account); assertEquals(before, f.row(1))
            val reviewed = repo.useAccountCopy(lease, result.repair!!)
            assertEquals(original.snapshot.account, reviewed.snapshot.account); assertNull(reviewed.repair)
            assertEquals(original.snapshot.account, repo.cached(lease)!!.account)
        } finally { f.close() }
    }
    @Test fun ownedObservationPublishesConfirmedChangesAndFailsClosedOnForeignFrames() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val lease = f.session.lease(1); val repo = f.repo(); val original = repo.load(lease)
            val watching = async {
                withTimeout(10000) { repo.observe(lease).filter { it is ProtectedRead.Ready && it.value?.account?.revision == 2L }.first() }
            }
            val saved = repo.save(lease, original.snapshot.account, f.complete())
            val observed = watching.await() as ProtectedRead.Ready<NightPreferencesSnapshot?>
            assertEquals(saved.snapshot.account, observed.value!!.account)
            val before = f.row(1)!!
            val foreign = saved.snapshot.copy(owner = 2)
            f.db.dao().cache(before.copy(payloadCipher = f.cipher.seal(NightPreferencesCodec.encode(foreign))))
            val unreadable = withTimeout(10000) { repo.observe(lease).filter { it != ProtectedRead.Loading }.first() }
            assertEquals(ProtectedRead.Unreadable, unreadable)
        } finally { f.close() }
    }
}
