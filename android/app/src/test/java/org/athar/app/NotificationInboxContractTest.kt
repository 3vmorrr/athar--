package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.notifications.NotificationDestination
import org.athar.app.notifications.NotificationInboxContract
import org.junit.Assert.*
import org.junit.Test

class NotificationInboxContractTest {
    private fun item(
        id: Int = 4,
        read: Boolean = false,
        destination: String = "lesson:9",
    ) = """
        {
          "id":$id,
          "category":"lesson_reminders",
          "source_type":"lesson_reminder",
          "source_id":"9",
          "title":"Lesson reminder",
          "body":"Starts soon",
          "destination":"$destination",
          "read":$read,
          "read_at":null,
          "expires_at":"2026-10-27T20:00:00Z",
          "created_at":"2026-10-26T20:00:00Z"
        }
    """.trimIndent()

    @Test fun strictItemParsesAllowlistedNavigation() {
        val value = NotificationInboxContract.item(JsonParser.parseString(item()))
        assertEquals(4, value.id)
        assertEquals(NotificationDestination.Lesson(9), value.destination)
        assertFalse(value.read)
    }

    @Test fun arbitraryDestinationAndUnknownCategoryFailClosed() {
        assertTrue(runCatching {
            NotificationInboxContract.item(
                JsonParser.parseString(item(destination = "https://evil.invalid")),
            )
        }.isFailure)
        assertTrue(runCatching {
            NotificationInboxContract.item(
                JsonParser.parseString(item().replace("lesson_reminders", "private_activity")),
            )
        }.isFailure)
    }

    @Test fun pageRequiresDescendingUniqueIdsAndBoundedCursor() {
        val raw = "{\"items\":[" + item(5) + "," + item(4,true) +
            "],\"pagination\":{\"per_page\":20,\"next_cursor\":\"abc\",\"has_more\":true},\"unread_count\":1}"
        val page = NotificationInboxContract.page(JsonParser.parseString(raw))
        assertEquals(listOf(5,4), page.items.map { it.id })
        assertEquals(1, page.unreadCount)
        assertTrue(page.hasMore)

        val duplicate = "{\"items\":[" + item(5) + "," + item(5) +
            "],\"pagination\":{\"per_page\":20,\"next_cursor\":null,\"has_more\":false},\"unread_count\":2}"
        assertTrue(runCatching {
            NotificationInboxContract.page(JsonParser.parseString(duplicate))
        }.isFailure)

        val outOfOrder = "{\"items\":[" + item(4) + "," + item(5) +
            "],\"pagination\":{\"per_page\":20,\"next_cursor\":null,\"has_more\":false},\"unread_count\":2}"
        assertTrue(runCatching {
            NotificationInboxContract.page(JsonParser.parseString(outOfOrder))
        }.isFailure)
    }
}
