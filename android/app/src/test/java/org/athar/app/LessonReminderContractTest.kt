package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.learning.LessonReminderChoice
import org.athar.app.learning.UpcomingLesson
import org.junit.Assert.*
import org.junit.Test

class LessonReminderContractTest {
    @Test fun choiceUsesSingleFifteenMinuteDefault() {
        val value = LessonReminderChoice(42)
        assertEquals(15, value.leadMinutes)
        assertTrue(runCatching { LessonReminderChoice(42, 10) }.isFailure)
    }

    @Test fun upcomingPayloadCarriesReminderAndSourceScheduleFacts() {
        val value = UpcomingLesson.from(JsonParser.parseString("""{
            "id":42,"event_id":91,"title":"Fixture","status":"scheduled",
            "scheduled_at":"2026-10-27T18:00:00Z","schedule_timezone":"Africa/Cairo",
            "reminders_enabled":true,"revision":7
        }"""))
        assertEquals(91L, value.eventId)
        assertEquals("Africa/Cairo", value.scheduleTimezone)
        assertTrue(value.remindersEnabled)
        assertEquals(7, value.revision)
    }
}
