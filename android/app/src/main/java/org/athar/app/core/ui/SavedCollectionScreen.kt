package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.SavedCollections
import org.athar.app.core.network.ApiException
import org.athar.app.quran.QuranIndex

@Composable internal fun SavedCollectionScreen(graph: AppGraph, tool: String, open: (String) -> Unit) {
    val owner = requireNotNull(graph.session.userId)
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(owner, tool) { mutableIntStateOf(1) }
    var busy by remember(owner, tool) { mutableStateOf(false) }
    var failed by remember(owner, tool) { mutableStateOf(false) }
    var index by remember { mutableStateOf<QuranIndex?>(null) }
    var indexFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(tool, attempt) {
        if (tool == "bookmarks") try { index = graph.quran.data().index; indexFailed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { indexFailed = true }
    }
    if (tool == "bookmarks" && index == null) {
        if (indexFailed) AccountDataFailure { attempt++ } else ContentLoadingSkeleton()
        return
    }
    val path = if (tool == "bookmarks") "quran/bookmarks" else "me/favorites"
    DataScreen(graph, path, mapOf("paged" to "1", "page" to page.toString(), "per_page" to "20")) { data, reload ->
        val result = runCatching { SavedCollections.page(tool, data, page, index = index) }.getOrNull()
        if (result == null) { AccountDataFailure(reload); return@DataScreen }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "identity", contentType = "heading") { AtharScreenIdentity(stringResource(if (tool == "bookmarks") R.string.bookmarks else R.string.favorites), if (tool == "bookmarks") AtharIcons.Bookmark else AtharIcons.Favorite) }
            item(key = "page-summary", contentType = "metadata") { Text(stringResource(R.string.saved_collection_page, page, result.total), style = MaterialTheme.typography.bodySmall) }
            item(key = "refresh", contentType = "controls") { TextButton(enabled = !busy, onClick = reload) { Text(stringResource(R.string.retry)) } }
            if (result.rows.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
            items(result.rows, key = { if (tool == "bookmarks") it.text("id") else it.text("target_type") + ":" + it.text("target_id") }, contentType = { tool }) { row ->
                Column {
                    if (tool == "bookmarks") {
                        Text("${row.text("surah")}:${row.text("ayah")} ${row.text("label")}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { open("quran/${row.text("surah")}/${row.text("ayah")}") }) { Text(stringResource(R.string.open_reader)) }
                    } else TextButton(onClick = { val kind = row.text("target_type"); open((if (kind == "series") "series" else kind + "s") + "/" + row.text("target_id")) }) { Text(row.text("title")) }
                    TextButton(enabled = !busy, onClick = { scope.launch {
                        busy = true; failed = false
                        try {
                            try {
                                if (tool == "bookmarks") graph.session.request("DELETE", "$path/${row.text("id")}", expectedUser = owner)
                                else graph.session.request("DELETE", path, mapOf("target_type" to row.text("target_type"), "target_id" to row.text("target_id").toInt()), expectedUser = owner)
                            } catch (error: ApiException) { if (error.status != 404) throw error }
                            graph.content.invalidate(path, owner)
                            // Keep the item until confirmation. Refill this page after deletion;
                            // move back when deleting the final item on a later page.
                            if (result.rows.size == 1 && page > 1) page-- else reload()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    } }) { Text(stringResource(R.string.delete_action)) }
                    HorizontalDivider()
                }
            }
            item(key = "pages", contentType = "controls") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy && page > 1, onClick = { failed = false; page-- }) { Text(stringResource(R.string.previous_page)) }
                    TextButton(enabled = !busy && result.hasMore && page < 10000, onClick = { failed = false; page++ }) { Text(stringResource(R.string.next_page)) }
                }
            }
            if (failed) item(key = "failure", contentType = "state") { MessagePanel(stringResource(R.string.network_error), true) }
        }
    }
}
