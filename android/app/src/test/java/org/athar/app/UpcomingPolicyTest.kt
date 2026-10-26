package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.learning.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UpcomingPolicyTest {
    @Test fun optionalContextKeepsOldPayloadsCompatibleAndPreservesExplicitReferences() {
        val old=UpcomingLesson.from(JsonParser.parseString("""{"id":5,"title":"Fixture","status":"scheduled","revision":1}"""))
        assertEquals("",old.courseTitle);assertTrue(old.topics.isEmpty());assertNull(old.relatedBook)
        val current=UpcomingLesson.from(JsonParser.parseString("""{"id":5,"title":"Fixture","status":"scheduled","revision":2,"course_title":"Fixture course","topics":["Fixture topic"],"related_book":{"id":7,"title":"Fixture book","page_start":10,"page_end":20},"youtube_id":"M7lc1UVf-VE"}"""))
        assertEquals("Fixture course",current.courseTitle);assertEquals(listOf("Fixture topic"),current.topics)
        assertEquals(EventBook(7,"Fixture book",10,20),current.relatedBook);assertEquals("M7lc1UVf-VE",current.youtubeId)
    }
    @Test fun countdownUsesRealInstantsAndRoundsUpWithoutLosingTheLastMinute() {
        val now=Instant.parse("2026-10-25T17:00:00Z")
        assertEquals(EventCountdown(1,CountdownUnit.MINUTES),eventCountdown(now.plusMillis(1),now))
        assertEquals(EventCountdown(1,CountdownUnit.MINUTES),eventCountdown(now.plusSeconds(1),now))
        assertEquals(EventCountdown(15,CountdownUnit.MINUTES),eventCountdown(now.plusSeconds(900),now))
        assertEquals(EventCountdown(2,CountdownUnit.HOURS),eventCountdown(now.plusSeconds(3601),now))
        assertEquals(EventCountdown(2,CountdownUnit.DAYS),eventCountdown(now.plusSeconds(86401),now))
        assertEquals(EventCountdown(0,CountdownUnit.AWAITING_START),eventCountdown(now,now))
        assertEquals(EventCountdown(0,CountdownUnit.AWAITING_START),eventCountdown(now.minusSeconds(300),now))
        assertEquals(eventCountdown(Instant.parse("2026-10-25T20:15:00+03:00"),now),eventCountdown(now.plusSeconds(900),now))
    }
    @Test fun passedScheduleAndErroneousWatchFlagNeverInventLiveState() {
        val row=JsonParser.parseString("""{"id":5,"title":"Fixture","status":"scheduled","scheduled_at":"2020-01-01T00:00:00Z","revision":2,"can_watch":true}""")
        val event=UpcomingLesson.from(row)
        assertEquals(EventStatus.SCHEDULED,event.status)
        assertFalse(event.canWatch)
        assertEquals(CountdownUnit.AWAITING_START,eventCountdown(requireNotNull(event.start),Instant.parse("2026-10-25T17:00:00Z")).unit)
    }
}
