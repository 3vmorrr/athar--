package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.core.ui.AtharNavigationLayoutPolicy
import org.athar.app.core.ui.HomeProjection
import org.junit.Assert.*
import org.junit.Test

class HomeProjectionTest {
    @Test fun actualZeroAndOwnedTotalsRemainDistinctFromUnreadableData() {
        assertEquals(0L, HomeProjection.metric(JsonParser.parseString("""{"activity":{"lesson_completions":"0"}}"""), "lesson_completions"))
        assertEquals(72L, HomeProjection.metric(JsonParser.parseString("""{"activity":{"lesson_completions":72}}"""), "lesson_completions"))
        for (json in listOf("{}", "null", """{"activity":null}""", """{"activity":{"lesson_completions":false}}""", """{"activity":{"lesson_completions":-1}}""", """{"activity":{"lesson_completions":"unreadable"}}""")) {
            assertNull(HomeProjection.metric(JsonParser.parseString(json), "lesson_completions"))
        }
    }
    @Test fun legacyReadingCountersCannotAppearAsImplementedSessionTracking() {
        for (raw in listOf("""{"activity":{"quran_sessions":72}}""","""{"activity":{"quran_sessions":72,"availability":{"reading_sessions":false}}}""","""{"activity":{"quran_sessions":72,"availability":{"reading_sessions":"true"}}}"""))
            assertNull(HomeProjection.metric(JsonParser.parseString(raw),"quran_sessions"))
        assertEquals(72L,HomeProjection.metric(JsonParser.parseString("""{"activity":{"quran_sessions":72,"availability":{"reading_sessions":true}}}"""),"quran_sessions"))
    }
    @Test fun confirmedEmptyCollectionsDifferFromInvalidRecordsAndUnsupportedRoutes() {
        assertEquals(emptyList<Any>(), HomeProjection.items(JsonParser.parseString("""{"books":[]}"""), "books"))
        for (json in listOf("{}", """{"books":null}""", """{"books":[{"id":-1,"title":"Fixture"}]}""", """{"books":[{"id":999999999999,"title":"Fixture"}]}""", """{"books":[{"id":1,"title":null}]}""")) {
            assertNull(HomeProjection.items(JsonParser.parseString(json), "books"))
        }
        assertEquals(1, HomeProjection.items(JsonParser.parseString("""{"books":[{"id":1,"title":"Fixture"}]}"""), "books")?.size)
    }
    @Test fun landscapeAndLargeTextKeepNavigationReachableWithoutShrinkingType() {
        assertTrue(AtharNavigationLayoutPolicy.resolve(640f, 320f, 1f).rail)
        assertFalse(AtharNavigationLayoutPolicy.resolve(390f, 740f, 2f).rail)
        assertTrue(AtharNavigationLayoutPolicy.resolve(390f, 740f, 2f).minimumBarHeightDp >= 120)
        assertTrue(AtharNavigationLayoutPolicy.resolve(900f, 700f, 1f).rail)
    }
}
