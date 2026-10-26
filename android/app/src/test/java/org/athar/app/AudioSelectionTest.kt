package org.athar.app

import com.google.gson.JsonParser
import org.athar.app.media.AudioSelection
import org.junit.Assert.*
import org.junit.Test

class AudioSelectionTest {
    private val reader = JsonParser.parseString("""{"id":7,"editions":[{"id":10,"surahs":[1]},{"id":20,"surahs":[1,2]},{"id":30,"surahs":[3]}]}""")
    @Test fun changingSurahRetainsSupportedEditionAndNeverLoadsAnUnavailableOne() {
        val first = AudioSelection().withReader(reader)
        assertEquals("7:10:1:0", first.mediaKey)
        assertEquals(2, first.supported().size)
        val second = first.copy(ayah = 5).withSurah(2)
        assertEquals("7:20:2:0", second.mediaKey)
        assertEquals(1, second.ayah)
        assertSame(reader, second.reader)
        assertEquals("7:20:1:0", second.withSurah(1).mediaKey)
        assertNull(first.withSurah(4).mediaKey)
    }
    @Test fun verseAndWholeSurahHaveDifferentMediaIdentityButFollowDoesNotRestartAudio() {
        val full = AudioSelection().withReader(reader)
        assertEquals(full.mediaKey, full.copy(follow = true).mediaKey)
        val verse = full.copy(verseMode = true, ayah = 3)
        assertEquals("7:10:1:3", verse.mediaKey)
        assertNotEquals(full.mediaKey, verse.mediaKey)
        assertEquals("7:10:1:4", verse.copy(ayah = 4).mediaKey)
    }
}
