package org.athar.app.core.data

import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.auth.SessionRepository
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher
import java.util.UUID
import java.io.IOException

@Keep data class CounterState(val item: Int, val target: Int, val count: Int = 0, val revision: Int = 0, val localVersion: Int = 0, val pending: Boolean = false, val conflict: Boolean = false, val events: List<String> = emptyList(), val lastCompletion: String? = null)

/** Coalesced counter writes, but completion events remain a durable UUID queue. */
@OptIn(FlowPreview::class)
class CounterRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository, private val automaticSync: Boolean = true) : AutoCloseable {
    private val gson = Gson()
    private val local = Mutex()
    private val syncing = Mutex()
    private val initializing = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    init {
        if(automaticSync) {
            scope.launch { changes.debounce(450).collect { try { sync() } catch (failure: CancellationException) { throw failure } catch (_: Exception) { /* Pending records remain durable. */ } } }
            scope.launch { session.state.collect { if(it is org.athar.app.auth.AuthState.SignedIn) schedule() } }
        }
    }
    private fun key(item: Int) = "adhkar:$item"
    private fun decode(row: CachedDocument):CounterState {
        val value=requireNotNull(gson.fromJson(cipher.open(row.payloadCipher), CounterState::class.java))
        require(value.item>0&&row.cacheKey==key(value.item)&&value.target in 1..65535&&value.count in 0..value.target&&value.revision>=0&&value.localVersion>=0)
        requireNotNull(value.events)
        return value
    }
    private suspend fun state(user: Long, item: Int, target: Int) = dao.cache(user, key(item))?.let(::decode) ?: CounterState(item, target)
    private suspend fun write(user: Long, state: CounterState) = dao.cache(CachedDocument(user, key(state.item), cipher.seal(gson.toJson(state)), System.currentTimeMillis()))
    fun observe(user: Long, item: Int, target: Int): Flow<CounterState> = dao.observeCache(user, key(item)).map { it?.let(::decode) ?: CounterState(item, target) }.flowOn(Dispatchers.Default)

    suspend fun tap(item: Int, target: Int, reset: Boolean = false) = withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        require(item > 0 && target in 1..65535)
        // A new installation has no revision, not a known server count of zero.
        initializing.withLock {
            check(session.userId == user)
            if(dao.cache(user,key(item))==null) {
                try { refresh(item,target,user) }
                catch(failure: IOException) { if(failure is ApiException && failure.status in listOf(401,403,422)) throw failure }
            }
        }
        val updated = local.withLock {
            val previous = state(user, item, target)
            check(session.userId == user)
            check(!previous.conflict)
            val count = if (reset || previous.target != target) 0 else (previous.count + 1).coerceAtMost(target)
            if(!reset && previous.target==target && count==previous.count) return@withLock previous
            val event = if (count == target && previous.count < target) UUID.randomUUID().toString() else null
            val next = previous.copy(target = target, count = count, localVersion = previous.localVersion + 1, pending = true, events = previous.events + listOfNotNull(event), lastCompletion = event ?: previous.lastCompletion)
            write(user, next)
            next
        }
        schedule()
        updated
    }
    private fun schedule() { changes.tryEmit(Unit) }
    override fun close() { scope.cancel() }
    suspend fun sync() = syncing.withLock { withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        for (document in dao.caches(user, "adhkar:%")) {
            val snapshot = local.withLock { decode(dao.cache(user, document.cacheKey) ?: document) }
            if ((!snapshot.pending && snapshot.events.isEmpty()) || snapshot.conflict) continue
            check(session.userId == user)
            for (event in snapshot.events) {
                session.request("POST", "me/activity", mapOf("event_id" to event, "activity_type" to "adhkar_completion"),expectedUser=user)
                local.withLock { val latest = state(user, snapshot.item, snapshot.target); write(user, latest.copy(events = latest.events - event)) }
            }
            if(!snapshot.pending) continue
            try {
                val response = session.request("PUT", "me/adhkar-progress", mapOf("item_id" to snapshot.item, "current_count" to snapshot.count, "expected_revision" to snapshot.revision, "event_id" to snapshot.lastCompletion),expectedUser=user).asJsonObject
                check(session.userId == user)
                local.withLock { val latest = state(user, snapshot.item, snapshot.target); write(user, latest.copy(revision = response["revision"].asInt, pending = latest.localVersion != snapshot.localVersion)) }
            } catch (failure: ApiException) {
                if (failure.status != 409) throw failure
                val server = fetchServer(user,snapshot.item)
                local.withLock {
                    check(session.userId == user)
                    val latest = state(user, snapshot.item, snapshot.target)
                    val acknowledged = server!=null && server.target==snapshot.target && server.count==snapshot.count && server.revision>snapshot.revision
                    write(user,if(acknowledged) latest.copy(revision=requireNotNull(server).revision,pending=latest.localVersion!=snapshot.localVersion,conflict=false) else latest.copy(conflict=true))
                }
            }
        }
    } }
    private suspend fun fetchServer(user:Long,item:Int):CounterState? {
        val row = session.request("GET", "me/adhkar-progress",query=mapOf("item_id" to "$item"),expectedUser=user).asJsonArray.firstOrNull { it.asJsonObject["item_key"].asString == "curated:$item" }?.asJsonObject ?: return null
        val count=row["current_count"].asInt;val target=row["repeat_count"].asInt;val revision=row["revision"].asInt
        if(target !in 1..65535 || count !in 0..target || revision<1) throw IOException("Invalid counter state")
        return CounterState(item,target,count,revision)
    }
    suspend fun refresh(item:Int,target:Int,expectedUser:Long?=null) = syncing.withLock { withContext(Dispatchers.IO) {
        require(item>0 && target in 1..65535)
        val user=requireNotNull(session.userId)
        check(expectedUser==null || expectedUser==user)
        val server=fetchServer(user,item)
        local.withLock {
            check(session.userId==user)
            val previous=state(user,item,target)
            if(previous.pending || previous.conflict || previous.events.isNotEmpty()) return@withLock
            if((server?.revision ?: 0)<previous.revision) write(user,previous.copy(conflict=true))
            else write(user,previous.copy(target=target,count=if(server?.target==target) server.count else 0,revision=server?.revision ?: 0,localVersion=previous.localVersion+1))
        }
        schedule()
    } }
    suspend fun useServer(item: Int, target: Int) = syncing.withLock { withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        val server = fetchServer(user,item)
        check(session.userId == user)
        local.withLock { val previous = state(user, item, target); write(user, previous.copy(count = if(server?.target==target) server.count else 0, target = target, revision = server?.revision ?: 0, localVersion=previous.localVersion+1, pending = false, conflict = false)) }
        schedule()
    } }
}
