package org.athar.app.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
                    AtharHeroSurface(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                        val image = scholar.text("image_url")
                        if (image.startsWith("/scholar-image?key=")) {
                            val origin = graph.apiBaseUrl.substringBefore("/api/")
                            AsyncImage(
                                model = origin + image,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(scholar.text("name"), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (standalone) stringResource(R.string.scholar_standalone) else stringResource(R.string.series),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        }
                        ExpandableDescription(scholar.text("biography"))
                        val source = scholar.text("source_url")
                        if (runCatching { java.net.URI(source).scheme == "https" && java.net.URI(source).host != null }.getOrDefault(false)) {
                            TextButton(onClick = { uri.openUri(source) }) {
                                Text(stringResource(R.string.scholar_source))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!standalone, { standalone = false }, label = { Text(stringResource(R.string.series)) })
                            FilterChip(standalone, { standalone = true }, label = { Text(stringResource(R.string.scholar_standalone)) })
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
