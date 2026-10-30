package org.athar.app.books

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.AtharStatusPill
import org.athar.app.core.ui.text
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

internal data class LocalBookDownload(
    val bookId: Int,
    val title: String?,
    val sizeBytes: Long,
)

internal object BookStorage {
    private const val BOOKS = "books"
    const val READER_PREFERENCES = "athar-book-reader"

    private fun ownerRoot(context: Context, owner: Long): File =
        File(context.filesDir, "$BOOKS/u-$owner")

    private fun revokedMarker(context: Context, owner: Long): File =
        File(context.filesDir, "$BOOKS/revoked-u-$owner")

    fun ownerRevoked(context: Context, owner: Long): Boolean =
        revokedMarker(context, owner).exists()

    fun revokeOwner(context: Context, owner: Long) {
        require(owner > 0L)
        val root = File(context.filesDir, BOOKS).apply { mkdirs() }
        val marker = File(root, "revoked-u-$owner")
        if (!marker.exists()) {
            marker.writeText("revoked")
        }
    }

    fun deleteOwnerFiles(context: Context, owner: Long) {
        require(owner > 0L)
        val root = ownerRoot(context, owner)
        if (root.exists() && !root.deleteRecursively()) {
            throw java.io.IOException("Unable to remove owned book files")
        }
        val preferences = context.getSharedPreferences(
            READER_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val prefix = "page:$owner:"
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith(prefix) }
                .forEach(::remove)
        }.commit()
    }

    fun directory(context: Context, owner: Long): File {
        require(owner > 0L && !ownerRevoked(context, owner))
        return ownerRoot(context, owner).apply { mkdirs() }
    }

    fun pdf(context: Context, owner: Long, bookId: Int): File =
        File(directory(context, owner), "book-$bookId.pdf")

    fun digest(context: Context, owner: Long, bookId: Int): File =
        File(directory(context, owner), "book-$bookId.sha256")

    private fun titleFile(context: Context, owner: Long, bookId: Int): File =
        File(directory(context, owner), "book-$bookId.title")

    fun temp(context: Context, owner: Long, bookId: Int): File =
        File(directory(context, owner), "book-$bookId.part")

    fun title(context: Context, owner: Long, bookId: Int): String? =
        titleFile(context, owner, bookId)
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun downloaded(context: Context, owner: Long): List<LocalBookDownload> {
        require(owner > 0L)
        if (ownerRevoked(context, owner)) return emptyList()
        val root = ownerRoot(context, owner)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.name.matches(Regex("""book-[1-9][0-9]*\.pdf""")) }
            .mapNotNull { pdf ->
                val id = pdf.name.removePrefix("book-").removeSuffix(".pdf").toIntOrNull()
                    ?: return@mapNotNull null
                val digest = File(root, "book-$id.sha256")
                if (!digest.isFile) return@mapNotNull null
                LocalBookDownload(
                    bookId = id,
                    title = File(root, "book-$id.title")
                        .takeIf { it.isFile }
                        ?.readText(Charsets.UTF_8)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    sizeBytes = pdf.length().coerceAtLeast(0L),
                )
            }
            .sortedBy { it.title?.lowercase() ?: it.bookId.toString() }
            .toList()
    }

    fun writeTitle(
        context: Context,
        owner: Long,
        bookId: Int,
        value: String,
    ) {
        val safe = value
            .filterNot { it.isISOControl() }
            .trim()
            .take(180)
        if (safe.isBlank()) return
        titleFile(context, owner, bookId).writeText(safe, Charsets.UTF_8)
    }

    fun isDownloaded(context: Context, owner: Long, bookId: Int): Boolean =
        pdf(context, owner, bookId).isFile &&
            digest(context, owner, bookId).isFile

    fun remove(context: Context, owner: Long, bookId: Int) {
        temp(context, owner, bookId).delete()
        pdf(context, owner, bookId).delete()
        digest(context, owner, bookId).delete()
        titleFile(context, owner, bookId).delete()
    }

    suspend fun verify(
        context: Context,
        owner: Long,
        bookId: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val pdf = pdf(context, owner, bookId)
        val digest = digest(context, owner, bookId)
        if (!pdf.isFile || !digest.isFile) return@withContext false
        val expected = digest.readText().trim().lowercase()
        if (!expected.matches(Regex("[a-f0-9]{64}"))) return@withContext false
        if (!pdfMagic(pdf)) return@withContext false
        secureHash(pdf) == expected
    }

    private fun pdfMagic(file: File): Boolean {
        if (file.length() < 5L) return false
        return FileInputStream(file).use { input ->
            val bytes = ByteArray(5)
            input.read(bytes) == 5 &&
                bytes.contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D))
        }
    }

    private fun secureHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal object BookDownloadPolicy {
    const val MAX_BYTES = 200L * 1024L * 1024L
    const val MAX_REDIRECTS = 5

    fun safeHttps(value: String): Boolean {
        if (
            value.isBlank() ||
            value.length > 2048 ||
            value.contains('\r') ||
            value.contains('\n')
        ) {
            return false
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return false
        if (uri.port !in listOf(-1, 443)) return false

        val host = uri.host.orEmpty().lowercase()
        if (
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local")
        ) {
            return false
        }

        val literal = host.removePrefix("[").removeSuffix("]")
        val looksIpv4 = literal.matches(Regex("""\d{1,3}(?:\.\d{1,3}){3}"""))
        val looksIpv6 = literal.contains(':')
        if (looksIpv4 || looksIpv6) {
            val address = runCatching { InetAddress.getByName(literal) }.getOrNull()
                ?: return false
            if (!publicAddress(address)) return false
        }
        return true
    }

    fun publicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }
        val bytes = address.address
        if (bytes.size == 16) {
            // fc00::/7 unique-local range.
            val first = bytes[0].toInt() and 0xff
            if (first and 0xfe == 0xfc) return false
        }
        return true
    }
}

