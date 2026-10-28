package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.adhkar.AdhkarSummary
import org.junit.Assert.*
import org.junit.Test

class AdhkarSummaryTest {
    private val fixture = """{"date":"2026-10-28","today_completions":1,"total_completions":4,"active_days":2,"categories":[{"category":"Morning","item_count":2,"completed_items":1,"current_count":3,"target_count":6}]}"""
    @Test fun authoritativeHistoryAndCurrentCategoryProgressRemainSeparate() {
        val summary = AdhkarSummary.parse(JsonParser.parseString(fixture))
        assertEquals(4L, summary.total); assertEquals(1L, summary.today)
        assertEquals(3L, summary.categories.single().count)
    }
    @Test fun missingOrInconsistentDataNeverFabricatesZeroStatistics() {
        for (broken in listOf("{}", fixture.replace("\"total_completions\":4", "\"total_completions\":0"), fixture.replace("\"target_count\":6", "\"target_count\":2"), fixture.replace("2026-10-28", "2026-02-30")))
            assertTrue(runCatching { AdhkarSummary.parse(JsonParser.parseString(broken)) }.isFailure)
    }
}
