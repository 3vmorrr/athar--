package org.athar.app.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.network.ApiException
import org.athar.app.core.ui.*

@Composable
internal fun TafsirIssuePanel(issue: TafsirIssue, retry: () -> Unit) {
    MessagePanel(
        stringResource(
            if (issue == TafsirIssue.SourceUnavailable) {
                R.string.tafsir_source_unavailable
            } else {
                R.string.network_error
            },
        ),
        true,
    ) {
        TextButton(onClick = retry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
internal fun TafsirReadingContent(
    state: TafsirUiState,
    retry: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    fontSize: Int,
) {
    val reading = requireNotNull(state.reading)
    val uri = LocalUriHandler.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("tafsir-reading"),
        contentPadding = PaddingValues(AtharSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
    ) {
        if (state.contentOffline) {
            item(key = "offline", contentType = "feedback") {
                MessagePanel(stringResource(R.string.cached_content_retry), false) {
                    TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        if (state.contentIssue != null) {
            item(key = "error", contentType = "feedback") {
                TafsirIssuePanel(state.contentIssue, retry)
            }
        }
        if (state.contentLoading) {
            item(key = "loading", contentType = "progress") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        item(key = "verse", contentType = "ayah") {
            state.verse?.let { verse ->
                Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    Text("${verse.position.surah}:${verse.position.ayah}", style = MaterialTheme.typography.labelLarge)
                    Text(verse.text, fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9f).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                }
            }
        }
        item(key = "book", contentType = "metadata") {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs)) {
                AtharSectionHeader(stringResource(R.string.tafsir_selected_source))
                Text(reading.book.name, style = MaterialTheme.typography.titleLarge)
                if (reading.book.author.isNotBlank()) {
                    Text(
                        reading.book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "pager-top", contentType = "pager") {
            TafsirPager(reading.pagination, previous, next)
        }
        if (reading.blocks.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    stringResource(R.string.tafsir_no_content),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(
                reading.blocks.withIndex().toList(),
                key = { "${reading.pagination.page}:${it.index}" },
                contentType = { "tafsir" },
            ) { (_, block) ->
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        Text(block.text, style = MaterialTheme.typography.bodyLarge)
                        if (block.part > 0 || block.page > 0) {
                            Text(
                                stringResource(
                                    R.string.tafsir_reference,
                                    block.part.toString(),
                                    block.page.toString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item(key = "pager-bottom", contentType = "pager") {
            TafsirPager(reading.pagination, previous, next)
        }
        item(key = "source", contentType = "source") {
            TextButton(
                onClick = {
                    if (
                        reading.sourceUrl == "https://quranpedia.net" ||
                        reading.sourceUrl.startsWith("https://api.quranpedia.net/")
                    ) {
                        uri.openUri(reading.sourceUrl)
                    }
                },
            ) {
                Text(stringResource(R.string.quranpedia_source))
            }
        }
    }
}

@Composable
private fun TafsirPager(
    pagination: TafsirPagination,
    previous: () -> Unit,
    next: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
    ) {
        Text(
            stringResource(
                R.string.tafsir_page_status,
                pagination.page,
                pagination.pages.coerceAtLeast(1),
                pagination.total,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = previous,
                enabled = pagination.hasPrevious,
            ) {
                Text(stringResource(R.string.previous_page))
            }
            TextButton(
                onClick = next,
                enabled = pagination.hasMore,
            ) {
                Text(stringResource(R.string.next_page))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TafsirSourceSheet(
    state: TafsirUiState,
    dismiss: () -> Unit,
    search: (String) -> Unit,
    retry: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    select: (TafsirBook) -> Unit,
) {
    var input by rememberSaveable(state.catalogQuery) {
        mutableStateOf(state.catalogQuery)
    }
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val maxListHeight = (
        configuration.screenHeightDp * if (fontScale >= 1.8f) 0.52f else 0.62f
    ).dp

    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AtharSpacing.Xl)
                .padding(bottom = AtharSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(
                stringResource(R.string.choose_tafsir),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(80) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                )
                Button(
                    onClick = { search(input) },
                    enabled = !state.catalogLoading,
                ) {
                    Text(stringResource(R.string.search))
                }
            }

            if (state.catalogOffline) {
                MessagePanel(stringResource(R.string.cached_content_retry), false) {
                    TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (state.catalogIssue != null) {
                TafsirIssuePanel(state.catalogIssue, retry)
            }
            if (state.catalogLoading && state.catalog == null) {
                LoadingScreen()
            } else if (state.catalogLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.catalog?.let { catalog ->
                Text(
                    stringResource(
                        R.string.tafsir_sources_count,
                        catalog.pagination.total,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxListHeight)
                        .testTag("tafsir-source-list"),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                ) {
                    if (catalog.books.isEmpty()) {
                        item(key = "empty") {
                            Text(stringResource(R.string.tafsir_no_sources))
                        }
                    }
                    items(
                        catalog.books,
                        key = { it.id },
                        contentType = { "source" },
                    ) { book ->
                        ListItem(
                            headlineContent = { Text(book.name) },
                            supportingContent = {
                                if (book.author.isNotBlank()) Text(book.author)
                            },
                            trailingContent = {
                                if (book.id == state.selectedBook?.id) {
                                    Icon(AtharIcons.CheckCircle, null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                        )
                        TextButton(
                            onClick = { select(book) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("tafsir-source-${book.id}"),
                        ) {
                            Text(
                                if (book.id == state.selectedBook?.id) {
                                    stringResource(R.string.tafsir_selected_source)
                                } else {
                                    stringResource(R.string.choose_tafsir)
                                },
                            )
                        }
                    }
                    item(key = "pager") {
                        TafsirPager(catalog.pagination, previous, next)
                    }
                }
            }
        }
    }
}
