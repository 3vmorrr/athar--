package org.athar.app

import org.athar.app.review.ReviewAudioCaptureContract
import org.athar.app.review.ReviewInferenceQueueContract
import org.junit.Assert.*
import org.junit.Test

class ReviewInferenceQueueContractTest {
    @Test fun microphoneInferenceQueueMatchesCallerMemoryContract() {
        assertEquals(
            ReviewAudioCaptureContract.MAX_CHUNKS_HELD_BY_CALLER,
            ReviewInferenceQueueContract.MAX_QUEUED_CHUNKS,
        )
        assertEquals(4, ReviewInferenceQueueContract.MAX_QUEUED_CHUNKS)
        assertEquals(
            6_400,
            ReviewInferenceQueueContract.MAX_QUEUED_SAMPLES,
        )
    }
}
