package org.athar.app

import org.athar.app.notifications.NotificationChannels
import org.junit.Assert.*
import org.junit.Test

class NotificationChannelsTest {
    @Test fun reminderCategoriesMapToStableChannels() {
        assertEquals(NotificationChannels.LESSONS, NotificationChannels.channelIdForCategory("lesson_reminders"))
        assertEquals(NotificationChannels.FOLLOWING, NotificationChannels.channelIdForCategory("following_updates"))
        listOf(
            "quran_routine",
            "adhkar_routine",
            "salawat_friday",
            "custom_routines",
            "khatmah",
            "night_prayer",
        ).forEach {
            assertEquals(NotificationChannels.ROUTINES, NotificationChannels.channelIdForCategory(it))
        }
        assertNull(NotificationChannels.channelIdForCategory("private_worship_history"))
    }
}
