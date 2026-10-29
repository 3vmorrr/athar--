package org.athar.app

import org.athar.app.core.data.BookCompletionEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BookCompletionEventTest {
    private val event = BookCompletionEvent("0123456789ab",7,42,"9db001e5-8d21-4cb2-84e7-29fda6da872e","a".repeat(64),LocalDate.parse("2026-10-29"))
    @Test fun originalDateIdHashAndScopeSurviveWithoutCredentialOrPrivateTextFields() {
        assertEquals(event,BookCompletionEvent.parse(event.json(),event.namespace,event.owner,event.eventId))
        assertEquals("BookCompletionEvent(redacted)",event.toString())
        assertEquals(7,event.json().size())
    }
    @Test fun copiedCrossOwnerNamespaceOrRequestReceiptsCannotEnterAnotherOutbox() {
        assertTrue(runCatching { BookCompletionEvent.parse(event.json(),event.namespace,8,event.eventId) }.isFailure)
        assertTrue(runCatching { BookCompletionEvent.parse(event.json(),"abcdef012345",7,event.eventId) }.isFailure)
        assertTrue(runCatching { BookCompletionEvent.parse(event.json(),event.namespace,7,"dda85c9a-1c29-4e3f-91f6-5353e662ea5c") }.isFailure)
    }
    @Test fun malformedCoercedOverflowAndUnexpectedFieldsArePreservedAsUnreadable() {
        val cases=listOf(event.json().apply{addProperty("owner",true)},event.json().apply{addProperty("book",1.5)},event.json().apply{addProperty("book","9223372036854775808")},event.json().apply{addProperty("sha256","invalid")},event.json().apply{addProperty("activity_date","2026-02-30")},event.json().apply{addProperty("password","unexpected")})
        for (bad in cases) assertTrue(runCatching { BookCompletionEvent.parse(bad,event.namespace,7,event.eventId) }.isFailure)
    }
}
