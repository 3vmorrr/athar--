package org.athar.app

import org.athar.app.media.PlaybackOwnership
import org.junit.Assert.*
import org.junit.Test

class PlaybackOwnershipTest {
    @Test fun accountSwitchAndLogoutInvalidateTheOldQueueOwner() {
        val access = PlaybackOwnership()
        assertTrue(access.update(1))
        assertFalse(access.update(1)) // profile/offline updates for the same account
        assertTrue(access.allows(100, 100, false, 1))
        assertTrue(access.update(2))
        assertFalse(access.allows(100, 100, false, 1))
        assertTrue(access.allows(100, 100, false, 2))
        assertTrue(access.update(null))
        assertFalse(access.allows(100, 100, true, 2))
    }

    @Test fun forgedOwnerHintCannotAuthorizeAnUntrustedUid() {
        val access = PlaybackOwnership().apply { update(2) }
        assertFalse(access.allows(100, 999, false, 2))
        assertFalse(access.allows(100, 999, false, null))
        assertFalse(access.allows(100, 100, false, -1))
    }

    @Test fun frameworkNotificationAndTrustedSystemControlsRemainAvailable() {
        val access = PlaybackOwnership().apply { update(1) }
        assertTrue(access.allows(100, 100, false, null))
        assertTrue(access.allows(100, 1000, true, null))
        assertTrue(access.update(2))
        assertTrue(access.allows(100, 1000, true, null))
        assertFalse(access.allows(100, 1000, true, 1))
    }

    @Test fun missingOrInvalidAccountsNeverGrantPlayback() {
        val access = PlaybackOwnership()
        assertFalse(access.allows(100, 100, true, null))
        access.update(0)
        assertFalse(access.allows(100, 100, true, null))
    }

    @Test fun systemControlsCannotReplaceTheAuthenticatedMediaSource() {
        val access = PlaybackOwnership().apply { update(1) }
        assertTrue(access.allows(100, 1000, true, null))
        assertFalse(access.allowsMediaReplacement(100, 1000, true, null))
        assertTrue(access.allowsMediaReplacement(100, 100, false, 1))
        access.update(2)
        assertFalse(access.allowsMediaReplacement(100, 100, false, 1))
    }
}
