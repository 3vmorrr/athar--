package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.core.data.*
import org.junit.Assert.*
import org.junit.Test

class AccountDeletionIntentTest {
    private val intent = AccountDeletionIntent("123456abcdef", 1, "11111111-1111-4111-8111-111111111111", "a".repeat(64), DeletionPhase.PENDING)
    private val row get() = LocalAccountDeletion(intent.owner,intent.requestId,intent.phase.wire,"encrypted fixture",1)
    @Test fun payloadBindsEveryRecoveryPhaseAndDoesNotLogItsCapability() {
        assertEquals(intent,AccountDeletionIntent.decode(intent.namespace,row,intent.encode()))
        assertFalse(intent.toString().contains(intent.secret));assertFalse(intent.toString().contains(intent.requestId))
        val first=AccountDeletionIntent.prepare(intent.namespace,1);val second=AccountDeletionIntent.prepare(intent.namespace,1)
        assertNotEquals(first.requestId,second.requestId);assertNotEquals(first.secret,second.secret)
        assertEquals(DeletionPhase.PREPARED,first.phase)
    }
    @Test fun swappedOwnerNamespaceRequestOrUnprotectedPhaseIsRejected() {
        val cases=listOf(row.copy(userId=2),row.copy(requestId="22222222-2222-4222-8222-222222222222"),row.copy(state="rejected"))
        cases.forEach{assertTrue(runCatching{AccountDeletionIntent.decode(intent.namespace,it,intent.encode())}.isFailure)}
        assertTrue(runCatching{AccountDeletionIntent.decode("abcdef123456",row,intent.encode())}.isFailure)
        assertTrue(runCatching{AccountDeletionIntent.decode(intent.namespace,row,intent.encode().replace("\"version\":1","\"version\":2"))}.isFailure)
    }
    @Test fun receiptMustPositivelyConfirmTheExactOwnedRequest() {
        fun proof(owner:String="1",id:String=intent.requestId,status:String="deleted")=JsonParser.parseString("""{"owner_id":$owner,"request_id":"$id","status":"$status"}""")
        assertTrue(DeletionReceiptPayload.confirms(intent,proof()))
        for(bad in listOf(proof("2"),proof("18446744073709551617"),proof("1.0"),proof("\"1\""),proof(id="22222222-2222-4222-8222-222222222222"),proof(status="pending"),JsonParser.parseString("{}")))assertFalse(DeletionReceiptPayload.confirms(intent,bad))
    }
}
