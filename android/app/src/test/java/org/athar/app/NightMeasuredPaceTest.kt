package org.athar.app

import org.athar.app.nightprayer.NightRules
import org.junit.Assert.*
import org.junit.Test

class NightMeasuredPaceTest {
    @Test fun averageWeightsByMeasuredVersesRatherThanAveragingSessionSpeeds() {
        val pace = NightRules.measuredPace(listOf(10 to 5, 20 to 20))!!
        assertEquals(2, pace.sessions); assertEquals(30L, pace.verses); assertEquals(25L, pace.seconds)
        assertEquals(25.0 / 30, pace.secondsPerVerse, 0.000001)
        assertEquals(pace.secondsPerVerse, NightRules.pace(listOf(10 to 5, 20 to 20))!!, 0.0)
    }
    @Test fun unmeasuredOrOutOfContractSamplesCannotInventOrDistortPace() {
        val invalid = listOf(1000 to 0, 10 to -1, 0 to 60, -1 to 60, 100001 to 60, 10 to 86401, Int.MAX_VALUE to Int.MAX_VALUE)
        assertNull(NightRules.measuredPace(invalid)); assertNull(NightRules.pace(emptyList()))
        val pace = NightRules.measuredPace(invalid + (10 to 5))!!
        assertEquals(1, pace.sessions); assertEquals(0.5, pace.secondsPerVerse, 0.0)
    }
    @Test fun validUpperBoundsKeepLongTotals() {
        val pace = NightRules.measuredPace(List(30000) { 100000 to 86400 })!!
        assertEquals(3_000_000_000L, pace.verses); assertEquals(2_592_000_000L, pace.seconds)
        assertEquals(0.864, pace.secondsPerVerse, 0.000001)
    }
}
