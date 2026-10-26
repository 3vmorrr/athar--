package org.athar.app

import org.athar.app.notifications.*
import org.junit.Assert.*
import org.junit.Test

class PushDeviceLifecycleTest {
    @Test fun installationIdsAreOpaqueStableFormatCandidates() {
        val first=PushInstallationContract.generate()
        val second=PushInstallationContract.generate()
        assertTrue(PushInstallationContract.valid(first))
        assertTrue(PushInstallationContract.valid(second))
        assertNotEquals(first,second)
        assertFalse(PushInstallationContract.valid("../device"))
    }

    @Test fun tokenStateRejectsMalformedProviderTokens() {
        val valid=DevicePushTokenState(
            token="token_12345678901234567890",
            updatedAtMillis=1,
        )
        assertSame(valid,valid.validated())
        assertTrue(runCatching{valid.copy(token="short").validated()}.isFailure)
        assertTrue(runCatching{valid.copy(provider="other").validated()}.isFailure)
    }

    @Test fun retryPolicyIsBoundedAndOnlyForTransientHttpStatuses() {
        assertTrue(PushDeviceSyncPolicy.shouldRetry(429,0))
        assertTrue(PushDeviceSyncPolicy.shouldRetry(503,4))
        assertFalse(PushDeviceSyncPolicy.shouldRetry(503,5))
        assertFalse(PushDeviceSyncPolicy.shouldRetry(401,0))
        assertFalse(PushDeviceSyncPolicy.shouldRetry(409,0))
    }
}
