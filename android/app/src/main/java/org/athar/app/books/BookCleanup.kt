package org.athar.app.books

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import java.io.File

/** Called only after remote deletion succeeds. A marker fences workers before files are removed. */
internal suspend fun cleanupDeletedAccountBooks(context: Context, graph: AppGraph, owner: Long) = withContext(Dispatchers.IO) {
    val scope = BookScope(graph.partition, owner)
    try { BookDownloads.revoke(context, scope) }
    finally { graph.database.dao().deleteUser(owner) }
}

internal suspend fun recoverBookCleanup(context: Context, graph: AppGraph) = withContext(Dispatchers.IO) {
    val root = File(context.filesDir, "books")
    BookFiles.recoverRevoked(root)
    root.listFiles()?.forEach { marker ->
        val match = Regex("revoked-([a-f0-9]{12})-u-([1-9][0-9]*)").matchEntire(marker.name) ?: return@forEach
        val owner = match.groupValues[2].toLongOrNull() ?: return@forEach
        if (marker.isFile && match.groupValues[1] == graph.partition) graph.database.dao().deleteUser(owner)
    }
}
