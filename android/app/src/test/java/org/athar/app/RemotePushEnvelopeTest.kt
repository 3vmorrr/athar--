package org.athar.app

import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class RemotePushEnvelopeTest {
    private fun valid()=mapOf(
        "version" to "1",
        "owner_id" to "7",
        "environment" to "012345abcdef",
        "category" to "lesson_reminders",
        "destination" to "lesson:42",
        "title" to "Lesson",
        "body" to "Starts soon",
        "dedupe_key" to "lesson-reminder-42",
        "expires_at_ms" to "1800000000000",
    )

    @Test fun strictEnvelopeAcceptsOnlyOwnerEnvironmentAndAllowlistedDestination() {
        val value=RemotePushEnvelopeContract.parse(valid())
        assertEquals(7L,value.ownerId)
        assertEquals("012345abcdef",value.environment)
        assertEquals(NotificationDestination.Lesson(42),value.destination)
    }

    @Test fun arbitraryUrlsUnknownFieldsAndBadEnvironmentFailClosed() {
        assertTrue(runCatching{RemotePushEnvelopeContract.parse(valid()+("destination" to "https://evil.invalid"))}.isFailure)
        assertTrue(runCatching{RemotePushEnvelopeContract.parse(valid()+("secret" to "x"))}.isFailure)
        assertTrue(runCatching{RemotePushEnvelopeContract.parse(valid()+("environment" to "production"))}.isFailure)
    }

    @Test fun expiryIsCheckedLocallyBeforeAnyFutureDeliveryHandler() {
        val value=RemotePushEnvelopeContract.parse(valid())
        assertFalse(value.expired(1_700_000_000_000))
        assertTrue(value.expired(1_800_000_000_000))
    }
}
