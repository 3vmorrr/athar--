package org.athar.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.athar.app.core.data.*
import org.junit.Assert.*
import org.junit.Test

class ProtectedReadTest {
    @Test fun absentDataIsDifferentFromAnUnreadableRecordWithoutExposingExceptionDetails()=runTest {
        val values=flow<String?>{emit(null);throw java.io.IOException("Private diagnostic fixture")}.protectedReads().toList()
        assertEquals(listOf(ProtectedRead.Ready(null),ProtectedRead.Unreadable),values)
        assertFalse(values.toString().contains("Private diagnostic fixture"))
    }
    @Test fun cancellationIsPropagatedInsteadOfBecomingARecoveryPrompt()=runTest {
        val cancellation=CancellationException("Fixture cancellation")
        val failure=runCatching { flow<Int>{throw cancellation}.protectedReads().collect{fail("Cancellation must not emit fallback data")} }.exceptionOrNull()
        assertSame(cancellation,failure)
    }
}
