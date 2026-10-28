package org.athar.app.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.athar.app.core.data.AccountPayloads
import org.athar.app.AppGraph
import org.athar.app.R
import java.time.LocalDate

@Composable fun AccountToolsScreen(graph: AppGraph, tool: String, open: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val owner=graph.session.userId
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    if (tool == "export") { AccountExportScreen(graph); return }
    when(tool) {
        "khatmah" -> DataScreen(graph, "me/khatmah") { data, reload ->
            if(!AccountPayloads.valid("khatmah",data)){AccountDataFailure(reload);return@DataScreen}
            var target by remember(data) { mutableStateOf(data.text("target_date").ifBlank { LocalDate.now().plusDays(30).toString() }) }
            var completed by remember(data) { mutableStateOf(data.text("completed_pages").ifBlank { "0" }) }
            var status by remember(data) { mutableStateOf(data.text("status").ifBlank { "active" }) }
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "identity", contentType = "heading") { AtharScreenIdentity(stringResource(R.string.khatmah), AtharIcons.MenuBook) }
                item { DateField(target, { target = it }, stringResource(R.string.target_date)) }
                item { OutlinedTextField(completed, { completed = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.completed_pages)) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("active" to R.string.active, "paused" to R.string.paused, "finished" to R.string.finished).forEach { (key,label) -> FilterChip(status == key, { status = key }, label = { Text(stringResource(label)) }) } } }
                item { Button(enabled = !busy, onClick = { scope.launch {
                    busy = true
                    try { graph.session.request("PUT", "me/khatmah", mapOf("target_date" to LocalDate.parse(target).toString(), "completed_pages" to completed.toInt(), "status" to status),expectedUser=requireNotNull(owner)); saved = true; failed = false }
                    catch (failure: CancellationException) { throw failure } catch (_: Exception) { failed = true } finally { busy = false }
                } }) { Text(stringResource(R.string.save)) } }
                if (saved) item { MessagePanel(stringResource(R.string.saved)) }
                if (failed) item { MessagePanel(stringResource(R.string.invalid_input), true) }
                if (data.text("daily_pages").isNotBlank()) item { Text(stringResource(R.string.daily_pages, data.text("daily_pages"))) }
            }
        }
        else -> {
            val path = if (tool == "bookmarks") "quran/bookmarks" else "me/favorites"
            DataScreen(graph, path) { data, reload ->
                if(!AccountPayloads.valid(tool,data)){AccountDataFailure(reload);return@DataScreen}
                val rows = data.asJsonArray.toList()
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "identity", contentType = "heading") { AtharScreenIdentity(stringResource(if (tool == "bookmarks") R.string.bookmarks else R.string.favorites), if (tool == "bookmarks") AtharIcons.Bookmark else AtharIcons.Favorite) }
                    if (rows.isEmpty()) item(key = "empty", contentType = "state") { QuietEmptyState(stringResource(R.string.empty_state)) }
                    items(rows, key = { if (tool == "bookmarks") it.text("id") else it.text("target_type") + it.text("target_id") }, contentType = { tool }) { row ->
                        Column {
                            if (tool == "bookmarks") {
                                Text("${row.text("surah")}:${row.text("ayah")} ${row.text("label")}", style = MaterialTheme.typography.titleMedium)
                                val surah = row.text("surah").toIntOrNull()
                                val ayah = row.text("ayah").toIntOrNull()
                                TextButton(onClick = { open("quran/$surah/$ayah") }, enabled = surah in 1..114 && ayah in 1..286) {
                                    Text(stringResource(R.string.open_reader))
                                }
                            }
                            else TextButton(onClick = { val kind = row.text("target_type"); open((if (kind == "series") "series" else kind + "s") + "/" + row.text("target_id")) }) { Text(row.text("title")) }
                            TextButton(onClick = { scope.launch {
                                try { if (tool == "bookmarks") graph.session.request("DELETE", "$path/${row.text("id")}",expectedUser=requireNotNull(owner)) else graph.session.request("DELETE", path, mapOf("target_type" to row.text("target_type"), "target_id" to row.text("target_id").toInt()),expectedUser=requireNotNull(owner)); reload() }
                                catch (failure: CancellationException) { throw failure } catch (_: Exception) { failed = true }
                            } }) { Text(stringResource(R.string.delete_action)) }
                            HorizontalDivider()
                        }
                    }
                    if (failed) item { MessagePanel(stringResource(R.string.network_error), true) }
                }
            }
        }
    }
}

@Composable internal fun AccountDataFailure(retry:()->Unit){
    Box(Modifier.padding(20.dp)){
        MessagePanel(stringResource(R.string.account_data_unavailable),true){TextButton(onClick=retry){Text(stringResource(R.string.retry))}}
    }
}
