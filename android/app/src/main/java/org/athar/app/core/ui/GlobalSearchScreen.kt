package org.athar.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.quran.QuranSearchNormalizer

private data class GlobalQuranResult(
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val text: String,
)

private data class GlobalSearchItem(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val body: String?,
)

private data class GlobalSearchSection(
    val key: String,
    val total: Int,
    val items: List<GlobalSearchItem>,
    val offline: Boolean,
)

private data class GlobalSearchUiState(
    val loading: Boolean = false,
    val query: String = "",
    val quran: List<GlobalQuranResult> = emptyList(),
    val quranTotal: Int = 0,
    val sections: List<GlobalSearchSection> = emptyList(),
    val partial: Boolean = false,
) {
    val offline: Boolean get() = sections.any { it.offline }
}

private val globalSearchDomains = listOf(
    "books",
    "lessons",
    "series",
    "scholars",
    "adhkar",
)

private suspend fun remoteSearchSection(
    graph: AppGraph,
    domain: String,
    query: String,
): GlobalSearchSection {
    val result = graph.content.get(
        domain,
        mapOf(
            "q" to query,
            "page" to "1",
            "per_page" to "5",
        ),
    )
    val rows = result.data.array("items")
    val pagination = runCatching {
        result.data.asJsonObject["pagination"]
    }.getOrNull()
    val total = pagination
        ?.text("total")
        ?.toIntOrNull()
        ?.coerceAtLeast(0)
        ?: rows.size

    val parsed = rows.mapNotNull { row ->
        val id = row.text("id").toIntOrNull()?.takeIf { it > 0 }
            ?: return@mapNotNull null
        val title = when (domain) {
            "scholars" -> row.text("name")
            else -> row.text("title")
        }.trim()
        if (title.isBlank()) return@mapNotNull null

        val subtitle = when (domain) {
            "books" -> row.text("author")
            "lessons", "series" -> row.text("scholar_name")
            "scholars" -> row.text("specialization")
                .ifBlank { row.text("title") }
            "adhkar" -> row.text("category")
            else -> ""
        }.trim().takeIf { it.isNotEmpty() }

        val body = when (domain) {
            "adhkar" -> row.text("formula")
            "scholars" -> row.text("biography")
            else -> row.text("description")
        }.trim().takeIf { it.isNotEmpty() }

        GlobalSearchItem(
            id = id,
            title = title,
            subtitle = subtitle,
            body = body,
        )
    }

    return GlobalSearchSection(
        key = domain,
        total = total,
        items = parsed,
        offline = result.offline,
    )
}

private suspend fun performGlobalSearch(
    graph: AppGraph,
    query: String,
): GlobalSearchUiState = coroutineScope {
    val quranDeferred = async {
        val data = graph.quran.data()
        val page = data.searchPage(query, offset = 0, limit = 6)
        val rows = page.hits.map { hit ->
            val position = hit.ayah.position
            GlobalQuranResult(
                surah = position.surah,
                ayah = position.ayah,
                surahName = data.index.surahs
                    .getOrNull(position.surah - 1)
                    ?.name
                    ?: position.surah.toString(),
                text = hit.ayah.text,
            )
        }
        page.total to rows
    }

    val remoteDeferred = globalSearchDomains.associateWith { domain ->
        async {
            try {
                remoteSearchSection(graph, domain, query) to false
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                null to true
            }
        }
    }

    val quranResult = try {
        quranDeferred.await()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        0 to emptyList()
    }

    var partial = false
    val sections = globalSearchDomains.mapNotNull { domain ->
        val (section, failed) = remoteDeferred.getValue(domain).await()
        if (failed) partial = true
        section
    }

    GlobalSearchUiState(
        query = query,
        quran = quranResult.second,
        quranTotal = quranResult.first,
        sections = sections,
        partial = partial,
    )
}

