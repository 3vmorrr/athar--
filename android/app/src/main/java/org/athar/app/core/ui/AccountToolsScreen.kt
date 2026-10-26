package org.athar.app.core.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.athar.app.core.data.retainedAccountData
import org.athar.app.core.data.accountExportEnvelope
import org.athar.app.core.data.AccountPayloads
import org.athar.app.AppGraph
import org.athar.app.R
import java.time.LocalDate

@Composable fun AccountToolsScreen(graph: AppGraph, tool: String, open: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val owner=graph.session.userId
    var localFailure by remember(owner,tool){mutableStateOf(false)}
    var remoteFailure by remember(owner,tool){mutableStateOf(false)}
    var payloadFailure by remember(owner,tool){mutableStateOf(false)}
    var writeFailure by remember(owner,tool){mutableStateOf(false)}
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                localFailure = false
                remoteFailure = false
                payloadFailure = false
                writeFailure = false
                failed = false
                saved = false

                try {
                    val user = requireNotNull(owner)
                    graph.session.requireVerifiedOwner(user)

                    val local = try {
                        retainedAccountData(
                            user,
                            graph.session,
                            graph.lessons,
                            graph.nightPrayer,
                        )
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        localFailure = true
                        return@launch
                    }

                    val account = try {
                        graph.session.request(
                            "GET",
                            "me/export",
                            expectedUser = user,
                        ).asJsonObject
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        remoteFailure = true
                        return@launch
                    }

                    graph.session.requireVerifiedOwner(user)
                    if (!AccountPayloads.validExport(user, account)) {
                        payloadFailure = true
                        return@launch
                    }

                    val envelope = try {
                        accountExportEnvelope(
                            owner = user,
                            account = account,
                            device = local,
                        )
                    } catch (_: Exception) {
                        payloadFailure = true
                        return@launch
                    }

                    try {
                        withContext(Dispatchers.IO) {
                            graph.session.requireVerifiedOwner(user)
                            val output = context.contentResolver
                                .openOutputStream(uri, "wt")
                                ?: throw java.io.IOException("Export unavailable")
                            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                                writer.write(envelope.toString())
                                writer.flush()
                            }
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        writeFailure = true
                        return@launch
                    }

                    graph.session.requireVerifiedOwner(user)
                    saved = true
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    failed = true
                } finally {
                    busy = false
                }
            }
        }
    }
    when(tool) {
        "export" -> Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AtharHeroSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.export_data),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.export_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Text(
                        stringResource(R.string.export_scope_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.export_scope_account))
                    Text(stringResource(R.string.export_scope_device))
                    AtharStatusPill(
                        stringResource(R.string.export_private_excluded),
                    )
                    Text(
                        stringResource(R.string.export_requires_current_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = {
                    exporter.launch("athar-data-${LocalDate.now()}.json")
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.export_data))
            }

            if (saved) {
                MessagePanel(stringResource(R.string.export_saved_complete))
            }
            when {
                localFailure -> MessagePanel(
                    stringResource(R.string.local_data_unreadable),
                    true,
                )
                remoteFailure -> MessagePanel(
                    stringResource(R.string.export_account_unavailable),
                    true,
                )
                payloadFailure -> MessagePanel(
                    stringResource(R.string.account_data_unavailable),
                    true,
                )
                writeFailure -> MessagePanel(
                    stringResource(R.string.export_write_failed),
                    true,
                )
                failed -> MessagePanel(
                    stringResource(R.string.network_error),
                    true,
                )
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        "khatmah" -> DataScreen(graph, "me/khatmah") { data, reload ->
            if(!AccountPayloads.valid("khatmah",data)){AccountDataFailure(reload);return@DataScreen}
            var target by remember(data) { mutableStateOf(data.text("target_date").ifBlank { LocalDate.now().plusDays(30).toString() }) }
            var completed by remember(data) { mutableStateOf(data.text("completed_pages").ifBlank { "0" }) }
            var status by remember(data) { mutableStateOf(data.text("status").ifBlank { "active" }) }
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(R.string.khatmah), style = MaterialTheme.typography.headlineSmall) }
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
                    if (rows.isEmpty()) item { Text(stringResource(R.string.empty_state)) }
                    items(rows, key = { if (tool == "bookmarks") it.text("id") else it.text("target_type") + it.text("target_id") }, contentType = { tool }) { row ->
                        Column {
                            if (tool == "bookmarks") Text("${row.text("surah")}:${row.text("ayah")} ${row.text("label")}", style = MaterialTheme.typography.titleMedium)
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
