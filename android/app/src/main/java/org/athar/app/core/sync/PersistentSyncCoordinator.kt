package org.athar.app.core.sync

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.auth.AuthState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Coalesces dirty writes and retains an infrequent fallback for a process-death enqueue gap. */
@OptIn(FlowPreview::class)
class PersistentSyncCoordinator(context:Context,private val graph:AppGraph) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val schedule=PrivateSyncSchedule(context,graph.partition)
    private val changes=MutableSharedFlow<Unit>(replay=1,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    private val operations=GraphSyncOperations(graph)
    private val dirty=ConcurrentHashMap<Pair<Long,SyncModule>,Long>()
    private val sequence=AtomicLong()
    init {
        scope.launch {
            var previous:Long?=null
            combine(graph.session.state,graph.deletionStore.blockedOwners){state,_->state}.collect{state->
                val owner=(state as? AuthState.SignedIn)?.user?.id?.takeUnless(graph.deletionStore::isBlocked)
                if(state !is AuthState.Restoring&&owner!=previous){
                    previous?.let(schedule::cancel)
                    previous=owner
                    owner?.let{schedule.maintain(it);schedule.enqueue(it)}
                }
            }
        }
        scope.launch {
            changes.debounce(1500).collect {
                val snapshot=dirty.entries.map{it.key to it.value}
                snapshot.forEach{(key,version)->dirty.remove(key,version)}
                val owner=operations.owner?:return@collect
                val modules=snapshot.map{it.first}.filter{it.first==owner}.map{it.second}.toSet()
                val pending=try{modules.any{operations.hasPending(it,owner)}}catch(failure:CancellationException){throw failure}catch(_:Exception){true}
                if(pending&&operations.owner==owner)schedule.enqueue(owner)
            }
        }
    }
    fun request(owner:Long,module:SyncModule){if(operations.owner==owner){dirty[owner to module]=sequence.incrementAndGet();changes.tryEmit(Unit)}}
}
