package org.athar.app.quran

import org.athar.app.core.ui.AtharIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads

data class QuranState(
    val loading: Boolean = true,
    val index: QuranIndex? = null,
    val verses: List<AyahText> = emptyList(),
    val surah: Int = 1,
    val error: Boolean = false,
    val sectionKind: String = "surah",
    val sectionNumber: Int = 1,
    val range: QuranRange? = null,
    val scrollTarget: Int? = null,
    val scrollRequest: Long = 0,
    val searchPage: QuranSearchPage? = null,
    val searchLoading: Boolean = false,
    val searchError: Boolean = false,
)
class QuranViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutable = MutableStateFlow(QuranState())
    val state = mutable.asStateFlow()
    private val mutableBookmarks = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarks = mutableBookmarks.asStateFlow()
    private val readingRetry=MutableStateFlow(0)
    private val readerOwner=requireNotNull(graph.session.userId)
    @OptIn(ExperimentalCoroutinesApi::class)
    val reading = readingRetry.flatMapLatest { graph.reading.observe(readerOwner).protectedReads() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProtectedRead.Loading)
    fun retryReading(){readingRetry.value++}
    private var load: Job? = null
    private var searchLoad: Job? = null
    init {
        select(1)
        viewModelScope.launch { try { graph.reading.refresh(readerOwner) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local resume remains available offline. */ } }
        viewModelScope.launch {
            try { val data = graph.content.get("quran/bookmarks").data; mutableBookmarks.value = data.asJsonArray.map { row -> graph.quran.data().index.global(row.text("surah").toInt(), row.text("ayah").toInt()) }.toSet() }
            catch (failure: CancellationException) { throw failure } catch (_: Exception) { /* Initial marker lookup may be unavailable offline. */ }
        }
    }
    fun select(number: Int, ayah: Int = 1) {
        load?.cancel(); load = viewModelScope.launch {
            try { val data = graph.quran.data(); mutable.value = QuranState(false, data.index, data.surah(number), number, sectionNumber = number, scrollTarget = data.index.global(number, ayah), scrollRequest = mutable.value.scrollRequest + 1) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = QuranState(loading = false, error = true) }
        }
    }
    fun section(kind: String, number: Int) {
        if (kind == "surah") { select(number); return }
        load?.cancel()
        load = viewModelScope.launch {
            try {
                val data = graph.quran.data()
                val interval = data.index.section(kind, number)
                mutable.value = QuranState(
                    loading = false,
                    index = data.index,
                    verses = data.section(kind, number),
                    surah = 0,
                    sectionKind = kind,
                    sectionNumber = number,
                    scrollTarget = interval.first,
                    scrollRequest = mutable.value.scrollRequest + 1,
                )
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
        }
    }
    fun range(target: QuranRange) {
        load?.cancel()
        load = viewModelScope.launch {
            try {
                val data = graph.quran.data()
                val canonical = data.index.range(
                    target.start.surah,
                    target.start.ayah,
                    target.end.surah,
                    target.end.ayah,
                )
                mutable.value = QuranState(
                    loading = false,
                    index = data.index,
                    verses = data.range(canonical),
                    surah = 0,
                    sectionKind = "range",
                    sectionNumber = 0,
                    range = canonical,
                    scrollTarget = canonical.start.globalIndex,
                    scrollRequest = mutable.value.scrollRequest + 1,
                )
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
        }
    }
    fun clearSearch() {
        searchLoad?.cancel()
        mutable.value = mutable.value.copy(
            searchPage = null,
            searchLoading = false,
            searchError = false,
        )
    }

    fun search(query: String, offset: Int = 0) {
        val safeQuery = query.take(80)
        if (QuranSearchNormalizer.normalize(safeQuery).length < 2) {
            clearSearch()
            return
        }
        searchLoad?.cancel()
        searchLoad = viewModelScope.launch {
            mutable.value = mutable.value.copy(searchLoading = true, searchError = false)
            try {
                val data = graph.quran.data()
                val page = withContext(Dispatchers.Default) {
                    data.searchPage(safeQuery, offset, SEARCH_PAGE_SIZE)
                }
                mutable.value = mutable.value.copy(
                    searchPage = page,
                    searchLoading = false,
                    searchError = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    searchLoading = false,
                    searchError = true,
                )
            }
        }
    }

    fun previousSearchPage() {
        val page = mutable.value.searchPage ?: return
        if (page.hasPrevious) search(page.query, (page.offset - page.limit).coerceAtLeast(0))
    }

    fun nextSearchPage() {
        val page = mutable.value.searchPage ?: return
        if (page.hasNext) search(page.query, page.offset + page.limit)
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 20
    }
    suspend fun savePosition(ayah:AyahText)=graph.reading.save(ayah.position.surah,ayah.position.ayah,readerOwner)
    suspend fun resolvePosition(remote:Boolean)=graph.reading.resolve(remote,readerOwner)
    suspend fun syncReading()=graph.reading.sync(readerOwner)
    suspend fun bookmark(ayah: AyahText, saved: Boolean) {
        graph.session.request(if (saved) "POST" else "DELETE", "quran/bookmarks", mapOf("surah" to ayah.position.surah, "ayah" to ayah.position.ayah),expectedUser=readerOwner)
        mutableBookmarks.value = if (saved) mutableBookmarks.value + ayah.position.globalIndex else mutableBookmarks.value - ayah.position.globalIndex
        graph.content.invalidate("quran/bookmarks",readerOwner)
    }
}

@Composable fun QuranScreen(
    graph: AppGraph,
    fontSize: Int,
    initialSurah: Int? = null,
    initialAyah: Int? = null,
) {
    val vm: QuranViewModel = viewModel(
        key = "reader:${graph.session.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                QuranViewModel(graph) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val storedReading by vm.reading.collectAsStateWithLifecycle()
    val reading = (storedReading as? ProtectedRead.Ready)?.value
    val exactRoute = initialSurah != null && initialAyah != null
    var mode by rememberSaveable(initialSurah, initialAyah) {
        mutableStateOf<String?>(if (exactRoute) "read" else null)
    }
    LaunchedEffect(initialSurah, initialAyah) {
        if (exactRoute) vm.select(requireNotNull(initialSurah), requireNotNull(initialAyah))
    }
    androidx.activity.compose.BackHandler(!exactRoute && mode != null) { mode = null }

    Column(Modifier.fillMaxSize()) {
        if (mode == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                item(key = "quran-hero", contentType = "hero") {
                    val positionText = reading?.let { position ->
                        stringResource(
                            R.string.reading_position_label,
                            state.index?.surahs?.getOrNull(position.surah - 1)?.name
                                ?: position.surah.toString(),
                            position.ayah,
                        )
                    }
                    AtharImmersiveHero(
                        presentation = AtharHeroPresentation(
                            artwork = AtharHeroArtwork.Quran,
                            eyebrow = stringResource(R.string.quran),
                            title = stringResource(R.string.quran),
                            supportingText = when {
                                storedReading == ProtectedRead.Unreadable ->
                                    stringResource(R.string.local_data_unreadable)
                                positionText != null -> positionText
                                else -> stringResource(R.string.quran_source)
                            },
                            statusText = when {
                                storedReading == ProtectedRead.Unreadable ->
                                    stringResource(R.string.local_data_unreadable)
                                reading?.conflict == true ->
                                    stringResource(R.string.reading_conflict)
                                reading?.pending == true ->
                                    stringResource(R.string.reading_pending)
                                else -> null
                            },
                            statusTone = if (
                                storedReading == ProtectedRead.Unreadable ||
                                    reading?.conflict == true
                            ) {
                                AtharHeroStatusTone.Warning
                            } else {
                                AtharHeroStatusTone.Neutral
                            },
                            primaryActionLabel = if (
                                reading != null &&
                                    !reading.conflict &&
                                    storedReading is ProtectedRead.Ready
                            ) {
                                stringResource(R.string.resume_reading)
                            } else {
                                stringResource(R.string.read)
                            },
                        ),
                        onPrimaryAction = {
                            if (reading != null && !reading.conflict) {
                                vm.select(reading.surah, reading.ayah)
                            }
                            mode = "read"
                        },
                    )
                }

                item(key = "quran-tools", contentType = "tools") {
                    Column(
                        Modifier.padding(horizontal = AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharSectionHeader(stringResource(R.string.quran_tools))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            AtharQuickAction(
                                AtharIcons.MenuBook,
                                stringResource(R.string.read),
                                { mode = "read" },
                                Modifier.weight(1f),
                                emphasized = true,
                            )
                            AtharQuickAction(
                                AtharIcons.PlayArrow,
                                stringResource(R.string.listen),
                                { mode = "listen" },
                                Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            AtharQuickAction(
                                AtharIcons.School,
                                stringResource(R.string.tafsir),
                                { mode = "tafsir" },
                                Modifier.weight(1f),
                            )
                            AtharQuickAction(
                                AtharIcons.NightsStay,
                                stringResource(R.string.radio),
                                { mode = "radio" },
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
                item(key = "quran-wird", contentType = "routine") {
                    Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                        QuranWirdCard(graph)
                    }
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                TextButton(
                    onClick = { mode = null },
                    modifier = Modifier.padding(horizontal = AtharSpacing.Md),
                ) {
                    Icon(AtharIcons.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(AtharSpacing.Sm))
                    Text(stringResource(R.string.quran_tools))
                }
            }
            Box(Modifier.weight(1f)) {
                when (mode) {
                    "read" -> QuranReaderScreen(graph, fontSize, vm)
                    "tafsir" -> TafsirScreen(
                        graph = graph,
                        fontSize = fontSize,
                        initialSurah = reading?.surah ?: 1,
                        initialAyah = reading?.ayah ?: 1,
                    )
                    else -> org.athar.app.media.AudioScreen(graph, mode == "radio")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun QuranReaderScreen(
    graph: AppGraph,
    fontSize: Int,
    vm: QuranViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val searchPage = state.searchPage
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val storedReading by vm.reading.collectAsStateWithLifecycle()
    val reading=(storedReading as? ProtectedRead.Ready)?.value
    val mushafScrollState = rememberScrollState()
    var selectedAyah by remember { mutableStateOf<AyahText?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var navigationSheet by remember { mutableStateOf(false) }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchMode, query) {
        if (!searchMode) return@LaunchedEffect
        if (QuranSearchNormalizer.normalize(query).length < 2) {
            vm.clearSearch()
        } else {
            kotlinx.coroutines.delay(250)
            vm.search(query)
        }
    }
    var fontDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.saved); val failed = stringResource(R.string.network_error)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                storedReading == ProtectedRead.Unreadable -> {
                    MessagePanel(stringResource(R.string.local_data_unreadable), true) {
                        TextButton(onClick = vm::retryReading) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                reading?.conflict == true -> {
                    MessagePanel(stringResource(R.string.reading_conflict), true) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                            listOf(
                                true to R.string.reading_use_account,
                                false to R.string.reading_keep_device,
                            ).forEach { (remote, label) ->
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                vm.resolvePosition(remote)
                                            } catch (failure: CancellationException) {
                                                throw failure
                                            } catch (_: Exception) {
                                                snackbar.showSnackbar(failed)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(label))
                                }
                            }
                        }
                    }
                }
                reading?.pending == true -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Xs),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        AtharStatusPill(
                            stringResource(R.string.reading_pending),
                            Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        vm.syncReading()
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        snackbar.showSnackbar(failed)
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!searchMode, { searchMode = false; vm.select(state.surah.takeIf { it > 0 } ?: 1) }, label = { Text(stringResource(R.string.read)) })
                FilterChip(searchMode, { searchMode = true }, label = { Text(stringResource(R.string.search)) })
                TextButton(onClick = { fontDialog = true }) { Text(stringResource(R.string.font_size)) }
            }
            if (searchMode) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(80) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                    )
                    Button(
                        onClick = { vm.search(query) },
                        enabled = QuranSearchNormalizer.normalize(query).length >= 2,
                    ) {
                        Text(stringResource(R.string.search))
                    }
                }
            } else {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "surah" to R.string.surah,
                        "juz" to R.string.juz,
                        "hizb" to R.string.hizb,
                        "page" to R.string.page,
                        "range" to R.string.quran_range,
                    ).forEach { (kind, label) ->
                        FilterChip(
                            selected = state.sectionKind == kind,
                            onClick = {
                                if (kind == "range") navigationSheet = true
                                else vm.section(kind, 1)
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                val maximum = when (state.sectionKind) {
                    "surah" -> 114
                    "juz", "hizb", "page" -> state.index
                        ?.let { runCatching { it.sectionCount(state.sectionKind) }.getOrNull() } ?: 0
                    else -> 0
                }
                val stepEnabled = state.sectionKind in setOf("surah", "juz", "hizb", "page")
                val selectionLabel = when (state.sectionKind) {
                    "surah" -> state.index?.surahs?.getOrNull(state.surah - 1)?.name
                        ?: state.sectionNumber.toString()
                    "juz" -> stringResource(R.string.quran_section_label, stringResource(R.string.juz), state.sectionNumber)
                    "hizb" -> stringResource(R.string.quran_section_label, stringResource(R.string.hizb), state.sectionNumber)
                    "page" -> stringResource(R.string.quran_section_label, stringResource(R.string.page), state.sectionNumber)
                    "range" -> state.range?.let {
                        stringResource(
                            R.string.quran_range_label,
                            it.start.surah,
                            it.start.ayah,
                            it.end.surah,
                            it.end.ayah,
                        )
                    } ?: stringResource(R.string.quran_range)
                    else -> stringResource(R.string.quran_go_to)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { vm.section(state.sectionKind, state.sectionNumber - 1) },
                        enabled = stepEnabled && state.sectionNumber > 1,
                    ) { Text(stringResource(R.string.back)) }
                    TextButton(onClick = { navigationSheet = true }) {
                        Text(selectionLabel, maxLines = 2)
                    }
                    TextButton(
                        onClick = { vm.section(state.sectionKind, state.sectionNumber + 1) },
                        enabled = stepEnabled && state.sectionNumber < maximum,
                    ) { Text(stringResource(R.string.more)) }
                }
            }
            if (searchMode) {
                when {
                    state.searchLoading -> LoadingScreen()
                    state.searchError -> FailurePanel(null) { vm.search(query) }
                    QuranSearchNormalizer.normalize(query).length < 2 -> Text(
                        stringResource(R.string.quran_search_prompt),
                        Modifier.padding(AtharSpacing.Xxl),
                    )
                    searchPage == null -> LoadingScreen()
                    searchPage.hits.isEmpty() -> Text(
                        stringResource(R.string.quran_search_no_results),
                        Modifier.padding(AtharSpacing.Xxl),
                    )
                    else -> QuranSearchResults(
                        page = searchPage,
                        fontSize = fontSize,
                        modifier = Modifier.fillMaxSize(),
                        onOpenAyah = { ayah ->
                            searchMode = false
                            vm.select(ayah.position.surah, ayah.position.ayah)
                        },
                        onLongPressAyah = { selectedAyah = it },
                        onPreviousPage = vm::previousSearchPage,
                        onNextPage = vm::nextSearchPage,
                    )
                }
            } else when {
                state.loading -> LoadingScreen()
                state.error -> FailurePanel(null) { vm.select(1) }
                state.verses.isEmpty() -> Text(
                    stringResource(R.string.empty_state),
                    Modifier.padding(AtharSpacing.Xxl),
                )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(mushafScrollState)
                        .padding(horizontal = AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    QuranMushafPage(
                        verses = state.verses,
                        fontSize = fontSize,
                        scrollState = mushafScrollState,
                        scrollTargetGlobalIndex = state.scrollTarget,
                        scrollRequest = state.scrollRequest,
                        onLongPressAyah = { selectedAyah = it },
                    )
                    Text(
                        stringResource(R.string.quran_source),
                        modifier = Modifier.padding(
                            horizontal = AtharSpacing.Sm,
                            vertical = AtharSpacing.Lg,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    selectedAyah?.let { ayah ->
        QuranAyahActionSheet(
            ayah = ayah,
            bookmarked = ayah.position.globalIndex in bookmarks,
            canSavePosition = storedReading is ProtectedRead.Ready &&
                reading?.conflict != true,
            onDismiss = { selectedAyah = null },
            onSavePosition = {
                scope.launch {
                    try {
                        vm.savePosition(ayah)
                        snackbar.showSnackbar(saved)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        snackbar.showSnackbar(failed)
                    }
                }
            },
            onToggleBookmark = {
                scope.launch {
                    try {
                        vm.bookmark(
                            ayah,
                            ayah.position.globalIndex !in bookmarks,
                        )
                        snackbar.showSnackbar(saved)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        snackbar.showSnackbar(failed)
                    }
                }
            },
        )
    }

    if (navigationSheet) {
        state.index?.let { index ->
            QuranNavigationSheet(
                index = index,
                state = state,
                dismiss = { navigationSheet = false },
                openPosition = { surah, ayah -> vm.select(surah, ayah) },
                openSection = { kind, number -> vm.section(kind, number) },
                openRange = vm::range,
            )
        }
    }

    if (fontDialog) {
        var size by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
        ModalBottomSheet(onDismissRequest = { fontDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Xl)
                    .padding(bottom = AtharSpacing.Xxl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                Text(
                    stringResource(R.string.font_size),
                    style = MaterialTheme.typography.titleLarge,
                )
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 18f..48f,
                )
                Button(
                    onClick = {
                        scope.launch { graph.settings.quranFontSize(size.toInt()) }
                        fontDialog = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }


}