private object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        require(addresses.isNotEmpty() && addresses.all(BookDownloadPolicy::publicAddress))
        return addresses
    }
}

class BookDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val owner = inputData.getLong(KEY_OWNER, -1L)
        val bookId = inputData.getInt(KEY_BOOK_ID, -1)
        val source = inputData.getString(KEY_URL).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()

        if (
            owner < 1L ||
            bookId < 1 ||
            !BookDownloadPolicy.safeHttps(source) ||
            BookStorage.ownerRevoked(applicationContext, owner)
        ) {
            return@withContext Result.failure()
        }

        val target = BookStorage.pdf(applicationContext, owner, bookId)
        val temporary = BookStorage.temp(applicationContext, owner, bookId)
        val digestFile = BookStorage.digest(applicationContext, owner, bookId)
        temporary.delete()

        val client = OkHttpClient.Builder()
            .dns(PublicOnlyDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        try {
            var current = source
            var redirects = 0
            var response: okhttp3.Response? = null

            while (true) {
                coroutineContext.ensureActive()
                val next = client.newCall(
                    Request.Builder()
                        .url(current)
                        .get()
                        .build(),
                ).execute()

                if (next.code in 300..399) {
                    val location = next.header("Location")
                    next.close()
                    if (
                        location.isNullOrBlank() ||
                        redirects++ >= BookDownloadPolicy.MAX_REDIRECTS
                    ) {
                        return@withContext Result.failure()
                    }
                    val resolved = runCatching {
                        URI(current).resolve(location).toString()
                    }.getOrNull() ?: return@withContext Result.failure()
                    if (!BookDownloadPolicy.safeHttps(resolved)) {
                        return@withContext Result.failure()
                    }
                    current = resolved
                    continue
                }

                response = next
                break
            }

            val finalResponse = response ?: return@withContext Result.failure()
            finalResponse.use { safeResponse ->
                if (!safeResponse.isSuccessful) {
                    return@withContext if (
                        safeResponse.code == 408 ||
                        safeResponse.code == 429 ||
                        safeResponse.code >= 500
                    ) {
                        retryOrFail()
                    } else {
                        Result.failure()
                    }
                }
                val body = safeResponse.body ?: return@withContext Result.failure()
                val declared = body.contentLength()
                if (declared > BookDownloadPolicy.MAX_BYTES) {
                    return@withContext Result.failure()
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            if (BookStorage.ownerRevoked(applicationContext, owner)) {
                                throw CancellationException("book_owner_revoked")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            total += read
                            if (total > BookDownloadPolicy.MAX_BYTES) {
                                throw IllegalStateException("book_too_large")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)

                            if (declared > 0L) {
                                val progress = ((total * 100L) / declared)
                                    .toInt()
                                    .coerceIn(0, 100)
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                            }
                        }
                    }
                }

                if (total < 5L || !pdfMagic(temporary)) {
                    temporary.delete()
                    return@withContext Result.failure()
                }
                if (BookStorage.ownerRevoked(applicationContext, owner)) {
                    temporary.delete()
                    return@withContext Result.failure()
                }

                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                digestFile.writeText(hash)
                BookStorage.writeTitle(
                    applicationContext,
                    owner,
                    bookId,
                    title,
                )
                setProgress(workDataOf(KEY_PROGRESS to 100))
                Result.success(
                    workDataOf(
                        KEY_PATH to target.absolutePath,
                        KEY_SHA256 to hash,
                    ),
                )
            }
        } catch (failure: CancellationException) {
            temporary.delete()
            throw failure
        } catch (_: Exception) {
            temporary.delete()
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < 3) Result.retry() else Result.failure()

    private fun pdfMagic(file: File): Boolean =
        FileInputStream(file).use { input ->
            val bytes = ByteArray(5)
            input.read(bytes) == 5 &&
                bytes.contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D))
        }

    companion object {
        const val KEY_OWNER = "owner"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_PATH = "path"
        const val KEY_SHA256 = "sha256"
    }
}

