package org.athar.app.quran

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
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

private enum class TafsirIssue { Network, SourceUnavailable }

private data class TafsirUiState(
    val reference: QuranPosition? = null,
    val verse: AyahText? = null,
    val invalidReference: Boolean = false,
    val catalog: TafsirCatalog? = null,
    val catalogQuery: String = "",
    val catalogLoading: Boolean = false,
    val catalogOffline: Boolean = false,
    val catalogIssue: TafsirIssue? = null,
    val selectedBook: TafsirBook? = null,
    val reading: TafsirReading? = null,
    val contentLoading: Boolean = false,
    val contentOffline: Boolean = false,
    val contentIssue: TafsirIssue? = null,
)

private class TafsirViewModel(
    private val graph: AppGraph,
    initialSurah: Int,
    initialAyah: Int,
) : ViewModel() {
    private val mutable = MutableStateFlow(TafsirUiState())
    val state = mutable.asStateFlow()
    private var referenceJob: Job? = null
    private var catalogJob: Job? = null
    private var contentJob: Job? = null

    init {
        openReference(initialSurah.toString(), initialAyah.toString())
        loadCatalog()
    }

    private fun issue(failure: Throwable): TafsirIssue =
        if (
            failure is ApiException &&
            (failure.status == 404 || failure.status >= 500)
        ) {
            TafsirIssue.SourceUnavailable
        } else {
            TafsirIssue.Network
        }

    fun openReference(surahInput: String, ayahInput: String) {
        referenceJob?.cancel()
        referenceJob = viewModelScope.launch {
            try {
                val data = graph.quran.data()
                val position = QuranReferenceInput.position(
                    data.index,
                    surahInput,
                    ayahInput,
                ) ?: error("Invalid Quran reference")
                val verse = data.verses[position.globalIndex - 1]
                mutable.value = mutable.value.copy(
                    reference = position,
                    verse = verse,
                    invalidReference = false,
                    reading = null,
                    contentIssue = null,
                    contentOffline = false,
                )
                if (mutable.value.selectedBook != null) loadContent(1)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(invalidReference = true)
            }
        }
    }

    fun loadCatalog(
        page: Int = mutable.value.catalog?.pagination?.page ?: 1,
        query: String = mutable.value.catalogQuery,
    ) {
        val reference = mutable.value.reference ?: QuranPosition(1, 1, 1)
        val safeQuery = query.trim().take(80)
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                catalogQuery = safeQuery,
                catalogLoading = true,
                catalogIssue = null,
            )
            try {
                val result = graph.content.get(
                    "quran/tafsirs",
                    mapOf(
                        "surah" to reference.surah.toString(),
                        "page" to page.coerceAtLeast(1).toString(),
                        "per_page" to CATALOG_PAGE_SIZE.toString(),
                        "q" to safeQuery,
                    ),
                )
                mutable.value = mutable.value.copy(
                    catalog = TafsirContract.catalog(result.data),
                    catalogLoading = false,
                    catalogOffline = result.offline,
                    catalogIssue = null,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                mutable.value = mutable.value.copy(
                    catalogLoading = false,
                    catalogIssue = issue(failure),
                )
            }
        }
    }

    fun selectBook(book: TafsirBook) {
        mutable.value = mutable.value.copy(
            selectedBook = book,
            reading = null,
            contentIssue = null,
            contentOffline = false,
        )
        loadContent(1)
    }

    fun loadContent(page: Int = mutable.value.reading?.pagination?.page ?: 1) {
        val reference = mutable.value.reference ?: return
        val book = mutable.value.selectedBook ?: return
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                contentLoading = true,
                contentIssue = null,
            )
            try {
                val result = graph.content.get(
                    "quran/tafsir",
                    mapOf(
                        "surah" to reference.surah.toString(),
                        "ayah" to reference.ayah.toString(),
                        "book" to book.id.toString(),
                        "page" to page.coerceAtLeast(1).toString(),
                        "per_page" to CONTENT_PAGE_SIZE.toString(),
                    ),
                )
                val reading = TafsirContract.reading(result.data)
                require(
                    reading.surah == reference.surah &&
                        reading.ayah == reference.ayah &&
                        reading.book.id == book.id,
                )
                mutable.value = mutable.value.copy(
                    reading = reading,
                    selectedBook = reading.book,
                    contentLoading = false,
                    contentOffline = result.offline,
                    contentIssue = null,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                mutable.value = mutable.value.copy(
                    contentLoading = false,
                    contentIssue = issue(failure),
                )
            }
        }
    }

    fun previousCatalogPage() {
        val page = mutable.value.catalog?.pagination ?: return
        if (page.hasPrevious) loadCatalog(page.page - 1)
    }

    fun nextCatalogPage() {
        val page = mutable.value.catalog?.pagination ?: return
        if (page.hasMore) loadCatalog(page.page + 1)
    }

    fun previousContentPage() {
        val page = mutable.value.reading?.pagination ?: return
        if (page.hasPrevious) loadContent(page.page - 1)
    }

    fun nextContentPage() {
        val page = mutable.value.reading?.pagination ?: return
        if (page.hasMore) loadContent(page.page + 1)
    }

    companion object {
        private const val CATALOG_PAGE_SIZE = 20
        private const val CONTENT_PAGE_SIZE = 8
    }
}

