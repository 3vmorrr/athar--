package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.security.PrivateCipher
import java.time.LocalDate
import java.util.UUID

/** Every method is scoped to the captured account. Exemptions never enter the outbox. */
class NightPrayerRepository(private val database: AppDatabase, private val session: SessionRepository, private val cipher: PrivateCipher) {
    private val dao = database.dao()
    private val syncLock = Mutex()
    private fun user() = requireNotNull(session.userId) { "Unauthenticated" }
    fun observe(user: Long) = dao.nights(user).map { rows -> rows.map { it to JsonParser.parseString(cipher.open(it.payloadCipher)).asJsonObject } }.flowOn(Dispatchers.Default)

    suspend fun save(payload: JsonObject, id: String = UUID.randomUUID().toString()) = withContext(Dispatchers.IO) {
        val user = user()
        database.withTransaction {
            check(session.userId == user)
            val previous = dao.night(user, id)
            require(previous?.deleted != true)
            val expected = previous?.localRevision ?: 0
            val revision = expected + 1
            val body = payload.deepCopy().apply { remove("id"); remove("revision"); remove("deleted") }
            val mutation = UUID.randomUUID().toString()
            val outgoing = body.deepCopy().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", expected) }
            val count = if (body["entry_type"].asString == "range") body["end_index"].asInt - body["start_index"].asInt + 1 else body["verse_count"].asInt
            require(count > 0)
            dao.night(LocalNightSession(user, id, body["night_date"].asString, count, body["measured_seconds"]?.takeUnless { it.isJsonNull }?.asInt, cipher.seal(body.toString()), revision, previous?.serverRevision ?: 0, false, true, System.currentTimeMillis()))
            dao.enqueue(OutboxMutation(userId = user, mutationId = mutation, sessionId = id, operation = "put", expectedRevision = expected, payloadCipher = cipher.seal(outgoing.toString())))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val user = user()
        database.withTransaction {
            val previous = requireNotNull(dao.night(user, id)); if (previous.deleted) return@withTransaction
            val mutation = UUID.randomUUID().toString()
            val body = JsonObject().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", previous.localRevision) }
            dao.night(previous.copy(nightDate = "", verseCount = 0, measuredSeconds = null, payloadCipher = cipher.seal("{}"), localRevision = previous.localRevision + 1, deleted = true, dirty = true))
            dao.enqueue(OutboxMutation(userId = user, mutationId = mutation, sessionId = id, operation = "delete", expectedRevision = previous.localRevision, payloadCipher = cipher.seal(body.toString())))
        }
    }

    suspend fun sync(): Int = syncLock.withLock { withContext(Dispatchers.IO) {
        val user = user()
        var conflicts = 0
        var more: Boolean
        var batches = 0
        do {
            check(session.userId == user)
            val pending = dao.pending(user)
            val blocked = pending.filter { it.conflict }.map { it.sessionId }.toSet()
            val eligible = pending.filter { it.sessionId !in blocked }
            val cursorDoc = dao.cache(user, "night-sync-cursor")
            val cursor = cursorDoc?.let { cipher.open(it.payloadCipher).toLong() } ?: 0L
            val operations = eligible.map { mapOf("id" to it.sessionId, "operation" to it.operation, "data" to JsonParser.parseString(cipher.open(it.payloadCipher))) }
            val reply = session.request("POST", "night-prayer/sync", mapOf("cursor" to cursor, "operations" to operations),expectedUser=user).asJsonObject
            check(session.userId == user)
            database.withTransaction {
                reply["results"].asJsonArray.forEachIndexed { index, element ->
                    val result = element.asJsonObject; val mutation = eligible[index]
                    if (result["status"].asString == "applied") {
                        dao.acknowledge(user, mutation.mutationId)
                        dao.night(user, mutation.sessionId)?.let { local -> dao.night(local.copy(serverRevision = result["result"].asJsonObject["revision"].asInt, dirty = dao.pendingCount(user, mutation.sessionId) > 0)) }
                    } else { dao.markConflict(user, mutation.mutationId); conflicts++ }
                }
                reply["changes"].asJsonArray.forEach { element ->
                    val item = element.asJsonObject; val id = item["id"].asString
                    if (dao.pendingCount(user, id) == 0) replaceRemote(user, item)
                }
                dao.cache(CachedDocument(user, "night-sync-cursor", cipher.seal(reply["next_cursor"].asString), System.currentTimeMillis()))
            }
            more = reply["has_more"].asBoolean || (eligible.isNotEmpty() && dao.pending(user).any { !it.conflict && it.sessionId !in blocked })
            batches++
        } while (more && batches < 20)
        conflicts
    } }

    /** Explicit conflict resolution: discard this device's pending edit and keep the server version. */
    suspend fun useServer(id: String) = withContext(Dispatchers.IO) {
        val user = user(); val remote = session.request("GET", "night-prayer/sessions/$id",expectedUser=user).asJsonObject
        check(session.userId == user)
        database.withTransaction { dao.removeSessionMutations(user, id); replaceRemote(user, remote) }
    }

    private suspend fun replaceRemote(user: Long, item: JsonObject) {
        val deleted = item["deleted"].asBoolean; val revision = item["revision"].asInt
        dao.night(LocalNightSession(user, item["id"].asString, if (deleted) "" else item["night_date"].asString, if (deleted) 0 else item["verse_count"].asInt, item["measured_seconds"]?.takeUnless { it.isJsonNull }?.asInt, cipher.seal(item.toString()), revision, revision, deleted, false, System.currentTimeMillis()))
    }

    suspend fun localExemptions(): Set<LocalDate> = withContext(Dispatchers.IO) {
        dao.exemptions(user())?.let { JsonParser.parseString(cipher.open(it.encryptedPayload)).asJsonArray.map { date -> LocalDate.parse(date.asString) }.toSet() }.orEmpty()
    }
    suspend fun localExemptions(dates: Set<LocalDate>) = withContext(Dispatchers.IO) {
        require(dates.size <= 3660)
        dao.exemptions(PrivateExemptions(user(), cipher.seal(com.google.gson.Gson().toJson(dates.map { it.toString() }.sorted()))))
    }
}
