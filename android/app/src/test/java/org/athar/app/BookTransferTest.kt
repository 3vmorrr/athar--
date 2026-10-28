package org.athar.app

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.athar.app.books.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BookTransferTest {
    private fun client(reply: (Request) -> Response): OkHttpClient = OkHttpClient.Builder().addInterceptor { reply(it.request()) }.build()
    private fun response(request: Request, code: Int = 200, text: String = "%PDF-1.4\nfixture", location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(text.toResponseBody("application/pdf".toMediaType())).apply { if (location != null) header("Location", location) }.build()

    @Test fun publicRedirectPublishesOnlyCompleteBytesAndRestartDoesNotRedownloadAnAcknowledgedCopy() = runBlocking {
        val root = Files.createTempDirectory("athar-transfer-test").toFile()
        try {
            val files = BookFiles(root, BookScope("0123456789ab", 1)); val token = files.begin(1)
            var calls = 0; val progress = mutableListOf<Int>()
            val transfer = BookTransfer(client { request -> calls++; if (request.url.encodedPath == "/start") response(request, 302, location = "/book.pdf") else response(request) })
            val copy = transfer.download(files, 1, token, "https://books.example.invalid/start") { progress += it }
            assertEquals(2, calls); assertTrue(files.verify(copy)); assertEquals(100, progress.last())
            assertEquals(copy, transfer.download(files, 1, token, "https://books.example.invalid/start")); assertEquals(2, calls)
        } finally { root.deleteRecursively() }
    }

    @Test fun unsafeRedirectAndProviderFailureNeverReplaceTheExistingCopy() = runBlocking {
        val root = Files.createTempDirectory("athar-transfer-test").toFile()
        try {
            val files = BookFiles(root, BookScope("0123456789ab", 1)); val first = files.begin(1)
            files.openPart(1, first).use { it.write("%PDF-1.4\nprior".toByteArray()) }; val prior = files.publish(1, first)
            for ((code, destination) in listOf(302 to "http://unsafe.example.invalid/a.pdf", 302 to "https://127.0.0.1/a.pdf", 403 to null, 503 to null)) {
                val next = files.begin(1); var calls = 0
                val transfer = BookTransfer(client { request -> calls++; response(request, code, location = destination) })
                assertTrue(runCatching { transfer.download(files, 1, next, "https://books.example.invalid/book.pdf") }.isFailure)
                assertEquals(1, calls); assertEquals(prior, files.current(1)); assertTrue(files.verify(prior))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationBeforePublicationAndBadPdfBytesCannotClaimDownloadSuccess() = runBlocking {
        val root = Files.createTempDirectory("athar-transfer-test").toFile()
        try {
            val files = BookFiles(root, BookScope("0123456789ab", 1)); val first = files.begin(1)
            val transfer = BookTransfer(client { response(it) })
            assertTrue(runCatching { transfer.download(files, 1, first, "https://books.example.invalid/book.pdf") { if (it < 100) files.cancel(1) } }.isFailure)
            assertNull(files.current(1))
            val next = files.begin(1)
            assertTrue(runCatching { BookTransfer(client { response(it, text = "not PDF") }).download(files, 1, next, "https://books.example.invalid/book.pdf") }.isFailure)
            assertNull(files.current(1))
        } finally { root.deleteRecursively() }
    }
}
