package org.athar.app.quran

import org.athar.app.core.ui.AtharIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

data class QuranState(val loading: Boolean = true, val index: QuranIndex? = null, val verses: List<AyahText> = emptyList(), val surah: Int = 1, val error: Boolean = false, val sectionKind: String = "surah", val sectionNumber: Int = 1, val range: QuranRange? = null, val scrollTarget: Int? = null, val scrollRequest: Long = 0, val searchPage: QuranSearchPage? = null, val searchLoading: Boolean = false, val searchError: Boolean = false)
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

@Composable fun QuranScreen(graph: AppGraph, fontSize: Int, initialSurah: Int? = null, initialAyah: Int? = null) {
    if (initialSurah != null && initialAyah != null) {
        QuranReaderScreen(graph, fontSize, initialSurah, initialAyah)
        return
    }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(mode != null) { mode = null }
    Column(Modifier.fillMaxSize()) {
        if (mode == null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item(key = "quran-identity", contentType = "heading") {
                    AtharScreenIdentity(stringResource(R.string.quran), AtharIcons.MenuBook, subtitle = stringResource(R.string.quran_invitation))
                }
                item(key = "quran-actions", contentType = "controls") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 320.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f) 2 else 1
                        val actions = listOf("read" to R.string.read, "listen" to R.string.listen, "tafsir" to R.string.tafsir, "radio" to R.string.radio)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            actions.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (key, label) -> AtharQuickAction(
                                    icon = when (key) { "read" -> AtharIcons.MenuBook; "listen" -> AtharIcons.Headphones; "tafsir" -> AtharIcons.School; else -> AtharIcons.PlayArrow },
                                    label = stringResource(label), onClick = { mode = key }, modifier = Modifier.weight(1f).testTag("quran-action-$key"), emphasized = key == "read",
                                ) }
                            } }
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = { mode = null }, Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.quran_tools)) }
            Box(Modifier.weight(1f)) { when(mode) { "read" -> QuranReaderScreen(graph, fontSize); "tafsir" -> TafsirScreen(graph, fontSize); else -> org.athar.app.media.AudioScreen(graph, mode == "radio") } }
        }
    }
}

