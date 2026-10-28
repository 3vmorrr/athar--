package org.athar.app.books

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors

/** Only bounded raw pixels cross back; the app never parses a renderer-supplied image format. */
internal class BookPdfClient(private val context: Context, private val authorize: () -> Unit) : AutoCloseable {
    private val token = UUID.randomUUID().toString()
    private val ready = CompletableDeferred<IBookPdfRenderer>()
    @Volatile private var remote: IBookPdfRenderer? = null
    @Volatile private var closed = false
    @Volatile private var pipe: ParcelFileDescriptor? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            val renderer = IBookPdfRenderer.Stub.asInterface(binder)
            remote = renderer; ready.complete(renderer)
        }
        override fun onServiceDisconnected(name: ComponentName) { remote = null; ready.completeExceptionally(IOException("pdf_renderer_disconnected")) }
        override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
        override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IOException("pdf_renderer_unavailable")) }
    }

    suspend fun open(document: ParcelFileDescriptor): Int {
        authorize()
        if (!bound) {
            val intent = Intent(context, BookPdfService::class.java)
            bound = if (android.os.Build.VERSION.SDK_INT >= 29) context.bindIsolatedService(intent, Context.BIND_AUTO_CREATE, "book_" + token.replace("-", ""), context.mainExecutor, connection)
            else context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) throw IOException("pdf_renderer_unavailable")
        }
        val renderer = withTimeout(15_000) { ready.await() }
        return withContext(Dispatchers.IO) { authorize(); check(!closed); renderer.open(token, document) }
    }

    suspend fun render(page: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        authorize(); check(!closed)
        val descriptor = checkNotNull(remote).render(token, page, width.coerceIn(1, 1800))
        pipe = descriptor
        try {
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(descriptor)).use { input ->
                val w = input.readInt(); val h = input.readInt()
                require(w in 1..1800 && h in 1..3600)
                val bytes = ByteArray(w * h * 4)
                input.readFully(bytes)
                authorize(); check(!closed)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try { bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes)); bitmap }
                catch (failure: Exception) { bitmap.recycle(); throw failure }
            }
        } finally { runCatching { descriptor.close() }; if (pipe === descriptor) pipe = null }
    }

    fun cancelRender() { runCatching { pipe?.close() }; pipe = null }
    override fun close() {
        closed = true; cancelRender()
        val renderer = remote; remote = null
        if (renderer != null) closer.execute { runCatching { renderer.close(token) } }
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
    }

    companion object { private val closer = Executors.newSingleThreadExecutor() }
}
