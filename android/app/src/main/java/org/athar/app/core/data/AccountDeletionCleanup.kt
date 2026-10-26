package org.athar.app.core.data

import android.content.Context
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.books.BookStorage

/**
 * Local cleanup runs only after the remote account deletion succeeds.
 * Every key/path is scoped to [owner]; another actor's rows and files are never enumerated here.
 */
suspend fun cleanupDeletedAccount(
    context: Context,
    graph: AppGraph,
    owner: Long,
) = withContext(Dispatchers.IO) {
    require(owner > 0L)
    require(graph.partition.matches(Regex("[a-f0-9]{12}")))

    // Block a stale book worker from recreating owned files even if WorkManager cancellation races.
    BookStorage.revokeOwner(context, owner)

    val manager = WorkManager.getInstance(context.applicationContext)
    val syncBase = "athar-private-sync-${graph.partition}-$owner"
    val cancellation = listOf(
        manager.cancelUniqueWork(syncBase),
        manager.cancelUniqueWork("$syncBase-maintenance"),
        manager.cancelAllWorkByTag("book-download-owner:$owner"),
    )
    cancellation.forEach { operation ->
        runCatching { operation.result.get() }
    }

    BookStorage.deleteOwnerFiles(context, owner)
    graph.database.dao().deleteUser(owner)
}
