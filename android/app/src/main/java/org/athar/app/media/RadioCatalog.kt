package org.athar.app.media
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
internal fun RadioCatalog(
    graph: AppGraph,
    vm: AudioViewModel,
    state: AudioUiState,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable(query) { mutableIntStateOf(1) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(80) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.search)) },
            )
            TextButton(
                onClick = {
                    query = input.trim()
                    page = 1
                },
            ) {
                Text(stringResource(R.string.search))
            }
        }

        Box(Modifier.weight(1f)) {
            DataScreen(
                graph,
                "quran/radios",
                mapOf(
                    "q" to query,
                    "page" to "$page",
                    "per_page" to "25",
                ),
            ) { data, _ ->
                val stations = data.array("items")
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    item(key = "radio-source", contentType = "source") {
                        val source = data.text("source")
                        if (source.isNotBlank()) {
                            Text(
                                "${stringResource(R.string.source)} · $source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (stations.isEmpty()) {
                        item(key = "radio-empty", contentType = "state") {
                            QuietEmptyState(stringResource(R.string.empty_state))
                        }
                    }

                    items(
                        stations,
                        key = { it.text("id") },
                        contentType = { "station" },
                    ) { station ->
                        val mediaId = "radio:${station.text("id")}"
                        val selected = state.radio && state.mediaKey == mediaId
                        Surface(
                            onClick = { vm.radio(station) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("radio-station-${station.text("id")}"),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(AtharSpacing.Lg),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            ) {
                                Icon(
                                    AtharIcons.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        station.text("name"),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    if (selected) {
                                        Text(
                                            stringResource(
                                                if (state.playing) R.string.watch_playing
                                                else R.string.watch_paused,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "radio-pages", contentType = "controls") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = { page-- },
                                enabled = page > 1,
                            ) {
                                Text(stringResource(R.string.back))
                            }
                            TextButton(
                                onClick = { page++ },
                                enabled = data.asJsonObject["pagination"]
                                    .text("has_more") == "true",
                            ) {
                                Text(stringResource(R.string.more))
                            }
                        }
                    }
                }
            }
        }
    }
}