@Composable private fun QuranReaderScreen(graph: AppGraph, fontSize: Int, initialSurah: Int? = null, initialAyah: Int? = null) {
    val vm: QuranViewModel = viewModel(key = "reader:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = QuranViewModel(graph) as T })
    LaunchedEffect(vm, initialSurah, initialAyah) {
        if (initialSurah != null && initialAyah != null) vm.select(initialSurah, initialAyah)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val searchPage = state.searchPage
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val storedReading by vm.reading.collectAsStateWithLifecycle()
    val reading=(storedReading as? ProtectedRead.Ready)?.value
    val listState = rememberLazyListState()
    val unreadablePosition = storedReading == ProtectedRead.Unreadable
    LaunchedEffect(state.scrollRequest, unreadablePosition) {
        if (state.verses.isNotEmpty()) listState.requestScrollToItem(state.verses.indexOfFirst { it.position.globalIndex == state.scrollTarget }.coerceAtLeast(0) + if (unreadablePosition) 1 else 0)
    }
    var selectedAyah by remember { mutableStateOf<AyahText?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var navigationSheet by remember { mutableStateOf(false) }
    var navigationKind by remember { mutableStateOf("surah") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm, searchMode, query) {
        vm.clearSearch()
        if (searchMode && QuranSearchNormalizer.normalize(query).length >= 2) {
            delay(250)
            vm.search(query)
        }
    }
    var fontDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.quran_share_ayah)
    val copied = stringResource(R.string.quran_ayah_copied)
    val saved = stringResource(R.string.saved); val failed = stringResource(R.string.network_error)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            reading?.let { position ->
                Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.reading_position_label, state.index?.surahs?.getOrNull(position.surah - 1)?.name ?: "${position.surah}", position.ayah), style = MaterialTheme.typography.bodySmall)
                                if (position.pending && !position.conflict) Text(stringResource(R.string.reading_pending), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { searchMode = false; vm.select(position.surah, position.ayah) }) { Icon(AtharIcons.ArrowForward, stringResource(R.string.resume_reading)) }
                            if (position.pending && !position.conflict) TextButton(onClick = { scope.launch { try { vm.syncReading() } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(R.string.retry)) }
                        }
                        if (position.conflict) {
                            Text(stringResource(R.string.reading_conflict), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true to R.string.reading_use_account, false to R.string.reading_keep_device).forEach { (remote, label) ->
                                    TextButton(onClick = { scope.launch { try { vm.resolvePosition(remote) } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(label)) }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!searchMode, { searchMode = false; vm.select(state.surah.takeIf { it > 0 } ?: 1) }, label = { Text(stringResource(R.string.read)) })
                FilterChip(searchMode, { searchMode = true }, label = { Text(stringResource(R.string.search)) })
                TextButton(onClick = { fontDialog = true }) { Text(stringResource(R.string.font_size)) }
            }
            if (searchMode) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it.take(80); vm.clearSearch() }, Modifier.weight(1f), label = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
                Button(onClick = { vm.search(query) }, enabled = QuranSearchNormalizer.normalize(query).length >= 2, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) { Text(stringResource(R.string.search)) }
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
                                if (kind == "range") { navigationKind = kind; navigationSheet = true }
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
                    TextButton(onClick = { navigationKind = state.sectionKind; navigationSheet = true }) {
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
                state.verses.isEmpty() -> Text(stringResource(R.string.empty_state), Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize().testTag("quran-reader-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if(storedReading==ProtectedRead.Unreadable)item(key="unreadable-position",contentType="state"){MessagePanel(stringResource(R.string.local_data_unreadable),true){TextButton(onClick=vm::retryReading){Text(stringResource(R.string.retry))}}}
                    items(state.verses, key = { it.position.globalIndex }, contentType = { "ayah" }) { ayah ->
                        Column(Modifier.fillMaxWidth().testTag("quran-ayah-${ayah.position.globalIndex}")) {
                            if (ayah.bismillah != null) Text(ayah.bismillah, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                            Text(ayah.text, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.95).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${ayah.position.surah}:${ayah.position.ayah}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { scope.launch { try { vm.savePosition(ayah); snackbar.showSnackbar(saved) } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }, enabled = storedReading is ProtectedRead.Ready&&reading?.conflict != true) { Text(stringResource(R.string.set_reading_position)) }
                                MarkToggle("ayah-${ayah.position.globalIndex}", ayah.position.globalIndex in bookmarks, false, snackbar) { selected -> vm.bookmark(ayah, selected) }
                                TextButton(onClick = { selectedAyah = ayah }) { Text(stringResource(R.string.more)) }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    item(key = "source", contentType = "source") { Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (navigationSheet) state.index?.let { index ->
        QuranNavigationSheet(index, state, { navigationSheet = false }, vm::select, vm::section, vm::range, initialKind = navigationKind)
    }
    if (fontDialog) {
        var size by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
        AlertDialog(onDismissRequest = { fontDialog = false }, title = { Text(stringResource(R.string.font_size)) }, text = { Slider(size, { size = it }, valueRange = 18f..48f) }, confirmButton = { TextButton(onClick = { scope.launch { graph.settings.quranFontSize(size.toInt()) }; fontDialog = false }) { Text(stringResource(R.string.save)) } })
    }
    selectedAyah?.let { ayah ->
        AlertDialog(
            onDismissRequest = { selectedAyah = null },
            title = { Text("${ayah.position.surah}:${ayah.position.ayah}") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text(ayah.text, fontFamily = QuranFont, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            QuranAyahShareActions.copy(context, QuranAyahShareContract.from(ayah))
                            selectedAyah = null
                            snackbar.showSnackbar(copied)
                        } catch (failure: CancellationException) { throw failure }
                        catch (_: Exception) { snackbar.showSnackbar(failed) }
                    }
                }) { Text(stringResource(R.string.quran_copy_ayah)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    try {
                        QuranAyahShareActions.share(context, QuranAyahShareContract.from(ayah), shareTitle)
                        selectedAyah = null
                    } catch (_: Exception) { scope.launch { snackbar.showSnackbar(failed) } }
                }) { Text(shareTitle) }
            },
        )
    }
}
