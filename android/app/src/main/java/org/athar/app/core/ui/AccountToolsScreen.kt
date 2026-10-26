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
import org.athar.app.khatmah.KhatmahRepository
import org.athar.app.khatmah.KhatmahScreen
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.khatmah.KhatmahScreen
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
    var confirmExport by remember(owner, tool) { mutableStateOf(false) }
    val context = LocalContext.current
    val khatmahRepository = remember(graph) {
        KhatmahRepository(
            dao = graph.database.dao(),
            cipher = graph.cipher,
            session = graph.session,
        )
    }
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
                            owner = user,
                            session = graph.session,
                            lessons = graph.lessons,
                            night = graph.nightPrayer,
                            khatmah = khatmahRepository,
                        )
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        localFailure = true
                        runCatching {
                            context.contentResolver.delete(uri, null, null)
                        }
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
                        runCatching {
                            context.contentResolver.delete(uri, null, null)
                        }
                        return@launch
                    }

                    graph.session.requireVerifiedOwner(user)
                    if (!AccountPayloads.validExport(user, account)) {
                        payloadFailure = true
                        runCatching {
                            context.contentResolver.delete(uri, null, null)
                        }
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
                        runCatching {
                            context.contentResolver.delete(uri, null, null)
                        }
                        return@launch
                    }

                    graph.session.requireVerifiedOwner(user)
                    saved = true
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    failed = true
                    runCatching {
                        context.contentResolver.delete(uri, null, null)
                    }
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
                        stringResource(R.string.export_security_excluded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.export_requires_current_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { confirmExport = true },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.export_data))
            }

            if (confirmExport) {
                AlertDialog(
                    onDismissRequest = { confirmExport = false },
                    title = { Text(stringResource(R.string.export_confirm_title)) },
                    text = { Text(stringResource(R.string.export_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmExport = false
                                exporter.launch(
                                    "athar-data-${LocalDate.now()}.json",
                                )
                            },
                        ) {
                            Text(stringResource(R.string.export_choose_location))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmExport = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
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
        "activity" -> DataScreen(graph, "me/activity-summary") { data, reload ->
            if (!AccountPayloads.validActivitySummary(data)) {
                AccountDataFailure(reload)
                return@DataScreen
            }
            val availability = data.asJsonObject["availability"].asJsonObject
            val recorded = data.asJsonObject["recorded"].asJsonObject
            val watchedMinutes = ((recorded["watched_seconds"]?.asLong ?: 0L) / 60L)
                .coerceAtLeast(0L)

            LazyColumn(
                contentPadding = PaddingValues(AtharSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                item(key = "activity-hero", contentType = "hero") {
                    AtharHeroSurface(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.activity),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.activity_genuine_only),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item(key = "activity-metrics", contentType = "metrics") {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharMetric(
                            value = recorded["adhkar_completions"]?.asString ?: "0",
                            label = stringResource(R.string.completed_adhkar),
                            modifier = Modifier.weight(1f).widthIn(min = 110.dp),
                        )
                        AtharMetric(
                            value = recorded["lesson_completions"]?.asString ?: "0",
                            label = stringResource(R.string.completed_lessons),
                            modifier = Modifier.weight(1f).widthIn(min = 110.dp),
                        )
                        AtharMetric(
                            value = recorded["lessons_started"]?.asString ?: "0",
                            label = stringResource(R.string.activity_lessons_started),
                            modifier = Modifier.weight(1f).widthIn(min = 110.dp),
                        )
                        AtharMetric(
                            value = watchedMinutes.toString(),
                            label = stringResource(R.string.activity_watch_minutes),
                            modifier = Modifier.weight(1f).widthIn(min = 110.dp),
                        )
                    }
                }

                item(key = "activity-availability", contentType = "availability") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            Modifier.padding(AtharSpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            AtharSectionHeader(stringResource(R.string.activity_tracking))
                            ActivityAvailabilityRow(
                                title = stringResource(R.string.activity_reading_sessions),
                                available = availability["reading_sessions"]?.asBoolean == true,
                            )
                            ActivityAvailabilityRow(
                                title = stringResource(R.string.activity_listening_history),
                                available = availability["listening_history"]?.asBoolean == true,
                            )
                            ActivityAvailabilityRow(
                                title = stringResource(R.string.adhkar),
                                available = availability["adhkar_completions"]?.asBoolean == true,
                            )
                            ActivityAvailabilityRow(
                                title = stringResource(R.string.lessons),
                                available = availability["lesson_progress"]?.asBoolean == true,
                            )
                        }
                    }
                }

                val recent = data.array("recent")
                if (recent.isNotEmpty()) {
                    item(key = "activity-recent-title", contentType = "heading") {
                        AtharSectionHeader(stringResource(R.string.activity_recent))
                    }
                    items(
                        recent.reversed(),
                        key = { it.text("date") },
                        contentType = { "activity-day" },
                    ) { day ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier.padding(AtharSpacing.Lg),
                                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                            ) {
                                Text(
                                    day.text("date"),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(
                                        R.string.activity_day_summary,
                                        day.text("lesson_completions").toIntOrNull() ?: 0,
                                        day.text("adhkar_completions").toIntOrNull() ?: 0,
                                        day.text("book_downloads").toIntOrNull() ?: 0,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        "reminders" -> org.athar.app.notifications.RoutineReminderSettingsScreen(graph, open)
        "khatmah" -> KhatmahScreen(graph)
        else -> {
            val path = if (tool == "bookmarks") {
                "quran/bookmarks"
            } else {
                "me/favorites"
            }
            var page by rememberSaveable(tool) { mutableIntStateOf(1) }
            var hiddenKeys by remember(tool, page) {
                mutableStateOf<Set<String>>(emptySet())
            }
            val query = mapOf(
                "paged" to "1",
                "page" to "$page",
                "per_page" to "20",
            )

            DataScreen(graph, path, query) { data, reload ->
                if (!AccountPayloads.validCollectionPage(tool, data)) {
                    AccountDataFailure(reload)
                    return@DataScreen
                }

                val allRows = data.array("items")
                val pagination = data.asJsonObject["pagination"].asJsonObject
                val hasMore = pagination["has_more"]?.asBoolean == true
                val total = pagination["total"]?.asInt ?: 0
                val rows = allRows.filterNot { row ->
                    val key = if (tool == "bookmarks") {
                        "bookmark:${row.text("id")}"
                    } else {
                        "favorite:${row.text("target_type")}:${row.text("target_id")}"
                    }
                    key in hiddenKeys
                }

                LazyColumn(
                    contentPadding = PaddingValues(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    item(key = "collection-heading", contentType = "heading") {
                        AtharSectionHeader(
                            title = stringResource(
                                if (tool == "bookmarks") {
                                    R.string.bookmarks
                                } else {
                                    R.string.favorites
                                },
                            ),
                        )
                        Text(
                            stringResource(R.string.saved_items_total, total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (rows.isEmpty()) {
                        item(key = "empty", contentType = "state") {
                            QuietEmptyState(stringResource(R.string.empty_state))
                        }
                    }

                    items(
                        rows,
                        key = {
                            if (tool == "bookmarks") {
                                "bookmark:${it.text("id")}"
                            } else {
                                "favorite:${it.text("target_type")}:${it.text("target_id")}"
                            }
                        },
                        contentType = { tool },
                    ) { row ->
                        val rowKey = if (tool == "bookmarks") {
                            "bookmark:${row.text("id")}"
                        } else {
                            "favorite:${row.text("target_type")}:${row.text("target_id")}"
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Column(
                                Modifier.padding(AtharSpacing.Lg),
                                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                            ) {
                                if (tool == "bookmarks") {
                                    Text(
                                        stringResource(
                                            R.string.saved_quran_reference,
                                            row.text("surah"),
                                            row.text("ayah"),
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    row.text("label").takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val bookmarkSurah = row.text("surah").toIntOrNull()
                                    val bookmarkAyah = row.text("ayah").toIntOrNull()
                                    TextButton(
                                        onClick = {
                                            if (bookmarkSurah != null && bookmarkAyah != null) {
                                                open("quran/$bookmarkSurah/$bookmarkAyah")
                                            }
                                        },
                                        enabled = (bookmarkSurah ?: 0) in 1..114 &&
                                            (bookmarkAyah ?: 0) > 0,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    ) {
                                        Text(stringResource(R.string.open_reader))
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            val kind = row.text("target_type")
                                            val destination = when (kind) {
                                                "book" -> "books"
                                                "lesson" -> "lessons"
                                                "series" -> "series"
                                                else -> null
                                            }
                                            if (destination != null) {
                                                open("$destination/${row.text("target_id")}")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(row.text("title"))
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        hiddenKeys = hiddenKeys + rowKey
                                        failed = false
                                        scope.launch {
                                            try {
                                                val user = requireNotNull(owner)
                                                graph.session.requireVerifiedOwner(user)
                                                if (tool == "bookmarks") {
                                                    graph.session.request(
                                                        "DELETE",
                                                        "$path/${row.text("id")}",
                                                        expectedUser = user,
                                                    )
                                                } else {
                                                    graph.session.request(
                                                        "DELETE",
                                                        path,
                                                        mapOf(
                                                            "target_type" to row.text("target_type"),
                                                            "target_id" to row.text("target_id").toInt(),
                                                        ),
                                                        expectedUser = user,
                                                    )
                                                }
                                                if (rows.size == 1 && page > 1) {
                                                    page--
                                                } else {
                                                    reload()
                                                }
                                            } catch (failure: CancellationException) {
                                                hiddenKeys = hiddenKeys - rowKey
                                                throw failure
                                            } catch (_: Exception) {
                                                hiddenKeys = hiddenKeys - rowKey
                                                failed = true
                                            }
                                        }
                                    },
                                    enabled = rowKey !in hiddenKeys,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.delete_action))
                                }
                            }
                        }
                    }

                    item(key = "pagination", contentType = "controls") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { page-- },
                                enabled = page > 1,
                            ) {
                                Text(stringResource(R.string.back))
                            }
                            Text(
                                stringResource(R.string.page_number, page),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            TextButton(
                                onClick = { page++ },
                                enabled = hasMore,
                            ) {
                                Text(stringResource(R.string.more))
                            }
                        }
                    }

                    if (failed) {
                        item(key = "failure", contentType = "state") {
                            MessagePanel(stringResource(R.string.network_error), true)
                        }
                    }
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
@Composable
private fun ActivityAvailabilityRow(
    title: String,
    available: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
        )
        AtharStatusPill(
            stringResource(
                if (available) {
                    R.string.activity_tracking_available
                } else {
                    R.string.activity_tracking_unavailable
                },
            ),
            emphasized = !available,
        )
    }
}
