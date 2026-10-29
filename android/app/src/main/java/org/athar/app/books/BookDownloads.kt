package org.athar.app.books

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.athar.app.R
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal fun bookFiles(context: Context, scope: BookScope) = BookFiles(File(context.filesDir, "books"), scope) { book, request, hash ->
    val graph = (context.applicationContext as org.athar.app.AtharApplication).graph
    graph.cipher.seal(org.athar.app.core.data.BookCompletionEvent(scope.namespace, scope.owner, book, request, hash, java.time.LocalDate.now(java.time.ZoneOffset.UTC)).json().toString())
}

class BookDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        var files: BookFiles? = null
        var book = -1
        var request: String? = null
        return try {
            val scope = BookScope(inputData.getString(NAMESPACE).orEmpty(), inputData.getLong(OWNER, -1))
            val graph = (applicationContext as org.athar.app.AtharApplication).graph
            if (scope.namespace != graph.partition) return Result.failure()
            graph.accountDeletion.initialize()
            graph.database.dao().requireWritable(scope.owner)
            book = inputData.getInt(BOOK, -1)
            request = requireNotNull(inputData.getString(TOKEN))
            files = bookFiles(applicationContext, scope)
            if (!files.isActive(book, request)) return Result.failure()
            setProgress(workDataOf(PERCENT to -1))
            setForeground(foreground(-1))
            val existing = files.current(book)?.takeIf { it.token == request && files.verify(it) }
            if (existing == null) BookTransfer().download(files, book, request, inputData.getString(URL).orEmpty()) { percent ->
                setProgress(workDataOf(PERCENT to percent))
                setForeground(foreground(percent))
            }
            try { graph.bookActivity.capture(scope.owner) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Committed encrypted file receipt survives for owned sync. */ }
            Result.success(workDataOf(TOKEN to request))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: BookTransferRejected) { Result.failure() }
        catch (_: BookStorageFull) { Result.failure(workDataOf("failure" to "storage")) }
        catch (_: IllegalArgumentException) { Result.failure() }
        catch (_: IllegalStateException) { Result.failure() }
        catch (_: IOException) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
        finally {
            val store = files; val token = request
            if (store != null && book > 0 && token != null) runCatching { store.discardPart(book, token) }
        }
    }

    private fun foreground(percent: Int): ForegroundInfo {
        val channel = "athar-book-downloads"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, applicationContext.getString(R.string.book_download_channel), NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        })
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.book_downloading))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(R.string.cancel), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification)
    }

    companion object {
        internal const val NAMESPACE = "namespace"
        internal const val OWNER = "owner"
        internal const val BOOK = "book"
        internal const val TOKEN = "request"
        internal const val URL = "url"
        internal const val PERCENT = "percent"
    }
}

internal object BookDownloads {
    fun name(scope: BookScope, book: Int): String { require(book > 0); return "book-download:${scope.key}:$book" }
    fun tag(scope: BookScope) = "book-download-owner:${scope.key}"

    suspend fun enqueue(context: Context, scope: BookScope, book: Int, url: String) = withContext(Dispatchers.IO) {
        require(BookDownloadPolicy.safeHttps(url))
        val request = UUID.randomUUID().toString()
        val work = OneTimeWorkRequestBuilder<BookDownloadWorker>().setInputData(workDataOf(
            BookDownloadWorker.NAMESPACE to scope.namespace, BookDownloadWorker.OWNER to scope.owner,
            BookDownloadWorker.BOOK to book, BookDownloadWorker.TOKEN to request, BookDownloadWorker.URL to url))
            .addTag(tag(scope)).addTag("book-request:$request")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        bookFiles(context, scope).begin(book, request)
        coroutineContext.ensureActive()
        WorkManager.getInstance(context).enqueueUniqueWork(name(scope, book), ExistingWorkPolicy.REPLACE, work).result.get()
    }

    suspend fun cancel(context: Context, scope: BookScope, book: Int) = withContext(Dispatchers.IO) {
        bookFiles(context, scope).cancel(book)
        WorkManager.getInstance(context).cancelUniqueWork(name(scope, book)).result.get()
    }

    suspend fun remove(context: Context, scope: BookScope, book: Int) = withContext(Dispatchers.IO) {
        cancel(context, scope, book)
        bookFiles(context, scope).remove(book)
    }

    suspend fun revoke(context: Context, scope: BookScope) = withContext(Dispatchers.IO) {
        try { bookFiles(context, scope).revoke() }
        finally { WorkManager.getInstance(context).cancelAllWorkByTag(tag(scope)).result.get() }
    }
}
