package org.athar.app.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.athar.app.core.network.ApiException
import java.io.IOException

enum class SyncModule { READING, COUNTERS, LESSONS, NIGHT_PRAYER, ACTIVITY }
enum class SyncOutcome { COMPLETE, RETRY, AUTH_REQUIRED, ACTION_REQUIRED }
interface OwnedSyncOperations {
    val owner:Long?
    suspend fun push(module:SyncModule,owner:Long)
    suspend fun hasPending(owner:Long):Boolean
}

/** Account-bound orchestration. Results never contain private exception/provider text. */
class OwnedSyncRunner(private val operations:OwnedSyncOperations,private val budgetMillis:Long=90_000) {
    suspend fun run(owner:Long):SyncOutcome = withTimeoutOrNull(budgetMillis){runOnce(owner)}?:SyncOutcome.RETRY
    private suspend fun runOnce(owner:Long):SyncOutcome {
        if(owner<=0||operations.owner!=owner)return SyncOutcome.AUTH_REQUIRED
        var retry=false;var attention=false
        for(module in SyncModule.entries){
            if(operations.owner!=owner)return SyncOutcome.AUTH_REQUIRED
            try{operations.push(module,owner)}
            catch(failure:CancellationException){throw failure}
            catch(failure:ApiException){
                if(failure.status in listOf(401,403))return SyncOutcome.AUTH_REQUIRED
                if(failure.status>=500||failure.status in listOf(408,429))retry=true else attention=true
            }
            catch(_:IOException){retry=true}
            catch(_:Exception){attention=true}
        }
        if(operations.owner!=owner)return SyncOutcome.AUTH_REQUIRED
        if(retry)return SyncOutcome.RETRY
        if(attention)return SyncOutcome.ACTION_REQUIRED
        return try{if(operations.hasPending(owner))SyncOutcome.RETRY else SyncOutcome.COMPLETE}
        catch(failure:CancellationException){throw failure}
        catch(_:Exception){SyncOutcome.ACTION_REQUIRED}
    }
}
