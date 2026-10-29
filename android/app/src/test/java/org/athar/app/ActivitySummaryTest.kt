package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.core.data.ActivitySummary
import org.junit.Assert.*
import org.junit.Test

class ActivitySummaryTest {
    private val fixture = """{"schema_version":1,"owner_id":7,"generated_date":"2026-10-29","availability":{"reading_sessions":false,"listening_history":false,"book_downloads":false,"adhkar_completions":true,"lesson_progress":true},"recorded":{"adhkar_completions":2,"lesson_completions":1,"active_days":2,"lessons_with_progress":3,"completed_lessons":1,"legacy_book_download_events":99,"legacy_quran_sessions":88},"recent":[{"date":"2026-10-28","lesson_completions":1,"adhkar_completions":0},{"date":"2026-10-29","lesson_completions":0,"adhkar_completions":2}]}"""
    private fun parse(value: String = fixture, owner: Long = 7) = ActivitySummary.parse(JsonParser.parseString(value), owner)
    @Test fun actualRecordedMetricsKeepLegacyEventsOutOfNativeCompletionCounts() {
        val value = parse()
        assertEquals(2L,value.adhkar);assertEquals(1L,value.lessons);assertEquals(3L,value.lessonsWithProgress)
        assertEquals(2,value.recent.size)
        assertTrue(runCatching { parse(owner=8) }.isFailure)
    }
    @Test fun versionTwoUsesOnlySeparateCompletedDownloadCountsAndKeepsOldCacheUnavailable() {
        assertNull(parse().downloads)
        val root=JsonParser.parseString(fixture).asJsonObject
        root.addProperty("schema_version",2);root.getAsJsonObject("availability").addProperty("book_downloads",true)
        root.getAsJsonObject("recorded").addProperty("completed_book_downloads",3)
        root.getAsJsonArray("recent")[0].asJsonObject.addProperty("completed_book_downloads",1)
        root.getAsJsonArray("recent")[1].asJsonObject.addProperty("completed_book_downloads",2)
        val summary=ActivitySummary.parse(root,7)
        assertEquals(3L,summary.downloads);assertEquals(2L,summary.recent.last().downloads)
        root.getAsJsonArray("recent")[1].asJsonObject.addProperty("completed_book_downloads",3)
        assertTrue(runCatching{ActivitySummary.parse(root,7)}.isFailure)
    }
    @Test fun unavailableOrUnreadableIsNeverAnInventedZeroMetric() {
        for (bad in listOf("{}",fixture.replace("\"active_days\":2","\"active_days\":true"),fixture.replace("\"active_days\":2","\"active_days\":2.5"),fixture.replace("\"active_days\":2","\"active_days\":9223372036854775808"),fixture.replace("\"book_downloads\":false","\"book_downloads\":true"),fixture.replace("\"reading_sessions\":false","\"reading_sessions\":\"false\""),fixture.replace("\"completed_lessons\":1","\"completed_lessons\":4")))
            assertTrue(runCatching { parse(bad) }.isFailure)
    }
    @Test fun recentDaysMustBeOrderedUniqueBoundedAndConsistentWithTotals() {
        for (bad in listOf(fixture.replace("2026-10-28","2026-10-29"),fixture.replace("2026-10-28","2026-10-15"),fixture.replace("2026-10-28","2026-10-30"),fixture.replace("2026-10-28","2026-02-30"),fixture.replace("\"active_days\":2","\"active_days\":1"),fixture.replace("\"adhkar_completions\":2}","\"adhkar_completions\":3}")))
            assertTrue(runCatching { parse(bad) }.isFailure)
    }
}
