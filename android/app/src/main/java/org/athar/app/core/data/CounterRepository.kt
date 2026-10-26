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
import java.time.Instant
import java.time.ZoneOffset

@Keep data class CounterState(
    val item: Int,
    val target: Int,
    val count: Int = 0,
    val revision: Int = 0,
    val localVersion: Int = 0,
    val pending: Boolean = false,
    val conflict: Boolean = false,
    val events: List<String> = emptyList(),
    val lastCompletion: String? = null,
    val day: String? = null,
)

/** Coalesced counter writes, but completion events remain a durable UUID queue. */
@OptIn(FlowPreview::class)
class CounterRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository, private val automaticSync: Boolean = true,private val requestSync:(Long)->Unit={}) : AutoCloseable {
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
    private fun utcDay(epochMillis:Long=System.currentTimeMillis()):String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    private fun validDay(value:String?):Boolean =
        value != null && Regex("""\d{4}-\d{2}-\d{2}""").matches(value)

    fun isCurrentDay(state:CounterState):Boolean = state.day==utcDay()


    private fun decode(row: CachedDocument):CounterState {
        val raw=requireNotNull(gson.fromJson(cipher.open(row.payloadCipher), CounterState::class.java))
        require(raw.item>0&&row.cacheKey==key(raw.item)&&raw.target in 1..65535&&raw.count in 0..raw.target&&raw.revision>=0&&raw.localVersion>=0)
        requireNotNull(raw.events)
        val day=raw.day?.takeIf(::validDay) ?: utcDay(row.updatedAt)
        return raw.copy(day=day)
    }

    private fun currentDayState(value:CounterState,target:Int,today:String=utcDay()):CounterState {
        if(value.day==today)return value
        if(value.pending||value.conflict||value.events.isNotEmpty())return value
        return value.copy(
            target=target,
            count=0,
            day=today,
            lastCompletion=null,
        )
    }

    private suspend fun state(user: Long, item: Int, target: Int):CounterState {
        val row=dao.cache(user,key(item))
        return if(row==null)CounterState(item,target,day=utcDay())
        else currentDayState(decode(row),target)
    }

    private suspend fun write(user: Long, state: CounterState) =
        dao.cache(
            CachedDocument(
                user,
                key(state.item),
                cipher.seal(gson.toJson(state)),
                System.currentTimeMillis(),
            ),
        )

    fun observe(user: Long, item: Int, target: Int): Flow<CounterState> =
        dao.observeCache(user,key(item))
            .map { row ->
                if(row==null)CounterState(item,target,day=utcDay())
                else currentDayState(decode(row),target)
            }
            .flowOn(Dispatchers.Default)

    suspend fun tap(item: Int, target: Int, reset: Boolean = false,expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        session.requireVerifiedOwner(expectedOwner)
        require(item > 0 && target in 1..65535)
        // A new installation has no revision, not a known server count of zero.
        initializing.withLock {
            session.requireVerifiedOwner(user)
            if(dao.cache(user,key(item))==null) {
                try { refresh(item,target,user) }
                catch(failure: IOException) { if(failure is ApiException && failure.status in listOf(401,403,422)) throw failure }
            }
        }
        val updated = local.withLock {
            val previous = state(user,item,target)
            session.requireVerifiedOwner(user)
            check(!previous.conflict)
            val today=utcDay()
            check(previous.day==today)
            val count=if(reset||previous.target!=target)0
                else (previous.count+1).coerceAtMost(target)
            if(
                previous.target==target &&
                ((reset&&previous.count==0)||(!reset&&count==previous.count))
            )return@withLock previous
            val event=if(!reset&&count==target&&previous.count<target)
                UUID.randomUUID().toString()
            else null
            val next=previous.copy(
                target=target,
                count=count,
                localVersion=previous.localVersion+1,
                pending=true,
                events=previous.events+listOfNotNull(event),
                lastCompletion=event ?: previous.lastCompletion,
                day=today,
            )
            write(user, next)
            next
        }
        schedule()
        if(updated.pending||updated.events.isNotEmpty())requestSync(user)
        updated
    }
    private fun schedule() { changes.tryEmit(Unit) }
    override fun close() { scope.cancel() }
    suspend fun hasPending(user:Long):Boolean=withContext(Dispatchers.IO){dao.caches(user,"adhkar:%").any{val value=decode(it);!value.conflict&&(value.pending||value.events.isNotEmpty())}}
    suspend fun pendingCompletionCount(
        user:Long,
        expectedOwner:Long?=session.userId,
    ):Int=withContext(Dispatchers.IO){
        session.requireVerifiedOwner(expectedOwner)
        if(expectedOwner!=user)throw ApiException(401,"account_changed","")
        dao.caches(user,"adhkar:%").sumOf { document ->
            val value=decode(document)
            if(value.conflict)0 else value.events.size
        }
    }

