package org.athar.app.learning

import androidx.annotation.Keep
import com.google.gson.Gson
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

/** Private durable checkpoints; remote errors never discard the local resume position. */
@OptIn(FlowPreview::class)
class LessonRepository(private val dao: AtharDao, private val cipher: PrivateCipher, private val session: SessionRepository,private val requestSync:(Long)->Unit={}) : AutoCloseable {
    private val gson = Gson(); private val local = Mutex(); private val remote = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(replay = 1,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    private val failed=MutableStateFlow<Long?>(null);val failedUser=failed.asStateFlow()
    init {
        scope.launch { changes.debounce(750).collect { try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Remains pending. */ } } }
        scope.launch { session.state.collect { if (it is AuthState.SignedIn) changes.emit(Unit) } }
    }
    private fun key(id: Int) = "lesson-progress:$id"
    private fun decode(row: CachedDocument):SavedLessonProgress {
        val value=requireNotNull(gson.fromJson(cipher.open(row.payloadCipher), SavedLessonProgress::class.java))
        require(value.lessonId>0&&row.cacheKey==key(value.lessonId)&&value.positionSeconds in 0..86400&&value.durationSeconds in 0..86400&&value.version>0)
        return value
    }
    suspend fun saved(user: Long, id: Int): SavedLessonProgress? = withContext(Dispatchers.IO) { dao.cache(user, key(id))?.let(::decode) }
    fun checkpoint(id:Int,position:Int,duration:Int,complete:Boolean) {
        val user=session.userId ?: return
        scope.launch { try { if(session.userId==user)save(id,position,duration,complete) }catch(e:CancellationException){throw e}catch(_:Exception){failed.value=user} }
    }
    suspend fun save(id: Int, position: Int, duration: Int, complete: Boolean = false) = withContext(Dispatchers.IO) {
        require(id > 0 && position in 0..86400 && duration in 0..86400)
        val user = requireNotNull(session.userId)
        local.withLock {
            val old = saved(user, id)
            check(session.userId == user)
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
        for (row in dao.caches(user, "lesson-progress:%")) {
            val snapshot = local.withLock { saved(user, decode(row).lessonId) } ?: continue
            if (!snapshot.pending) continue
            check(session.userId == user)
            session.request("PUT", "me/lesson-progress", mapOf("lesson_id" to snapshot.lessonId, "position_seconds" to snapshot.positionSeconds, "duration_seconds" to snapshot.durationSeconds, "is_complete" to snapshot.complete, "event_id" to snapshot.eventId),expectedUser=user)
            local.withLock {
                check(session.userId == user)
                val latest = saved(user, snapshot.lessonId) ?: return@withLock
                if (latest.version == snapshot.version) dao.cache(CachedDocument(user, key(latest.lessonId), cipher.seal(gson.toJson(latest.copy(pending = false))), System.currentTimeMillis()))
            }
        }
    } }
    override fun close() { scope.cancel() }
}
