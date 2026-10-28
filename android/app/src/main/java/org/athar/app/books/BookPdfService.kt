package org.athar.app.books

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

/** Receives only verified read-only descriptors; isolated UID has no app files or network access. */
class BookPdfService : Service() {
    private val sessions = mutableMapOf<String, PdfRenderer>()
    private val pixels = Executors.newSingleThreadExecutor()
    private fun authorize() { check(Binder.getCallingUid() == applicationInfo.uid) { "pdf_caller_denied" } }
    private fun token(value: String) { require(UUID.fromString(value).toString() == value) }
    private val binder = object : IBookPdfRenderer.Stub() {
        override fun open(key: String, document: ParcelFileDescriptor): Int {
            var accepted = false
            try {
                authorize(); token(key)
                synchronized(sessions) {
                // The authorized parent opens MODE_READ_ONLY on every API. The extra descriptor
                // flag inspection became public in API30; never call it on supported API26–29.
                if (Build.VERSION.SDK_INT >= 30) require((Os.fcntlInt(document.fileDescriptor, OsConstants.F_GETFL, 0) and OsConstants.O_ACCMODE) == OsConstants.O_RDONLY)
                sessions.remove(key)?.close()
                check(sessions.size < 4) { "pdf_session_limit" }
                val renderer = try { PdfRenderer(document) } catch (failure: Exception) { document.close(); throw IllegalStateException("pdf_open_failed", failure) }
                if (renderer.pageCount < 1) { renderer.close(); throw IllegalStateException("pdf_empty") }
                sessions[key] = renderer
                accepted = true
                return renderer.pageCount
                }
            } finally {
                if (!accepted) runCatching { document.close() }
            }
        }

        override fun render(key: String, page: Int, width: Int): ParcelFileDescriptor {
            authorize(); token(key); require(width in 1..1800)
            val bitmap = synchronized(sessions) {
                val renderer = checkNotNull(sessions[key]) { "pdf_session_missing" }
                require(page in 0 until renderer.pageCount)
                renderer.openPage(page).use { source ->
                    require(source.width > 0 && source.height > 0)
                    val scale = min(width.toDouble() / source.width, 3600.0 / source.height)
                    val bitmap = Bitmap.createBitmap((source.width * scale).roundToInt().coerceIn(1, 1800), (source.height * scale).roundToInt().coerceIn(1, 3600), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    } catch (failure: Exception) {
                        bitmap.recycle(); throw IllegalStateException("pdf_render_failed", failure)
                    }
                }
            }
            val pipe = try { ParcelFileDescriptor.createPipe() } catch (failure: Exception) { bitmap.recycle(); throw failure }
            try {
                pixels.execute {
                    try {
                        DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).use { output ->
                            require(bitmap.byteCount == bitmap.width * bitmap.height * 4)
                            val buffer = ByteBuffer.allocate(bitmap.byteCount)
                            bitmap.copyPixelsToBuffer(buffer)
                            output.writeInt(bitmap.width); output.writeInt(bitmap.height); output.write(buffer.array())
                        }
                    } finally { bitmap.recycle(); runCatching { pipe[1].close() } }
                }
            } catch (failure: Exception) { bitmap.recycle(); pipe.forEach { it.close() }; throw failure }
            return pipe[0]
        }

        override fun close(key: String) { authorize(); synchronized(sessions) { sessions.remove(key)?.close() } }
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        synchronized(sessions) { sessions.values.forEach { runCatching { it.close() } }; sessions.clear() }
        pixels.shutdownNow()
        super.onDestroy()
    }
}
