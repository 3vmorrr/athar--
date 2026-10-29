package org.athar.app

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AccountDeletionWorkflowTest {
    private class Actions : AccountDeletionActions {
        var value: AccountDeletionIntent? = null
        var proof: JsonElement = JsonParser.parseString("{}")
        var failure: Exception? = null
        var acknowledged = true
        var currentActor = 1L
        val events = mutableListOf<String>()
        override suspend fun current(owner: Long) = value
        override suspend fun prepare(owner: Long) = AccountDeletionIntent.prepare("123456abcdef",owner).also { value=it; events.add("prepared:$owner") }
        override suspend fun transition(value: AccountDeletionIntent,next: DeletionPhase)=value.copy(phase=next).also{this.value=it;events.add(next.wire)}
        override suspend fun pause(value: AccountDeletionIntent){events.add("paused")}
        override suspend fun undoRejected(value: AccountDeletionIntent){check(value.phase==DeletionPhase.REJECTED);events.add("undo");this.value=null}
        override suspend fun deleteRemote(value: AccountDeletionIntent,password: String){events.add("request");failure?.let{throw it}}
        override suspend fun proof(value: AccountDeletionIntent)=proof
        override suspend fun purgeOwned(value: AccountDeletionIntent){check(value.phase==DeletionPhase.CONFIRMED);events.add("purge:${value.owner}")}
        override suspend fun forgetCredentials(owner: Long){if(currentActor==owner){currentActor=0;events.add("signed-out:$owner")}}
        override suspend fun acknowledge(value: AccountDeletionIntent){if(!acknowledged)throw IOException("offline acknowledgement fixture");events.add("ack")}
        override suspend fun forgetCapability(value: AccountDeletionIntent){events.add("forget-capability");this.value=null}
        override suspend fun schedule(owner: Long){events.add("retry:$owner")}
        fun pending(){value=AccountDeletionIntent.prepare("123456abcdef",1).copy(phase=DeletionPhase.PENDING)}
        fun positive(){val current=checkNotNull(value);proof=JsonParser.parseString("""{"status":"deleted","request_id":"${current.requestId}","owner_id":${current.owner}}""")}
    }
    @Test fun successfulDeleteFencesBeforeDispatchAndPurgesOnlyAfterDurableConfirmation()=runTest {
        val a=Actions();assertEquals(DeletionRecoveryResult.COMPLETE,AccountDeletionWorkflow(a).start(1,"password fixture"))
        assertTrue(a.events.indexOf("paused")<a.events.indexOf("request"))
        assertTrue(a.events.indexOf("retry:1")<a.events.indexOf("request"))
        assertTrue(a.events.indexOf("confirmed")<a.events.indexOf("purge:1"))
        assertTrue(a.events.indexOf("purge:1")<a.events.indexOf("ack"))
    }
    @Test fun firstDefinitiveRejectionResumesWithoutPurgingAnything()=runTest {
        val a=Actions().apply{failure=DeletionRejected(ApiException(422,"invalid_credentials",""))}
        assertTrue(runCatching{AccountDeletionWorkflow(a).start(1,"wrong fixture")}.exceptionOrNull() is ApiException)
        assertTrue("undo" in a.events);assertFalse(a.events.any{it.startsWith("purge")});assertNull(a.value)
    }
    @Test fun lostResultOrLaterRejectionNeverResumesOrPurgesUnconfirmedData()=runTest {
        for(failure in listOf(IOException("lost response fixture"),ApiException(401,"session_changed",""),DeletionRejected(ApiException(422,"invalid_credentials","")))){
            val a=Actions().apply{pending();this.failure=failure}
            assertTrue(runCatching{AccountDeletionWorkflow(a).start(1,"password fixture")}.isFailure)
            assertFalse("undo" in a.events);assertFalse(a.events.any{it.startsWith("purge")});assertEquals(DeletionPhase.PENDING,a.value!!.phase)
        }
    }
    @Test fun recoveryAfterProcessRecreationRequiresPositiveExactProofAndPreservesANewActor()=runTest {
        val a=Actions().apply{pending();currentActor=2}
        assertEquals(DeletionRecoveryResult.RETRY,AccountDeletionWorkflow(a).recover(1))
        assertFalse(a.events.any{it.startsWith("purge")})
        a.positive()
        assertEquals(DeletionRecoveryResult.COMPLETE,AccountDeletionWorkflow(a).recover(1))
        assertTrue("purge:1" in a.events);assertEquals(2L,a.currentActor)
    }
    @Test fun missingAcknowledgementRetriesOnlyMinimalProofAfterPrivateCleanup()=runTest {
        val a=Actions().apply{acknowledged=false}
        assertEquals(DeletionRecoveryResult.RETRY,AccountDeletionWorkflow(a).start(1,"password fixture"))
        assertEquals(DeletionPhase.CLEANED,a.value!!.phase)
        a.acknowledged=true
        assertEquals(DeletionRecoveryResult.COMPLETE,AccountDeletionWorkflow(a).recover(1))
        assertEquals(1,a.events.count{it=="purge:1"});assertTrue("forget-capability" in a.events)
    }
    @Test fun neverDispatchedIntentWaitsForReauthenticationAndNeverInventsADeletion()=runTest {
        val a=Actions();a.prepare(1)
        assertEquals(DeletionRecoveryResult.REAUTH_REQUIRED,AccountDeletionWorkflow(a).recover(1))
        assertFalse("request" in a.events);assertFalse(a.events.any{it.startsWith("purge")})
    }
}
