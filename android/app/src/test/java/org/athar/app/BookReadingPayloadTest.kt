package org.athar.app

import org.athar.app.books.*
import org.junit.Assert.*
import org.junit.Test

class BookReadingPayloadTest {
    private val scope = BookScope("0123456789ab", 1)
    private val digest = "a".repeat(64)
    @Test fun readingPositionIsBoundToOwnerEnvironmentBookAndExactDocument() {
        val text = BookReadingPayload.encode(scope, 7, digest, 19)
        assertEquals(19, BookReadingPayload.decode(text, scope, 7, digest, 100))
        for ((owner, book, hash) in listOf(Triple(BookScope(scope.namespace, 2), 7, digest), Triple(BookScope("abcdef012345", 1), 7, digest), Triple(scope, 8, digest), Triple(scope, 7, "b".repeat(64))))
            assertTrue(runCatching { BookReadingPayload.decode(text, owner, book, hash, 100) }.isFailure)
    }
    @Test fun malformedOrOutOfBoundsProgressDoesNotBecomePageZero() {
        val text = BookReadingPayload.encode(scope, 7, digest, 19)
        assertTrue(runCatching { BookReadingPayload.decode(text, scope, 7, digest, 10) }.isFailure)
        for (bad in listOf("{}", text.replace("\"page\":19", "\"page\":-1"), text.replace("\"page\":19", "\"page\":\"invalid\"")))
            assertTrue(runCatching { BookReadingPayload.decode(bad, scope, 7, digest, 100) }.isFailure)
    }
}
