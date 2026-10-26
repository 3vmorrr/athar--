package org.athar.app

import org.athar.app.notifications.NotificationConsent
import org.athar.app.notifications.NotificationPreferenceContract
import org.athar.app.notifications.NotificationPreferences
import org.junit.Assert.*
import org.junit.Test

class NotificationDeliveryConsentTest {
    @Test fun aPreviouslyEnabledCategoryFailsClosedAfterConsentWithdrawal() {
        val categories = NotificationPreferenceContract.categories.associateWith { it == "quran_routine" }
        val revoked = NotificationPreferences(
            schemaVersion = 1,
            revision = 3,
            masterEnabled = false,
            consent = NotificationConsent(1, 1, true, "2026-10-26 20:00:00"),
            categories = categories,
            updatedAt = null,
        )
        assertFalse(revoked.allows("quran_routine"))
    }

    @Test fun categoryWithdrawalFailsClosedEvenWhenMasterAndConsentRemainCurrent() {
        val categories = NotificationPreferenceContract.categories.associateWith { false }
        val value = NotificationPreferences(
            schemaVersion = 1,
            revision = 4,
            masterEnabled = true,
            consent = NotificationConsent(1, 1, true, "2026-10-26 20:00:00"),
            categories = categories,
            updatedAt = null,
        )
        assertFalse(value.allows("lesson_reminders"))
        assertFalse(value.allows("quran_routine"))
    }
}
