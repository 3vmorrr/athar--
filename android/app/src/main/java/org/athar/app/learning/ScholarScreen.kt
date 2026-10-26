package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.core.data.ProtectedRead
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

/** Profile metadata and independently paginated series/standalone lessons. */
@Composable
fun ScholarScreen(graph: AppGraph, id: Int, open: (String) -> Unit) {
    val owner = requireNotNull(graph.session.userId)
    var standalone by rememberSaveable(id) { mutableStateOf(false) }
    var page by rememberSaveable(id, standalone) { mutableIntStateOf(1) }
    val uri = LocalUriHandler.current
    val path = if (standalone) "lessons" else "series"
    val query = buildMap {
        put("scholar_id", "$id")
        put("page", "$page")
        if (standalone) put("standalone", "1")
    }
    DataScreen(graph, "scholars/$id/profile") { scholar, _ ->
        DataScreen(graph, path, query) { catalog, _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "profile", contentType = "profile") {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val image = scholar.text("image_url")
                            if (image.startsWith("/scholar-image?key=")) {
                                val origin = graph.apiBaseUrl.substringBefore("/api/")
                                AsyncImage(
                                    model = origin + image,
                                    contentDescription = null,
                                    modifier = Modifier.size(112.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(scholar.text("name"), style = MaterialTheme.typography.headlineMedium)
                            ScholarFollowControl(graph, owner, id)
                            ExpandableDescription(scholar.text("biography"))
                            val source = scholar.text("source_url")
                            if (runCatching { java.net.URI(source).scheme == "https" && java.net.URI(source).host != null }.getOrDefault(false)) {
                                TextButton(onClick = { uri.openUri(source) }) {
                                    Text(stringResource(R.string.scholar_source))
                                }
                            }
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(!standalone, { standalone = false }, label = { Text(stringResource(R.string.series)) })
                                FilterChip(standalone, { standalone = true }, label = { Text(stringResource(R.string.scholar_standalone)) })
                            }
                        }
                    }
                }
                if (catalog.array("items").isEmpty()) item(key = "empty", contentType = "state") {
                    Text(stringResource(R.string.empty_state))
                }
                items(catalog.array("items"), key = { "$path-${it.text("id")}" }, contentType = { path }) { row ->
                    if (standalone) PlaylistLesson(row, false) { open("lessons/${row.text("id")}") }
                    else OutlinedCard(onClick = { open("series/${row.text("id")}") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.text("title"), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.scholar_lesson_count, row.text("lesson_count").toIntOrNull() ?: 0), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item(key = "pagination", contentType = "controls") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.back)) }
                        TextButton(onClick = { page++ }, enabled = catalog.asJsonObject["pagination"].text("has_more") == "true") { Text(stringResource(R.string.more)) }
                    }
                }
            }
        }
    }
}


@Composable
private fun ScholarFollowControl(
    graph: AppGraph,
    owner: Long,
    scholarId: Int,
) {
    val flow = remember(owner, scholarId) { graph.follows.observe(owner, "scholar", scholarId) }
    val read by flow.collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading)
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable(owner, scholarId) { mutableStateOf(false) }
    var refreshFailed by rememberSaveable(owner, scholarId) { mutableStateOf(false) }
    var mutationFailed by rememberSaveable(owner, scholarId) { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            try {
                graph.follows.refresh(owner, "scholar", scholarId)
                refreshFailed = false
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                refreshFailed = true
            }
        }
    }

    LaunchedEffect(owner, scholarId) { refresh() }

    when (val current = read) {
        ProtectedRead.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true)
        is ProtectedRead.Ready -> {
            val state = current.value
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state == null) {
                    Text(
                        stringResource(R.string.follow_state_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { refresh() }) {
                        Text(stringResource(R.string.retry))
                    }
                } else {
                    val following = state.following
                    if (following) {
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                busy = true
                                mutationFailed = false
                                scope.launch {
                                    try {
                                        graph.follows.set(owner, "scholar", scholarId, false)
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        mutationFailed = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.following_scholar))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                mutationFailed = false
                                scope.launch {
                                    try {
                                        graph.follows.set(owner, "scholar", scholarId, true)
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        mutationFailed = true
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.follow_scholar))
                        }
                    }
                    if (refreshFailed) {
                        Text(
                            stringResource(R.string.follow_saved_state_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (mutationFailed) {
                        Text(
                            stringResource(R.string.follow_change_failed_rollback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
