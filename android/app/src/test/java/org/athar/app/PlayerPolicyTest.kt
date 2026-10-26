package org.athar.app
import org.athar.app.learning.PlayerPolicy
import org.junit.Test
import org.junit.Assert.*
class PlayerPolicyTest {
    @Test fun onlyCanonicalYouTubeIdsEnterPlayerCommands() {
        assertTrue(PlayerPolicy.validVideo("M7lc1UVf-VE"))
        assertFalse(PlayerPolicy.validVideo("';alert(1)//"))
        assertFalse(PlayerPolicy.validVideo("https://youtu.be/M7lc1UVf-VE"))
        assertThrows(IllegalArgumentException::class.java){PlayerPolicy.youtubeUrl("bad")}
        assertEquals("https://www.youtube.com/watch?v=M7lc1UVf-VE&t=0s",PlayerPolicy.youtubeUrl("M7lc1UVf-VE",-8))
    }
    @Test fun providerSamplesAreFiniteAndIdentityIsInstalledPackage() {
        assertNull(PlayerPolicy.safeTime(Double.NaN));assertNull(PlayerPolicy.safeTime(Double.POSITIVE_INFINITY))
        assertNull(PlayerPolicy.safeTime(-1.0));assertNull(PlayerPolicy.safeTime(86401.0))
        assertEquals(30,PlayerPolicy.safeTime(30.5))
        assertEquals("https://org.athar.app.debug/",PlayerPolicy.origin("org.athar.app.debug"))
        assertThrows(IllegalArgumentException::class.java){PlayerPolicy.origin("a/b<script>")}
    }
}
