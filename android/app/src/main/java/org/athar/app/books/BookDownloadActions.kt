package org.athar.app.books

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

private data class LocalBook(val request: String? = null, val copy: BookCopy? = null, val corrupt: Boolean = false)

@Composable internal fun BookDownloadActions(graph: AppGraph, book: Int, canRequest: Boolean, checkAvailability: Boolean = false, onChanged: () -> Unit = {}, onOpen: (() -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val owner = requireNotNull(graph.session.userId)
    val scope = remember(graph.partition, owner) { BookScope(graph.partition, owner) }
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(scope, book) { manager.getWorkInfosForUniqueWorkFlow(BookDownloads.name(scope, book)) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var local by remember(scope, book) { mutableStateOf(LocalBook()) }
    var loaded by remember(scope, book) { mutableStateOf(false) }
    var revision by remember(scope, book) { mutableIntStateOf(0) }
    var busy by remember(scope, book) { mutableStateOf(false) }
    var failed by remember(scope, book) { mutableStateOf(false) }
    var confirmRemove by remember(scope, book) { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()
    LaunchedEffect(scope, book, work.map { it.id to it.state }, revision) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                val files = bookFiles(context, scope)
                val request = files.currentRequest(book)
                val copy = files.current(book)
                LocalBook(request, copy, copy != null && !files.verify(copy))
            }
            graph.session.requireVerifiedOwner(owner)
            local = snapshot
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { local = local.copy(corrupt = true) }
        finally { loaded = true }
    }
    val latest = work.firstOrNull { "book-request:${local.request}" in it.tags }
    val active = latest?.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        coroutine.launch {
            try { graph.session.requireVerifiedOwner(owner); block(); graph.session.requireVerifiedOwner(owner); revision++; onChanged() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!loaded || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (active) {
            val percent = latest?.progress?.getInt(BookDownloadWorker.PERCENT, -1) ?: -1
            if (latest?.state == WorkInfo.State.RUNNING) {
                if (percent in 0..99) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (percent in 0..99) stringResource(R.string.book_download_progress, percent) else stringResource(R.string.book_downloading))
            } else Text(stringResource(R.string.book_download_waiting))
            OutlinedButton(onClick = { action { BookDownloads.cancel(context, scope, book) } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("book-cancel-download")) { Text(stringResource(R.string.book_cancel_download)) }
        }
        if (local.copy != null && !local.corrupt) {
            Text(stringResource(R.string.book_download_saved))
            if (onOpen != null) Button(onClick = { action { onOpen() } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("book-open-reader")) { Text(stringResource(R.string.book_open)) }
        }
        if (local.corrupt) MessagePanel(stringResource(R.string.book_integrity_failed), true)
        if (loaded && (local.copy != null || local.corrupt)) OutlinedButton(onClick = { confirmRemove = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("book-remove-download")) { Text(stringResource(R.string.book_remove_download)) }
        if (loaded && !active && !local.corrupt && canRequest && (local.copy == null || latest?.state == WorkInfo.State.FAILED)) {
            Button(onClick = { action {
                val response = graph.session.request("GET", "books/$book/download", expectedUser = owner)
                graph.session.requireVerifiedOwner(owner)
                BookInventoryStore(context, graph).remember(scope, book, response.text("title"))
                BookDownloads.enqueue(context, scope, book, response.text("url"))
            } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("book-download")) { Text(stringResource(if (checkAvailability) R.string.book_check_availability else R.string.download_book)) }
        }
        if (failed || latest?.state == WorkInfo.State.FAILED) MessagePanel(stringResource(if (latest?.outputData?.getString("failure") == "storage") R.string.book_storage_full else R.string.book_download_failed), true)
        if (latest?.state == WorkInfo.State.CANCELLED) Text(stringResource(R.string.book_download_cancelled))
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text(stringResource(R.string.book_remove_download)) }, text = { Text(stringResource(R.string.book_remove_download_confirm)) }, confirmButton = {
        TextButton(onClick = { confirmRemove = false; action { BookDownloads.remove(context, scope, book) } }) { Text(stringResource(R.string.delete_action)) }
    }, dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable internal fun OwnedBookDownloadsScreen(graph: AppGraph, open: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val owner = requireNotNull(graph.session.userId)
    val scope = remember(graph.partition, owner) { BookScope(graph.partition, owner) }
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(scope) { manager.getWorkInfosByTagFlow(BookDownloads.tag(scope)) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var books by remember(scope) { mutableStateOf<List<Int>?>(null) }
    var titles by remember(scope) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var failed by remember(scope) { mutableStateOf(false) }
    var revision by remember(scope) { mutableIntStateOf(0) }
    LaunchedEffect(scope, work.map { it.id to it.state }, revision) {
        failed = false
        try {
            val rows = withContext(Dispatchers.IO) { bookFiles(context, scope).knownBooks() }
            val metadata = BookInventoryStore(context, graph)
            val names = rows.mapNotNull { book -> metadata.title(scope, book)?.let { book to it } }.toMap()
            graph.session.requireVerifiedOwner(owner); books = rows; titles = names
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "heading", contentType = "heading") { AtharSectionHeader(title = stringResource(R.string.book_downloads), actionLabel = stringResource(R.string.retry), onAction = { revision++ }) }
        if (failed) item(key = "error", contentType = "state") { MessagePanel(stringResource(R.string.local_data_unreadable), true) }
        else if (books == null) item(key = "loading", contentType = "state") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (books!!.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
        items(books.orEmpty(), key = { it }, contentType = { "download" }) { book ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titles[book] ?: stringResource(R.string.book_download_item, book), style = MaterialTheme.typography.titleMedium)
                BookDownloadActions(graph, book, canRequest = true, checkAvailability = true, onChanged = { revision++ }, onOpen = { open("book-reader/$book") })
                HorizontalDivider()
            }
        }
    }
}