@Composable
fun GlobalSearchScreen(
    graph: AppGraph,
    open: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var requestRevision by rememberSaveable { mutableIntStateOf(0) }
    var selectedDomain by rememberSaveable { mutableStateOf("all") }
    var state by remember { mutableStateOf(GlobalSearchUiState()) }
    val canSearch = QuranSearchNormalizer.normalize(input.trim()).length >= 2

    LaunchedEffect(submitted, requestRevision) {
        if (submitted.isBlank()) return@LaunchedEffect
        state = GlobalSearchUiState(
            loading = true,
            query = submitted,
        )
        state = try {
            performGlobalSearch(graph, submitted)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            GlobalSearchUiState(
                query = submitted,
                partial = true,
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(
                horizontal = AtharSpacing.Lg,
                vertical = AtharSpacing.Md,
            ),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.global_search)) },
                placeholder = { Text(stringResource(R.string.global_search_hint)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (canSearch) {
                                submitted = input.trim()
                                selectedDomain = "all"
                                requestRevision++
                            }
                        },
                        enabled = canSearch,
                    ) {
                        Icon(
                            AtharIcons.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
            )

            if (!canSearch && input.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_minimum_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedDomain == "all",
                        onClick = { selectedDomain = "all" },
                        label = { Text(stringResource(R.string.all_categories)) },
                    )
                }
                item(key = "quran") {
                    FilterChip(
                        selected = selectedDomain == "quran",
                        onClick = { selectedDomain = "quran" },
                        label = { Text(stringResource(R.string.quran)) },
                    )
                }
                items(globalSearchDomains, key = { it }) { domain ->
                    FilterChip(
                        selected = selectedDomain == domain,
                        onClick = { selectedDomain = domain },
                        label = {
                            Text(
                                stringResource(
                                    when (domain) {
                                        "books" -> R.string.books
                                        "lessons" -> R.string.lessons
                                        "series" -> R.string.series
                                        "scholars" -> R.string.scholars
                                        else -> R.string.adhkar
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (submitted.isBlank()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(AtharSpacing.Xl),
            ) {
                MessagePanel(stringResource(R.string.search_minimum_hint))
            }
            return@Column
        }

        val showQuran = selectedDomain == "all" || selectedDomain == "quran"
        val visibleSections = if (selectedDomain == "all") {
            state.sections
        } else {
            state.sections.filter { it.key == selectedDomain }
        }
        val visibleCount =
            (if (showQuran) state.quranTotal else 0) +
                visibleSections.sumOf { it.total }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            item(key = "result-summary", contentType = "summary") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Text(
                        stringResource(R.string.search_results_count, visibleCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.offline) {
                        MessagePanel(stringResource(R.string.offline_notice))
                    }
                    if (state.partial) {
                        MessagePanel(stringResource(R.string.search_partial_results))
                    }
                }
            }

            if (visibleCount == 0 && !state.loading) {
                item(key = "empty", contentType = "empty") {
                    QuietEmptyState(stringResource(R.string.search_no_results))
                }
            }

            if (showQuran && state.quran.isNotEmpty()) {
                item(key = "quran-heading", contentType = "heading") {
                    AtharSectionHeader(
                        stringResource(R.string.search_quran_results) +
                            " (" + state.quranTotal + ")",
                    )
                }
                items(
                    items = state.quran,
                    key = { "quran:${it.surah}:${it.ayah}" },
                    contentType = { "quran-result" },
                ) { hit ->
                    Surface(
                        onClick = {
                            open("quran/${hit.surah}/${hit.ayah}")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            Modifier.padding(AtharSpacing.Lg),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            Text(
                                hit.surahName + " · " +
                                    stringResource(R.string.ayah) + " " + hit.ayah,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                hit.text,
                                fontFamily = QuranFont,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            visibleSections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    item(
                        key = "heading:${section.key}",
                        contentType = "heading",
                    ) {
                        AtharSectionHeader(
                            stringResource(
                                when (section.key) {
                                    "books" -> R.string.books
                                    "lessons" -> R.string.lessons
                                    "series" -> R.string.series
                                    "scholars" -> R.string.scholars
                                    else -> R.string.adhkar
                                },
                            ) + " (" + section.total + ")",
                        )
                    }
                    items(
                        items = section.items,
                        key = { section.key + ":" + it.id },
                        contentType = { "search-result:${section.key}" },
                    ) { result ->
                        Surface(
                            onClick = {
                                when (section.key) {
                                    "books" -> open("books/${result.id}")
                                    "lessons" -> open("lessons/${result.id}")
                                    "series" -> open("series/${result.id}")
                                    "scholars" -> open("scholars/${result.id}")
                                    "adhkar" -> open("adhkar")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Column(
                                Modifier.padding(AtharSpacing.Lg),
                                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                            ) {
                                Text(
                                    result.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                result.subtitle?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                result.body?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