    suspend fun sync(expectedOwner:Long?=null) = syncing.withLock { withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        if(expectedOwner!=null&&expectedOwner!=user)throw ApiException(401,"account_changed","")
        session.requireVerifiedOwner(user)
        for (document in dao.caches(user, "adhkar:%")) {
            val snapshot = local.withLock { decode(dao.cache(user, document.cacheKey) ?: document) }
            if ((!snapshot.pending && snapshot.events.isEmpty()) || snapshot.conflict) continue
            session.requireVerifiedOwner(user)
            for (event in snapshot.events) {
                session.request(
                    "POST",
                    "me/activity",
                    mapOf(
                        "event_id" to event,
                        "activity_type" to "adhkar_completion",
                        "activity_date" to (snapshot.day ?: utcDay()),
                    ),
                    expectedUser=user,
                )
                local.withLock { session.requireVerifiedOwner(user);val latest = state(user, snapshot.item, snapshot.target); write(user, latest.copy(events = latest.events - event)) }
            }
            if(!snapshot.pending) continue
            try {
                val response=session.request(
                    "PUT",
                    "me/adhkar-progress",
                    mapOf(
                        "item_id" to snapshot.item,
                        "current_count" to snapshot.count,
                        "expected_revision" to snapshot.revision,
                        "event_id" to snapshot.lastCompletion,
                        "progress_date" to (snapshot.day ?: utcDay()),
                    ),
                    expectedUser=user,
                ).asJsonObject
                session.requireVerifiedOwner(user)
                local.withLock {
                    session.requireVerifiedOwner(user)
                    val latest=state(user,snapshot.item,snapshot.target)
                    val unchanged=latest.localVersion==snapshot.localVersion
                    write(
                        user,
                        latest.copy(
                            revision=response["revision"].asInt,
                            pending=!unchanged,
                            count=if(unchanged)response["current_count"].asInt else latest.count,
                            day=if(unchanged)response["progress_date"].asString else latest.day,
                            lastCompletion=if(
                                unchanged &&
                                response["progress_date"].asString != snapshot.day
                            ) null else latest.lastCompletion,
                        ),
                    )
                }
            } catch (failure: ApiException) {
                if (failure.status != 409) throw failure
                val server = fetchServer(user,snapshot.item)
                local.withLock {
                    session.requireVerifiedOwner(user)
                    val latest = state(user, snapshot.item, snapshot.target)
                    val acknowledged=server!=null &&
                        server.target==snapshot.target &&
                        server.revision>snapshot.revision &&
                        (
                            server.count==snapshot.count ||
                            (
                                snapshot.day!=null &&
                                server.day!=null &&
                                snapshot.day < server.day &&
                                server.count==0
                            )
                        )
                    if(acknowledged){
                        val resolved=requireNotNull(server)
                        val unchanged=latest.localVersion==snapshot.localVersion
                        write(
                            user,
                            latest.copy(
                                revision=resolved.revision,
                                pending=!unchanged,
                                conflict=false,
                                count=if(unchanged)resolved.count else latest.count,
                                day=if(unchanged)resolved.day else latest.day,
                                lastCompletion=if(
                                    unchanged && resolved.day!=snapshot.day
                                ) null else latest.lastCompletion,
                            ),
                        )
                    }else{
                        write(user,latest.copy(conflict=true))
                    }
                }
            }
        }
    } }
    private suspend fun fetchServer(user:Long,item:Int):CounterState? {
        val row = session.request("GET", "me/adhkar-progress",query=mapOf("item_id" to "$item"),expectedUser=user).asJsonArray.firstOrNull { it.asJsonObject["item_key"].asString == "curated:$item" }?.asJsonObject ?: return null
        val count=row["current_count"].asInt
        val target=row["repeat_count"].asInt
        val revision=row["revision"].asInt
        val day=row["progress_date"]?.asString?.takeIf(::validDay) ?: utcDay()
        if(target !in 1..65535 || count !in 0..target || revision<1) throw IOException("Invalid counter state")
        return CounterState(item,target,count,revision,day=day)
    }
    suspend fun refresh(item:Int,target:Int,expectedUser:Long?=session.userId) = syncing.withLock { withContext(Dispatchers.IO) {
        require(item>0 && target in 1..65535)
        val user=requireNotNull(session.userId)
        session.requireVerifiedOwner(expectedUser)
        val server=fetchServer(user,item)
        local.withLock {
            session.requireVerifiedOwner(user)
            val previous=state(user,item,target)
            if(previous.pending || previous.conflict || previous.events.isNotEmpty()) return@withLock
            if((server?.revision ?: 0)<previous.revision) {
                write(user,previous.copy(conflict=true))
            } else {
                write(
                    user,
                    previous.copy(
                        target=target,
                        count=if(server?.target==target)server.count else 0,
                        revision=server?.revision ?: 0,
                        localVersion=previous.localVersion+1,
                        day=server?.day ?: utcDay(),
                        lastCompletion=null,
                    ),
                )
            }
        }
        schedule()
        if(hasPending(user))requestSync(user)
    } }
    suspend fun useServer(item: Int, target: Int,expectedOwner:Long?=session.userId) = syncing.withLock { withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        session.requireVerifiedOwner(expectedOwner)
        val server = fetchServer(user,item)
        session.requireVerifiedOwner(user)
        local.withLock {
            session.requireVerifiedOwner(user)
            val previous=state(user,item,target)
            write(
                user,
                previous.copy(
                    count=if(server?.target==target)server.count else 0,
                    target=target,
                    revision=server?.revision ?: 0,
                    localVersion=previous.localVersion+1,
                    pending=false,
                    conflict=false,
                    events=emptyList(),
                    lastCompletion=null,
                    day=server?.day ?: utcDay(),
                ),
            )
        }
        schedule()
        if(hasPending(user))requestSync(user)
    } }
}
