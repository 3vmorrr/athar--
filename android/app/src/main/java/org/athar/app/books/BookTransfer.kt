package org.athar.app.books

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal class BookTransferRejected : IOException("book_transfer_rejected")
internal class BookStorageFull : IOException("book_storage_full")

/** No API bearer/cookie client is shared with a book host. DNS and redirects stay public. */
internal class BookTransfer(private val client: OkHttpClient = publicClient) {
    suspend fun download(files: BookFiles, book: Int, request: String, source: String, progress: suspend (Int) -> Unit = {}): BookCopy = withContext(Dispatchers.IO) {
        if (!BookDownloadPolicy.safeHttps(source)) throw BookTransferRejected()
        check(files.isActive(book, request)) { "book_request_changed" }
        files.current(book)?.takeIf { it.token == request && files.verify(it) }?.let { return@withContext it }
        var current = source
        var redirects = 0
        try {
            while (true) {
                coroutineContext.ensureActive()
                check(files.isActive(book, request)) { "book_request_changed" }
                client.newCall(Request.Builder().url(current).get().build()).execute().use { response ->
                    if (response.code in 300..399) {
                        val location = response.header("Location") ?: throw BookTransferRejected()
                        if (redirects++ >= BookDownloadPolicy.MAX_REDIRECTS) throw BookTransferRejected()
                        current = runCatching { URI(current).resolve(location).toString() }.getOrElse { throw BookTransferRejected() }
                        if (!BookDownloadPolicy.safeHttps(current)) throw BookTransferRejected()
                    } else {
                        if (response.code == 408 || response.code == 429 || response.code >= 500) throw IOException("book_provider_unavailable")
                        if (!response.isSuccessful) throw BookTransferRejected()
                        val body = response.body ?: throw BookTransferRejected()
                        val declared = body.contentLength()
                        if (declared > BookDownloadPolicy.MAX_BYTES) throw BookTransferRejected()
                        if (declared >= 0 && declared > files.freeBytes() - BookDownloadPolicy.MIN_FREE_BYTES) throw BookStorageFull()
                        var total = 0L
                        var lastProgress = -1
                        var nextOwnershipCheck = 0L
                        files.openPart(book, request).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    if (total >= nextOwnershipCheck) {
                                        check(files.isActive(book, request)) { "book_request_changed" }
                                        if (files.freeBytes() < BookDownloadPolicy.MIN_FREE_BYTES) throw BookStorageFull()
                                        nextOwnershipCheck = total + 1024 * 1024
                                    }
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue
                                    total += read
                                    if (total > BookDownloadPolicy.MAX_BYTES) throw BookTransferRejected()
                                    output.write(buffer, 0, read)
                                    val percent = if (declared > 0) ((total * 100) / declared).toInt().coerceIn(0, 99) else -1
                                    if (percent != lastProgress) { progress(percent); lastProgress = percent }
                                }
                            }
                            output.fd.sync()
                        }
                        if (declared >= 0 && total != declared) throw BookTransferRejected()
                        val context = coroutineContext
                        return@withContext files.publish(book, request) { context.ensureActive() }.also { progress(100) }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } finally { files.discardPart(book, request) }
    }

    companion object {
        private val publicClient by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(6, TimeUnit.HOURS)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                        if (addresses.isEmpty() || addresses.any { !BookDownloadPolicy.publicAddress(it) }) throw UnknownHostException("book_destination_not_public")
                    }
                }).build()
        }
    }
}
