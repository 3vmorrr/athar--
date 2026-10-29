package org.athar.app

import org.athar.app.books.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BookCompletionJournalTest {
    private fun fixture(test: (File, BookScope, BookFiles) -> Unit) {
        val root = Files.createTempDirectory("book-completion-").toFile()
        val scope = BookScope("0123456789ab", 1)
        val files = BookFiles(root, scope) { book, request, hash -> "opaque-receipt:$book:$request:$hash:2026-10-29" }
        try { test(root, scope, files) } finally { root.deleteRecursively() }
    }
    private fun publish(files: BookFiles, book: Int = 1): BookCopy {
        val request = files.begin(book)
        files.openPart(book, request).use { it.write("%PDF-fixture-$request".toByteArray()); it.fd.sync() }
        return files.publish(book, request)
    }
    @Test fun legacyManifestNeverInventsACompletionAndRemainsReadable() = fixture { root, scope, files ->
        val legacy = BookFiles(root, scope); val copy = publish(legacy)
        assertTrue(files.completionReceipts().isEmpty()); assertEquals(copy, files.current(1)); assertTrue(files.verify(copy))
    }
    @Test fun committedManifestRecoversAHandoffMissingAtProcessDeathWithoutChangingItsReceipt() = fixture { root, scope, files ->
        val copy = publish(files)
        val expected = files.completionReceipts()
        assertEquals(1, expected.size)
        assertTrue(File(root, "${scope.key}/completion-receipts/${copy.token}").delete())
        val reopened = BookFiles(root, scope)
        assertEquals(expected, reopened.completionReceipts()); assertTrue(reopened.verify(copy))
        // Repeated publication of one request reuses its verified commit and original date.
        assertEquals(copy, reopened.publish(1, copy.token)); assertEquals(expected, reopened.completionReceipts())
    }
    @Test fun overwriteAndExplicitBookRemovalPreserveEveryPendingCommittedReceipt() = fixture { root, scope, files ->
        val first = publish(files); val firstReceipt = files.completionReceipts()[first.token]
        val second = publish(files)
        assertFalse(first.file.exists()); assertTrue(second.file.exists())
        assertEquals(firstReceipt, files.completionReceipts()[first.token]); assertEquals(2, files.completionReceipts().size)
        files.remove(1)
        val reopened = BookFiles(root, scope)
        assertTrue(reopened.knownBooks().isEmpty()); assertEquals(setOf(first.token,second.token),reopened.completionReceipts().keys)
        reopened.acknowledgeCompletion(1,first.token); reopened.acknowledgeCompletion(1,second.token)
        assertTrue(BookFiles(root,scope).completionReceipts().isEmpty())
    }
    @Test fun acknowledgementClearsPromotionSourceBeforeUnlinkAndNeverChangesCopyOrReadingIdentity() = fixture { root, scope, files ->
        val copy = publish(files); val receipt = files.completionReceipts().getValue(copy.token)
        files.acknowledgeCompletion(1,copy.token)
        assertEquals(copy,files.current(1)); assertTrue(files.verify(copy))
        assertTrue(BookFiles(root,scope).completionReceipts().isEmpty())
        // Equivalent to a process stopping after the manifest downgrade but before unlink.
        File(root,"${scope.key}/completion-receipts/${copy.token}").writeText(receipt)
        val reopened = BookFiles(root,scope)
        assertEquals(receipt,reopened.completionReceipts()[copy.token])
        reopened.acknowledgeCompletion(1,copy.token); reopened.acknowledgeCompletion(1,copy.token)
        assertTrue(BookFiles(root,scope).completionReceipts().isEmpty()); assertEquals(copy,reopened.current(1))
    }
    @Test fun invalidCancelledPausedAndRevokedCopiesCannotCreateReceiptsOrAffectOtherOwner() = fixture { root, scope, files ->
        val request = files.begin(1)
        files.openPart(1,request).use { it.write("invalid".toByteArray()) }
        assertTrue(runCatching { files.publish(1,request) }.isFailure); assertTrue(files.completionReceipts().isEmpty())
        val copy = publish(files)
        val other = BookFiles(root,BookScope(scope.namespace,2)) { _,token,_ -> "other-opaque-$token" }
        val keep = publish(other)
        val pause = java.util.UUID.randomUUID().toString();files.pauseDeletion(pause)
        assertTrue(runCatching { files.completionReceipts() }.isFailure)
        assertTrue(runCatching { files.acknowledgeCompletion(1,copy.token) }.isFailure)
        files.resumeDeletion(pause);assertEquals(1,files.completionReceipts().size)
        files.revoke();assertTrue(runCatching { files.completionReceipts() }.isFailure)
        assertEquals(1,other.completionReceipts().size);assertTrue(other.verify(keep))
    }
    @Test fun unreadableHandoffIsPreservedAndCannotBeAcknowledgedByUntrustedMetadata() = fixture { root, scope, files ->
        val copy = publish(files)
        val receipt = File(root,"${scope.key}/completion-receipts/${copy.token}");receipt.writeText("broken\nreceipt")
        assertTrue(runCatching { files.completionReceipts() }.isFailure)
        assertEquals("broken\nreceipt",receipt.readText());assertTrue(copy.file.exists())
        assertTrue(runCatching { files.acknowledgeCompletion(1,"../../escape") }.isFailure)
        assertTrue(receipt.exists());assertTrue(files.verify(copy))
    }
}
