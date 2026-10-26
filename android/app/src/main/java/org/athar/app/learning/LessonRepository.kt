package org.athar.app.learning

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.BufferOverflow
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.security.PrivateCipher
import org.athar.app.core.network.ApiException
import java.util.UUID

@Keep data class SavedLessonProgress(val lessonId: Int, val positionSeconds: Int, val durationSeconds: Int, val complete: Boolean, val eventId: String?, val pending: Boolean = true, val version: Long = 1)

data class RetainedLessonProgress(
    val progress: SavedLessonProgress,
    val updatedAtMillis: Long,
)

object LessonContinuationPolicy {
    fun latest(values: List<RetainedLessonProgress>): RetainedLessonProgress? =
        values
            .asSequence()
            .filter { !it.progress.complete && it.progress.positionSeconds > 0 }
            .maxWithOrNull(
                compareBy<RetainedLessonProgress> { it.updatedAtMillis }
                    .thenBy { it.progress.lessonId },
            )
}

/** Private durable checkpoints; remote errors never discard the local resume position. */
@OptIn(FlowPreview::class)
class LessonRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository,private val requestSync:(Long)->Unit={},automaticSync:Boolean=true) : AutoCloseable {
    private val gson = Gson(); private val local = Mutex(); private val remote = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(replay = 1,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    private val failed=MutableStateFlow<Long?>(null);val failedUser=failed.asStateFlow()
    init {
        if(automaticSync)scope.launch { changes.debounce(750).collect { try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Remains pending. */ } } }
        if(automaticSync)scope.launch { session.state.collect { if (it is AuthState.SignedIn) changes.emit(Unit) } }
    }
    private fun key(id: Int) = "lesson-progress:$id"
    private fun decode(row: CachedDocument):SavedLessonProgress {
        val body=JsonParser.parseString(cipher.open(row.payloadCipher)).asJsonObject
        for(field in listOf("lessonId","positionSeconds","durationSeconds","version"))require(body[field]?.toString()?.matches(Regex("[0-9]+"))==true)
        for(field in listOf("complete","pending"))require(body[field]?.let{it.isJsonPrimitive&&it.asJsonPrimitive.isBoolean}==true)
        val value=requireNotNull(gson.fromJson(body, SavedLessonProgress::class.java))
        require(value.lessonId>0&&row.cacheKey==key(value.lessonId)&&value.positionSeconds in 0..86400&&value.durationSeconds in 0..86400&&value.version>0)
        require(value.durationSeconds==0||value.positionSeconds<=value.durationSeconds)
        value.eventId?.let{require(UUID.fromString(it).toString()==it)}
        require(!value.complete||value.eventId!=null)
        return value
    }
    suspend fun saved(user: Long, id: Int): SavedLessonProgress? = withContext(Dispatchers.IO) { dao.cache(user, key(id))?.let(::decode) }
    fun observe(user:Long,id:Int)=dao.observeCache(user,key(id)).map{it?.let(::decode)}.flowOn(Dispatchers.Default)
    suspend fun retained(user:Long)=withContext(Dispatchers.IO){session.requireVerifiedOwner(user);dao.caches(user,"lesson-progress:%").map(::decode).also{session.requireVerifiedOwner(user)}}
    suspend fun latestIncomplete(user:Long):RetainedLessonProgress?=withContext(Dispatchers.IO){
        session.requireVerifiedOwner(user)
        val retained=dao.caches(user,"lesson-progress:%").map { row ->
            RetainedLessonProgress(decode(row),row.updatedAt)
        }
        session.requireVerifiedOwner(user)
        LessonContinuationPolicy.latest(retained)
    }
    fun checkpoint(id:Int,position:Int,duration:Int,complete:Boolean,expectedOwner:Long?=null) {
        val user=session.userId ?: return
        if(expectedOwner!=null&&expectedOwner!=user)return
        scope.launch { try { if(session.userId==user)save(id,position,duration,complete,user) }catch(e:CancellationException){throw e}catch(_:Exception){failed.value=user} }
    }
    suspend fun save(id: Int, position: Int, duration: Int, complete: Boolean = false,expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        require(id > 0 && position in 0..86400 && duration in 0..86400)
        val user = session.requireVerifiedOwner(expectedOwner)
        local.withLock {
            val old = saved(user, id)
            session.requireVerifiedOwner(user)
            val bounded = if (duration > 0) position.coerceAtMost(duration) else position
            val next = SavedLessonProgress(id, bounded, duration, complete || old?.complete == true, old?.eventId ?: if (complete) UUID.randomUUID().toString() else null, version = (old?.version ?: 0) + 1)
            dao.cache(CachedDocument(user, key(id), cipher.seal(gson.toJson(next)), System.currentTimeMillis()))
        }
        failed.value=null;changes.tryEmit(Unit)
        requestSync(user)
    }
    suspend fun hasPending(user:Long):Boolean=withContext(Dispatchers.IO){dao.caches(user,"lesson-progress:%").any{decode(it).pending}}
    suspend fun sync(expectedOwner:Long?=null) = remote.withLock { withContext(Dispatchers.IO) {
        val user = session.userId ?: return@withContext
        if(expectedOwner!=null&&expectedOwner!=user)throw ApiException(401,"account_changed","")
        session.requireVerifiedOwner(user)
        for (row in dao.caches(user, "lesson-progress:%")) {
            val snapshot = local.withLock { saved(user, decode(row).lessonId) } ?: continue
            if (!snapshot.pending) continue
            session.requireVerifiedOwner(user)
            session.request("PUT", "me/lesson-progress", mapOf("lesson_id" to snapshot.lessonId, "position_seconds" to snapshot.positionSeconds, "duration_seconds" to snapshot.durationSeconds, "is_complete" to snapshot.complete, "event_id" to snapshot.eventId),expectedUser=user)
            local.withLock {
                session.requireVerifiedOwner(user)
                val latest = saved(user, snapshot.lessonId) ?: return@withLock
                if (latest.version == snapshot.version) dao.cache(CachedDocument(user, key(latest.lessonId), cipher.seal(gson.toJson(latest.copy(pending = false))), System.currentTimeMillis()))
            }
        }
    } }
    override fun close() { scope.cancel() }
}
