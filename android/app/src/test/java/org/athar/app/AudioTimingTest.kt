package org.athar.app

import org.athar.app.media.AyahTiming
import org.athar.app.media.QuranAudioSource
import org.athar.app.media.timingForAyah
import org.athar.app.media.validatedAyahTimings
import org.junit.Assert.*
import org.junit.Test

class AudioTimingTest {
    @Test fun validIntervalsRetainExactClipBoundariesAndStableAyahOrder() {
        val table = validatedAyahTimings(listOf(AyahTiming(2, 1200, 2000), AyahTiming(1, 0, 1000)), 7)
        assertEquals(listOf(1, 2), table.map { it.ayah })
        assertEquals(AyahTiming(2, 1200, 2000), timingForAyah(table, 2))
        assertNull(timingForAyah(table, 3)) // gaps never fabricate ayah timing
    }

    @Test fun malformedIntervalsNeverClipOrHighlightTheWrongAyah() {
        for (rows in listOf(
            listOf(AyahTiming(1, -1, 100)),
            listOf(AyahTiming(1, 100, 100)),
            listOf(AyahTiming(8, 0, 100)),
            listOf(AyahTiming(1, 0, 100), AyahTiming(1, 100, 200)),
            listOf(AyahTiming(1, 0, 200), AyahTiming(2, 199, 300)),
            listOf(AyahTiming(1, 0, Long.MAX_VALUE)),
        )) assertTrue(validatedAyahTimings(rows, 7).isEmpty())
    }

    @Test fun retainedMediaIdentityDistinguishesWholeSurahFromAyahAndRadio() {
        assertEquals(QuranAudioSource(7, 20, 2), QuranAudioSource.fromKey("7:20:2:0"))
        assertEquals(QuranAudioSource(7, 20, 2, 255), QuranAudioSource.fromKey("7:20:2:255"))
        for (key in listOf("radio:7", "07:20:2:0", "7:20:115:0", "7:20:2:287", "7:20:2:-1", "https://invalid.example"))
            assertNull(QuranAudioSource.fromKey(key))
    }
}