private object BookDownloads {
    fun workName(owner: Long, bookId: Int): String =
        "book-download:$owner:$bookId"

    fun enqueue(
        context: Context,
        owner: Long,
        bookId: Int,
        url: String,
        title: String,
    ) {
        require(BookDownloadPolicy.safeHttps(url))
        require(!BookStorage.ownerRevoked(context, owner))
        val request = OneTimeWorkRequestBuilder<BookDownloadWorker>()
            .setInputData(
                workDataOf(
                    BookDownloadWorker.KEY_OWNER to owner,
                    BookDownloadWorker.KEY_BOOK_ID to bookId,
                    BookDownloadWorker.KEY_URL to url,
                    BookDownloadWorker.KEY_TITLE to title.take(180),
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag("book-download-owner:$owner")
            .addTag("book-download-id:$bookId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(owner, bookId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, owner: Long, bookId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(owner, bookId))
    }
}

@Composable
internal fun BookDownloadActions(
    graph: AppGraph,
    owner: Long,
    bookId: Int,
    canDownload: Boolean,
    onOpenReader: () -> Unit,
) {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(owner, bookId) {
        workManager.getWorkInfosForUniqueWorkFlow(
            BookDownloads.workName(owner, bookId),
        )
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val latest = work.lastOrNull()
    val progress = latest?.progress?.getInt(BookDownloadWorker.KEY_PROGRESS, 0) ?: 0
    var localRevision by remember { mutableIntStateOf(0) }
    val downloaded = remember(owner, bookId, latest?.state, localRevision) {
        BookStorage.isDownloaded(context, owner, bookId)
    }
    var requesting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when {
        downloaded -> {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                Button(
                    onClick = onOpenReader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("book-open-reader"),
                ) {
                    Text(stringResource(R.string.book_open))
                }
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("book-remove-download"),
                ) {
                    Text(stringResource(R.string.book_remove_download))
                }
            }
        }

        latest?.state == WorkInfo.State.RUNNING ||
            latest?.state == WorkInfo.State.ENQUEUED ||
            latest?.state == WorkInfo.State.BLOCKED -> {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                LinearProgressIndicator(
                    progress = {
                        if (progress in 1..99) progress / 100f else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AtharStatusPill(
                    if (progress in 1..99) {
                        stringResource(R.string.book_download_progress, progress)
                    } else {
                        stringResource(R.string.book_download_waiting)
                    },
                )
                OutlinedButton(
                    onClick = {
                        BookDownloads.cancel(context, owner, bookId)
                        localRevision++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("book-cancel-download"),
                ) {
                    Text(stringResource(R.string.book_cancel_download))
                }
            }
        }

        canDownload -> {
            Button(
                onClick = {
                    if (requesting) return@Button
                    requesting = true
                    error = false
                    scope.launch {
                        try {
                            graph.session.requireVerifiedOwner(owner)
                            val response = graph.session.request(
                                "GET",
                                "books/$bookId/download",
                                expectedUser = owner,
                            )
                            val url = response.text("url")
                            val title = response.text("title")
                            if (!BookDownloadPolicy.safeHttps(url)) {
                                throw IllegalArgumentException("unsafe_download_url")
                            }
                            BookDownloads.enqueue(
                                context = context,
                                owner = owner,
                                bookId = bookId,
                                url = url,
                                title = title,
                            )
                            localRevision++
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            error = true
                        } finally {
                            requesting = false
                        }
                    }
                },
                enabled = !requesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("book-download"),
            ) {
                Text(
                    stringResource(
                        if (requesting) R.string.loading
                        else R.string.download_book,
                    ),
                )
            }
        }
    }

    if (latest?.state == WorkInfo.State.FAILED || error) {
        Text(
            stringResource(R.string.book_download_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (!canDownload && !downloaded) {
        Text(
            stringResource(R.string.book_download_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.book_remove_download)) },
            text = { Text(stringResource(R.string.book_remove_download_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BookDownloads.cancel(context, owner, bookId)
                        BookStorage.remove(context, owner, bookId)
                        localRevision++
                        confirmRemove = false
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
