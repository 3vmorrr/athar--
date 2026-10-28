package org.athar.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.athar.app.books.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BookPdfStorageTest {
    @Test fun verifiedReadOnlyCopyRendersThroughTheInstalledIsolatedPrivateService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "qa-books-${UUID.randomUUID()}").apply { mkdirs() }
        val files = BookFiles(root, BookScope("0123456789ab", 1))
        val client = BookPdfClient(context) {}
        try {
            val request = files.begin(1)
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 420, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(20f, 20f, 100f, 100f, Paint().apply { color = Color.BLACK })
                document.finishPage(page)
                files.openPart(1, request).use { document.writeTo(it); it.fd.sync() }
            } finally { document.close() }
            val copy = files.publish(1, request)
            assertTrue(files.verify(copy))
            val descriptor = files.openVerified(1) { ParcelFileDescriptor.open(it.file, ParcelFileDescriptor.MODE_READ_ONLY) }
            try { assertEquals(1, client.open(descriptor)) } finally { descriptor.close() }
            val bitmap = client.render(0, 300)
            try {
                assertEquals(300, bitmap.width); assertEquals(420, bitmap.height)
                assertEquals(Color.BLACK, bitmap.getPixel(50, 50))
            } finally { bitmap.recycle() }
            assertTrue(runCatching { client.render(1, 300) }.isFailure)
            @Suppress("DEPRECATION") val service = context.packageManager.getServiceInfo(ComponentName(context, BookPdfService::class.java), 0)
            assertFalse(service.exported)
            assertTrue(service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
            // Corrupt bytes remain, but no read capability can be issued from the verified-open path.
            copy.file.appendText("corrupt")
            var opened = false
            assertTrue(runCatching { files.openVerified(1) { opened = true } }.isFailure)
            assertFalse(opened); assertTrue(copy.file.exists())
        } finally {
            client.close()
            check(root.parentFile == context.cacheDir && root.name.startsWith("qa-books-"))
            root.deleteRecursively()
        }
    }
}