@Composable
fun TafsirScreen(
    graph: AppGraph,
    fontSize: Int,
    initialSurah: Int = 1,
    initialAyah: Int = 1,
) {
    val vm: TafsirViewModel = viewModel(
        key = "tafsir:${graph.session.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TafsirViewModel(graph, initialSurah, initialAyah) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var surahInput by rememberSaveable {
        mutableStateOf(initialSurah.toString())
    }
    var ayahInput by rememberSaveable {
        mutableStateOf(initialAyah.toString())
    }
    var selector by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                TafsirNumberField(
                    surahInput,
                    { surahInput = it },
                    R.string.surah,
                    "tafsir-surah",
                    Modifier.weight(1f),
                )
                TafsirNumberField(
                    ayahInput,
                    { ayahInput = it },
                    R.string.ayah,
                    "tafsir-ayah",
                    Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                Button(
                    onClick = { vm.openReference(surahInput, ayahInput) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.show_ayah))
                }
                OutlinedButton(
                    onClick = { selector = true },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text(
                        state.selectedBook?.name
                            ?: stringResource(R.string.choose_tafsir),
                        maxLines = 2,
                    )
                }
            }
            if (state.invalidReference) {
                Text(
                    stringResource(R.string.invalid_input),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        state.verse?.let { verse ->
            Surface(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    Text(
                        stringResource(
                            R.string.saved_quran_reference,
                            verse.position.surah.toString(),
                            verse.position.ayah.toString(),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        verse.text,
                        Modifier.fillMaxWidth(),
                        fontFamily = QuranFont,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.9f).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        when {
            state.selectedBook == null -> {
                Box(Modifier.padding(AtharSpacing.Xl)) {
                    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                        Text(
                            stringResource(R.string.tafsir_choose_source_hint),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { selector = true }) {
                            Text(stringResource(R.string.choose_tafsir))
                        }
                    }
                }
            }

            state.contentLoading && state.reading == null -> LoadingScreen()

            state.contentIssue != null && state.reading == null -> {
                TafsirIssuePanel(state.contentIssue) { vm.loadContent(1) }
            }

            state.reading != null -> {
                TafsirReadingContent(
                    state = state,
                    retry = { vm.loadContent() },
                    previous = vm::previousContentPage,
                    next = vm::nextContentPage,
                )
            }
        }
    }

    if (selector) {
        TafsirSourceSheet(
            state = state,
            dismiss = { selector = false },
            search = { vm.loadCatalog(1, it) },
            retry = { vm.loadCatalog() },
            previous = vm::previousCatalogPage,
            next = vm::nextCatalogPage,
            select = {
                vm.selectBook(it)
                selector = false
            },
        )
    }
}

@Composable
private fun TafsirNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            QuranReferenceInput.canonicalizeDigits(candidate)?.let(onValueChange)
        },
        modifier = modifier.testTag(tag),
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TafsirIssuePanel(issue: TafsirIssue, retry: () -> Unit) {
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
private fun TafsirReadingContent(
    state: TafsirUiState,
    retry: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
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
                MessagePanel(stringResource(R.string.offline_notice), false)
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
private fun TafsirSourceSheet(
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
                MessagePanel(stringResource(R.string.offline_notice), false)
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
