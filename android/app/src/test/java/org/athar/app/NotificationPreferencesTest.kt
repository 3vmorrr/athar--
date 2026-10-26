package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.notifications.NotificationPreferenceContract
import org.athar.app.notifications.NotificationPreferencePatch
import org.athar.app.notifications.NotificationPreferences
import org.athar.app.notifications.notificationPreferencesFromJson
import org.junit.Assert.*
import org.junit.Test

class NotificationPreferencesTest {
    @Test fun safeDefaultIsFullyOptedOut() {
        val value = NotificationPreferences.safeDefault()
        assertFalse(value.masterEnabled)
        assertFalse(value.consent.current)
        assertEquals(0, value.revision)
        NotificationPreferenceContract.categories.forEach { assertFalse(value.allows(it)) }
    }

    @Test fun currentConsentAndCategoryAreBothRequired() {
        val value = notificationPreferencesFromJson(
            JsonParser.parseString(
                """{
                    "schema_version":1,
                    "revision":3,
                    "master_enabled":true,
                    "consent":{"required_version":1,"accepted_version":1,"current":true,"accepted_at":"2026-10-26 20:00:00"},
                    "categories":{"lesson_reminders":true,"night_prayer":false},
                    "updated_at":"2026-10-26 20:00:00"
                }""",
            ),
        )
        assertTrue(value.allows("lesson_reminders"))
        assertFalse(value.allows("night_prayer"))
        assertFalse(value.allows("unknown"))
    }

    @Test fun newerServerConsentFailsClosedOnThisClient() {
        val value = notificationPreferencesFromJson(
            JsonParser.parseString(
                """{
                    "schema_version":1,
                    "revision":4,
                    "master_enabled":true,
                    "consent":{"required_version":2,"accepted_version":2,"current":true,"accepted_at":"2026-10-26 20:00:00"},
                    "categories":{"lesson_reminders":true},
                    "updated_at":null
                }""",
            ),
        )
        assertFalse(value.consent.current)
        assertFalse(value.allows("lesson_reminders"))
    }

    @Test fun patchRejectsUnknownCategoriesBeforeNetwork() {
        val failure = runCatching {
            NotificationPreferencePatch(
                expectedRevision = 0,
                categories = mapOf("private_worship_history" to true),
            ).asBody()
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
