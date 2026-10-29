package org.athar.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.athar.app.core.network.ApiException
import org.athar.app.core.sync.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OwnedSyncRunnerTest {
    private class Operations:OwnedSyncOperations {
        override var owner:Long?=1
        val calls=mutableListOf<SyncModule>();var pending=false
        var action:suspend (SyncModule)->Unit={}
        override suspend fun push(module:SyncModule,owner:Long){assertEquals(1L,owner);calls.add(module);action(module)}
        override suspend fun hasPending(owner:Long)=pending
    }
    @Test fun matchingOwnerFinishesAllModulesWithoutCreatingProgress()=runTest {
        val operations=Operations()
        assertEquals(SyncOutcome.COMPLETE,OwnedSyncRunner(operations).run(1))
        assertEquals(SyncModule.entries,operations.calls)
    }
    @Test fun staleOwnerCannotStartAnyPrivateOperation()=runTest {
        val operations=Operations()
        assertEquals(SyncOutcome.AUTH_REQUIRED,OwnedSyncRunner(operations).run(2));assertTrue(operations.calls.isEmpty())
    }
    @Test fun changingAccountsDuringARunStopsBeforeTheNextModule()=runTest {
        val operations=Operations().apply{action={owner=2}}
        assertEquals(SyncOutcome.AUTH_REQUIRED,OwnedSyncRunner(operations).run(1));assertEquals(listOf(SyncModule.READING),operations.calls)
    }
    @Test fun unavailableModuleKeepsOtherModulesUsableAndRequestsNetworkRetry()=runTest {
        val operations=Operations().apply{action={if(it==SyncModule.READING)throw IOException("Private diagnostic")}}
        assertEquals(SyncOutcome.RETRY,OwnedSyncRunner(operations).run(1));assertEquals(SyncModule.entries.size,operations.calls.size)
    }
    @Test fun revocationStopsTheRunRatherThanRetryingAFormerAccount()=runTest {
        val operations=Operations().apply{action={throw ApiException(403,"forbidden","Private provider text")}}
        assertEquals(SyncOutcome.AUTH_REQUIRED,OwnedSyncRunner(operations).run(1));assertEquals(1,operations.calls.size)
    }
    @Test fun malformedLocalDataRequiresAttentionButDoesNotBlockHealthyModules()=runTest {
        val operations=Operations().apply{action={if(it==SyncModule.COUNTERS)throw IllegalArgumentException("Private ciphertext fixture")}}
        assertEquals(SyncOutcome.ACTION_REQUIRED,OwnedSyncRunner(operations).run(1));assertEquals(SyncModule.entries.size,operations.calls.size)
    }
    @Test fun throttlingAndServerFailureRetryWhileValidationNeedsAnExplicitFix()=runTest {
        for(status in listOf(429,500,422)){
            val operations=Operations().apply{action={if(it==SyncModule.LESSONS)throw ApiException(status,"fixture","")}}
            assertEquals(if(status==422)SyncOutcome.ACTION_REQUIRED else SyncOutcome.RETRY,OwnedSyncRunner(operations).run(1))
        }
    }
    @Test fun newPendingEditsAndBudgetExpiryDoNotProduceFalseCompletion()=runTest {
        assertEquals(SyncOutcome.RETRY,OwnedSyncRunner(Operations().apply{pending=true}).run(1))
        val operations=Operations().apply{action={delay(100)}}
        assertEquals(SyncOutcome.RETRY,OwnedSyncRunner(operations,5).run(1));assertEquals(1,operations.calls.size)
    }
    @Test fun externalCancellationPropagatesWithoutEmittingAFailureRecord()=runTest {
        val cancellation=CancellationException("Fixture cancellation")
        val operations=Operations().apply{action={throw cancellation}}
        val failure=runCatching{OwnedSyncRunner(operations).run(1)}.exceptionOrNull()
        assertTrue(failure is CancellationException);assertEquals(cancellation.message,failure?.message)
    }
}
