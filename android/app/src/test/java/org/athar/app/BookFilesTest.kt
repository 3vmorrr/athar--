package org.athar.app

import org.athar.app.books.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class BookFilesTest {
    private fun fixture(test: (java.io.File, BookFiles) -> Unit) {
        val root = Files.createTempDirectory("athar-book-test").toFile()
        try { test(root, BookFiles(root, BookScope("0123456789ab", 1))) }
        finally { root.deleteRecursively() }
    }
    private fun write(files: BookFiles, book: Int, request: String, contents: String = "%PDF-1.4\nfixture") {
        files.openPart(book, request).use { it.write(contents.toByteArray()); it.fd.sync() }
    }
    @Test fun immutableCopySurvivesReopenAndAFailedReplacementPreservesThePreviousManifest() = fixture { root, files ->
        val first = files.begin(1); write(files, 1, first); val copy = files.publish(1, first)
        assertTrue(files.verify(copy))
        val reopened = BookFiles(root, files.scope); assertEquals(copy, reopened.current(1))
        val replacement = files.begin(1); write(files, 1, replacement, "not a PDF")
        assertTrue(runCatching { files.publish(1, replacement) }.isFailure)
        assertEquals(copy, reopened.current(1)); assertTrue(reopened.verify(copy))
        val temporary = java.io.File(copy.file.parentFile, "$replacement.part")
        assertTrue(temporary.exists())
        reopened.begin(1)
        assertFalse(temporary.exists()); assertEquals(copy, reopened.current(1))
    }
    @Test fun namespaceOwnerAndBookBoundariesRemainIndependent() = fixture { root, files ->
        val otherOwner = BookFiles(root, BookScope("0123456789ab", 2))
        val otherServer = BookFiles(root, BookScope("abcdef012345", 1))
        listOf(files, otherOwner, otherServer).forEachIndexed { index, store ->
            val book = index + 1
            val request = store.begin(book); write(store, book, request); store.publish(book, request)
        }
        assertEquals(listOf(1), files.knownBooks()); assertEquals(listOf(2), otherOwner.knownBooks()); assertEquals(listOf(3), otherServer.knownBooks())
        files.remove(1)
        assertTrue(files.knownBooks().isEmpty()); assertNull(files.current(1)); assertNotNull(otherOwner.current(2)); assertNotNull(otherServer.current(3))
    }
    @Test fun cancellationAndReplacementFenceAnOldWorkerEvenIfTransportIgnoresCancellation() = fixture { _, files ->
        val first = files.begin(1); write(files, 1, first)
        files.cancel(1)
        assertFalse(files.isActive(1, first)); assertTrue(runCatching { files.publish(1, first) }.isFailure)
        val second = files.begin(1); write(files, 1, second)
        val third = files.begin(1)
        assertTrue(runCatching { files.publish(1, second) }.isFailure)
        write(files, 1, third); assertTrue(files.verify(files.publish(1, third)))
    }
    @Test fun checksumAndManifestFailuresPreserveBytesUntilExplicitRemoval() = fixture { _, files ->
        val request = files.begin(1); write(files, 1, request); val copy = files.publish(1, request)
        copy.file.appendText("tampered")
        assertFalse(files.verify(copy)); assertTrue(copy.file.exists())
        java.io.File(copy.file.parentFile, "manifest").writeText("damaged")
        assertTrue(runCatching { files.current(1) }.isFailure); assertTrue(copy.file.exists())
    }
    @Test fun accountRevocationDuringHashingCannotResurrectAFileOrTouchAnotherAccount() = fixture { root, files ->
        val other = BookFiles(root, BookScope("0123456789ab", 2))
        val keep = other.begin(1); write(other, 1, keep); other.publish(1, keep)
        val request = files.begin(1); write(files, 1, request)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var failed = false
        val worker = thread {
            failed = runCatching { files.publish(1, request) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) } }.isFailure
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        // Windows cannot unlink the hash input while open; revocation must still fence publication.
        val cleanup = runCatching { files.revoke() }
        assertTrue(cleanup.isSuccess || cleanup.exceptionOrNull() is java.io.IOException)
        release.countDown(); worker.join(5000)
        BookFiles.recoverRevoked(root)
        assertFalse(worker.isAlive); assertTrue(failed)
        assertFalse(java.io.File(root, files.scope.key).exists())
        assertTrue(runCatching { files.begin(2) }.isFailure)
        assertNotNull(other.current(1))
    }
}
