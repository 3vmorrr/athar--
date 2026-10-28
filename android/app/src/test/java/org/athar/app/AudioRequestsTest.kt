package org.athar.app

import org.athar.app.media.AudioRequests
import org.junit.Assert.*
import org.junit.Test

class AudioRequestsTest {
    @Test fun selectionChangeRejectsTheOldResponseEvenIfCancellationDidNotStopTransport() {
        val requests = AudioRequests()
        val old = requests.begin("1:1:2:0")
        assertTrue(requests.selectionChanged("1:1:3:0"))
        assertFalse(requests.current(old))
        val chosen = requests.begin("1:1:3:0")
        assertTrue(requests.current(chosen))
        assertFalse(requests.current(old))
    }
    @Test fun followOnlyChangesAndBrowsingQuranDoNotCancelAnExplicitRadioRequest() {
        val requests = AudioRequests()
        val recitation = requests.begin("1:1:2:0")
        assertFalse(requests.selectionChanged("1:1:2:0"))
        assertTrue(requests.current(recitation))
        val radio = requests.begin("radio:7")
        assertFalse(requests.selectionChanged("1:1:3:0"))
        assertTrue(requests.current(radio))
    }
    @Test fun stopAndReplacementFenceAllPreviousReplies() {
        val requests = AudioRequests()
        val first = requests.begin("1:1:1:0")
        val replacement = requests.begin("radio:8")
        assertFalse(requests.current(first))
        requests.begin(null)
        assertFalse(requests.current(replacement))
    }
}
