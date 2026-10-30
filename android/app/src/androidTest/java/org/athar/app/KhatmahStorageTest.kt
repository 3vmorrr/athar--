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
import org.athar.app.khatmah.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

/** Real Room and Android Keystore; deterministic transport does not stand in for real HTTP acceptance. */
class KhatmahStorageTest {
    private val day = LocalDate.of(2026, 10, 29)
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private inner class Server : ApiTransport {
        val plans = mutableMapOf<Long, KhatmahPlan>()
        var loseReply = false
        var hold = false
        var mutations = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun request(method: String, path: String, body: Map<String, Any?>, query: Map<String, String>, bearer: String?, language: String): JsonElement {
            if (path == "auth/login") {
                val owner = if (body["email"] == "two@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$owner,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$owner","access_token":"a$owner","refresh_token":"r$owner","expires_in":900,"scope":"full"}}""")
            }
            require(path == "me/khatmah" || path == "me/khatmah/action")
            val owner = when (bearer) { "a1" -> 1L; "a2" -> 2L; else -> error("Unexpected actor") }
            val before = plans[owner]
            if (method == "GET") return json(before)
            val expected = (body.getValue("expected_revision") as Number).toInt()
            val desired = KhatmahJournal.applyIntent(before, KhatmahIntent(UUID.randomUUID().toString(), if (method == "PUT") "save" else body.getValue("action").toString(), if (method == "PUT" || body["action"] == "restart") KhatmahDraft(body.getValue("start_date").toString(), body.getValue("target_date").toString(), body["completed_pages"]?.toString() ?: "0") else null))
            val replay = before != null && before.revision == expected + 1 && KhatmahJournal.sameValues(before, desired)
            if (!replay) {
                if (expected != (before?.revision ?: 0) || body["expected_plan_hash"] != KhatmahContract.fingerprint(owner, before)) throw ApiException(409, "fixture_conflict", "")
                plans[owner] = desired.copy(revision = expected + 1); mutations++
            }
            if (hold) { started.complete(Unit); release.await() }
            if (loseReply) { loseReply = false; throw IOException("Fixture committed response lost") }
            return json(plans.getValue(owner))
        }
        private fun json(plan: KhatmahPlan?): JsonElement = plan?.let { JsonObject().apply {
            addProperty("start_date", it.start.toString()); addProperty("target_date", it.target.toString())
            addProperty("total_pages", 604); addProperty("completed_pages", it.completed)
            addProperty("status", it.status); addProperty("revision", it.revision)
        } } ?: JsonNull.INSTANCE
    }
    private inner class Fixture : AutoCloseable {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "khatmah-fixture-${UUID.randomUUID()}.db"
        val namespace = "123456abcdef"
        val cipher = PrivateCipher(); val tokens = Store(); val server = Server()
        private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        var session = SessionRepository(server, tokens)
        fun repository() = KhatmahRepository(namespace, db, cipher, session, today = { day })
        suspend fun login(owner: Long) = session.login(if (owner == 2L) "two@example.test" else "one@example.test", "Fixture123!")
        suspend fun row(owner: Long) = db.dao().cache(owner, KhatmahJournalCodec.KEY)
        fun reopen() { db.close(); db = open(); session = SessionRepository(server, tokens) }
        override fun close() { server.release.complete(Unit); db.close(); check(name.startsWith("khatmah-fixture-")); context.deleteDatabase(name) }
    }
    private fun draft(pages: Int) = KhatmahDraft(day.toString(), day.plusDays(30).toString(), pages.toString())

    @Test fun committedLostReplySurvivesDatabaseReopenWithoutDuplicateMutation() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val repository = f.repository()
            repository.enqueue(1, "save", draft(20), 0)
            assertFalse(f.row(1)!!.payloadCipher.contains("start_date"))
            f.server.loseReply = true
            assertTrue(runCatching { repository.sync(1) }.exceptionOrNull() is IOException)
            val prepared = repository.read(1); assertEquals(0, prepared.intents.single().expectedRevision)
            val bytes = f.row(1)
            f.db.dao().trimContentCache(1, Long.MAX_VALUE / 2, ContentCachePolicy(maxEntries = 1, maxBytes = 64, maxEntryBytes = 64, maxAgeMillis = 1))
            assertEquals(bytes, f.row(1))
            f.reopen(); f.login(1); val cold = f.repository(); assertEquals(prepared, cold.read(1))
            cold.sync(1); assertFalse(cold.hasPending(1)); assertEquals(1, f.server.mutations)
            assertEquals(20, cold.read(1).acknowledged!!.completed)
        } finally { f.close() }
    }
    @Test fun heldAcknowledgementKeepsNewerTailAndSyncsBothInOrder() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val repository = f.repository()
            val first = repository.enqueue(1, "save", draft(20), 0)
            f.server.hold = true
            val request = async { repository.sync(1) }
            withTimeout(10000) { f.server.started.await() }
            val tail = repository.enqueue(1, "save", draft(30), first.version)
            assertEquals(2, tail.intents.size)
            f.server.hold = false; f.server.release.complete(Unit); request.await()
            assertEquals(2, f.server.mutations); assertFalse(repository.hasPending(1))
            assertEquals(30, repository.read(1).projected()!!.completed)
            assertEquals(tail.version, repository.read(1).version)
        } finally { f.close() }
    }
    @Test fun lateActorAResponseCannotChangeEitherActorsRetainedRows() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val repository = f.repository(); repository.enqueue(1, "save", draft(20), 0)
            f.server.hold = true; val request = async { runCatching { repository.sync(1) } }
            withTimeout(10000) { f.server.started.await() }; val a = f.row(1)
            f.login(2); repository.enqueue(2, "save", draft(40), 0); val b = f.row(2)
            f.server.hold = false; f.server.release.complete(Unit)
            assertTrue(request.await().isFailure); assertEquals(a, f.row(1)); assertEquals(b, f.row(2))
            f.login(1); repository.sync(1)
            assertEquals(1, f.server.mutations); assertEquals(b, f.row(2))
        } finally { f.close() }
    }
    @Test fun unreadableForeignEnvelopeAndDeletionFenceRetainBytes() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val repository = f.repository(); repository.enqueue(1, "save", draft(20), 0)
            val a = f.row(1)!!; f.login(2); val swapped = a.copy(userId = 2); f.db.dao().cache(swapped)
            assertEquals(ProtectedRead.Unreadable, repository.observe(2).first())
            assertTrue(runCatching { retainedKhatmah(f.namespace, 2, f.db.dao(), f.cipher) }.isFailure)
            assertTrue(runCatching { repository.refresh(2) }.isFailure)
            assertEquals(swapped, f.row(2)); assertEquals(0, f.server.mutations)
            f.login(1); val lease = f.session.lease(1)
            val store = AccountDeletionStore(f.namespace, f.db, f.cipher)
            store.prepare(1) { f.session.requireLease(lease) }
            assertTrue(runCatching { repository.enqueue(1, "save", draft(25), 1) }.exceptionOrNull() is AccountDeletionPending)
            assertEquals(a, f.row(1)); assertEquals(swapped, f.row(2))
            f.db.dao().deleteUser(1)
            assertNull(f.row(1)); assertEquals(swapped, f.row(2))
        } finally { f.close() }
    }
    @Test fun conflictReviewCannotOverwriteAServerVersionThatChangedAgain() = runBlocking {
        val f = Fixture()
        try {
            f.login(1); val repository = f.repository()
            repository.enqueue(1, "save", draft(20), 0); repository.sync(1)
            val acknowledged = repository.read(1)
            f.server.plans[1] = acknowledged.acknowledged!!.copy(completed = 40, revision = 2)
            repository.enqueue(1, "save", draft(30), acknowledged.version)
            assertTrue(runCatching { repository.sync(1) }.exceptionOrNull() is ApiException)
            val review = repository.read(1)
            assertTrue(review.conflicted); assertEquals(30, review.projected()!!.completed)
            assertEquals(40, review.serverConflict!!.completed); assertFalse(repository.hasPending(1))
            f.server.plans[1] = review.serverConflict.copy(completed = 50, revision = 3)
            assertEquals(409, (runCatching { repository.keepDevice(1, review.version, draft(30), true) }.exceptionOrNull() as ApiException).status)
            val changed = repository.read(1)
            assertEquals(50, changed.serverConflict!!.completed); assertTrue(changed.version > review.version)
            val rebased = repository.keepDevice(1, changed.version, draft(30), true)
            assertEquals(409, (runCatching { repository.useServer(1, rebased.version) }.exceptionOrNull() as ApiException).status)
            repository.sync(1)
            assertEquals(30, f.server.plans.getValue(1).completed)
            assertEquals(4, f.server.plans.getValue(1).revision)
            assertFalse(repository.read(1).blocked); assertFalse(repository.hasPending(1))
        } finally { f.close() }
    }
}
