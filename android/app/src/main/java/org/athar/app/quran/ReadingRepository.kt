package org.athar.app.quran

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.network.ApiException
import org.athar.app.core.security.PrivateCipher

@Keep
data class SavedReadingPosition(
    val surah: Int,
    val ayah: Int,
    val revision: Int = 0,
    val version: Long = 1,
    val pending: Boolean = false,
    val conflict: Boolean = false
)

/** Explicit resume markers, never inferred reading activity or completion. */
@OptIn(FlowPreview::class)
class ReadingRepository(
    private val dao: AtharDao,
    private val cipher: PrivateCipher,
    private val session: SessionRepository,
    private val index: suspend () -> QuranIndex,
    automaticSync: Boolean = true,
    private val requestSync:(Long)->Unit={},
    private val onSavedProgress:(Long,Int?,Int)->Unit={_,_,_->},
) : AutoCloseable {
    private val gson = Gson()
    private val local = Mutex()
    private val remote = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val key = "quran-reading-position"

    init {
        if (automaticSync) {
            scope.launch { changes.debounce(850).collect { try { sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* The encrypted marker remains pending. */ } } }
            scope.launch { session.state.collect { if (it is AuthState.SignedIn) changes.tryEmit(Unit) } }
        }
    }

    private suspend fun decode(row: CachedDocument): SavedReadingPosition {
        val value=requireNotNull(gson.fromJson(cipher.open(row.payloadCipher), SavedReadingPosition::class.java))
        index().global(value.surah,value.ayah)
        require(value.revision>=0&&value.version>0)
        return value
    }
    suspend fun saved(user: Long): SavedReadingPosition? = withContext(Dispatchers.IO) { dao.cache(user, key)?.let { decode(it) } }
    fun observe(user: Long): Flow<SavedReadingPosition?> = dao.observeCache(user, key).map { it?.let { row -> decode(row) } }.flowOn(Dispatchers.Default)
    private suspend fun write(user: Long, value: SavedReadingPosition) {
        dao.cache(CachedDocument(user, key, cipher.seal(gson.toJson(value)), System.currentTimeMillis()))
    }

    suspend fun save(surah: Int, ayah: Int,expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        session.requireVerifiedOwner(expectedOwner)
        val quranIndex=index()
        val newGlobal=quranIndex.global(surah, ayah)
        var previousGlobal:Int?=null
        local.withLock {
            val old = saved(user)
            previousGlobal=old?.let{quranIndex.global(it.surah,it.ayah)}
            session.requireVerifiedOwner(user)
            check(old?.conflict != true)
            write(user, SavedReadingPosition(surah, ayah, old?.revision ?: 0, (old?.version ?: 0) + 1, pending = true))
        }
        changes.tryEmit(Unit)
        requestSync(user)
        onSavedProgress(user,previousGlobal,newGlobal)
    }

    private suspend fun fetch(user: Long): SavedReadingPosition? {
        val data = session.request("GET", "quran/position", expectedUser = user)
        return parse(data)
    }

    private suspend fun parse(data: JsonElement): SavedReadingPosition? {
        if (data.isJsonNull) return null
        val row = data.asJsonObject
        val surah = row["surah"].asInt
        val ayah = row["ayah"].asInt
        val revision = row["revision"].asInt
        index().global(surah, ayah)
        require(revision > 0)
        return SavedReadingPosition(surah, ayah, revision)
    }

    suspend fun refresh(expectedOwner:Long?=session.userId) = remote.withLock { withContext(Dispatchers.IO) {
        val user = session.userId ?: return@withContext
        session.requireVerifiedOwner(expectedOwner)
        val server = fetch(user)
        local.withLock {
            session.requireVerifiedOwner(user)
            val current = saved(user)
            if (current?.pending != true) {
                if (server == null) dao.removeCache(user, key)
                else write(user, server.copy(version = (current?.version ?: 0) + 1))
            }
        }
        changes.tryEmit(Unit)
        if(hasPending(user))requestSync(user)
    } }

    suspend fun hasPending(user:Long):Boolean=withContext(Dispatchers.IO){saved(user)?.let{it.pending&&!it.conflict}==true}
    suspend fun sync(expectedOwner:Long?=null) = remote.withLock { withContext(Dispatchers.IO) {
        val user = session.userId ?: return@withContext
        if(expectedOwner!=null&&expectedOwner!=user)throw ApiException(401,"account_changed","")
        session.requireVerifiedOwner(user)
        val snapshot = local.withLock { saved(user) } ?: return@withContext
        if (!snapshot.pending || snapshot.conflict) return@withContext
        val server = try {
            parse(session.request("PUT", "quran/position", mapOf("surah" to snapshot.surah, "ayah" to snapshot.ayah, "expected_revision" to snapshot.revision), expectedUser = user))
        } catch (e: ApiException) {
            if (e.status != 409) throw e
            fetch(user)
        }
        local.withLock {
            session.requireVerifiedOwner(user)
            val current = saved(user) ?: return@withLock
            val acknowledged = server != null && server.surah == snapshot.surah && server.ayah == snapshot.ayah && server.revision > snapshot.revision
            if (acknowledged) {
                write(user, current.copy(revision = requireNotNull(server).revision, pending = current.version != snapshot.version))
            } else {
                write(user, current.copy(conflict = true))
            }
        }
    } }

    /** Always read the latest remote revision before an explicit conflict choice. */
    suspend fun resolve(useRemote: Boolean,expectedOwner:Long?=session.userId) = remote.withLock { withContext(Dispatchers.IO) {
        val user = requireNotNull(session.userId)
        session.requireVerifiedOwner(expectedOwner)
        val server = fetch(user)
        local.withLock {
            session.requireVerifiedOwner(user)
            val current = saved(user)
            if (useRemote) {
                if (server == null) dao.removeCache(user, key)
                else write(user, server.copy(version = (current?.version ?: 0) + 1))
            } else if (current != null) {
                write(user, current.copy(revision = server?.revision ?: 0, version = current.version + 1, pending = true, conflict = false))
            }
        }
        changes.tryEmit(Unit)
        if(hasPending(user))requestSync(user)
    } }

    override fun close() { scope.cancel() }
}
