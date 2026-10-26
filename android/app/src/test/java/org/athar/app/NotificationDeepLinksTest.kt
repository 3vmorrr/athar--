package org.athar.app

import org.athar.app.notifications.NotificationDeepLinks
import org.athar.app.notifications.NotificationDestination
import org.junit.Assert.*
import org.junit.Test

class NotificationDeepLinksTest {
    @Test fun supportedDestinationsRoundTripThroughStableTokens() {
        val destinations = listOf(
            NotificationDestination.Lesson(42),
            NotificationDestination.Series(7),
            NotificationDestination.Home,
            NotificationDestination.Quran,
            NotificationDestination.Adhkar,
            NotificationDestination.NightPrayer,
            NotificationDestination.Inbox,
        )
        destinations.forEach {
            assertEquals(it, NotificationDeepLinks.decode(NotificationDeepLinks.encode(it)))
        }
    }

    @Test fun arbitraryOrMalformedRoutesAreRejected() {
        listOf(
            null,
            "",
            "http://example.invalid",
            "../account",
            "lesson:0",
            "lesson:-1",
            "lesson:not-a-number",
            "series:0",
            "book:12",
            "night:private-session-id",
        ).forEach {
            assertNull(NotificationDeepLinks.decode(it))
        }
    }
}
