package org.athar.app.quran

import org.athar.app.core.ui.AtharIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
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

data class QuranState(val loading: Boolean = true, val index: QuranIndex? = null, val verses: List<AyahText> = emptyList(), val surah: Int = 1, val error: Boolean = false, val sectionKind: String = "surah", val sectionNumber: Int = 1, val scrollTarget: Int? = null, val scrollRequest: Long = 0)
class QuranViewModel(private val graph: AppGraph) : ViewModel() {
    private val mutable = MutableStateFlow(QuranState())
    val state = mutable.asStateFlow()
    private val mutableBookmarks = MutableStateFlow<Set<Int>>(emptySet())
    val bookmarks = mutableBookmarks.asStateFlow()
    val reading = graph.reading.observe(requireNotNull(graph.session.userId)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private var load: Job? = null
    init {
        select(1)
        viewModelScope.launch { try { graph.reading.refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local resume remains available offline. */ } }
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
        load?.cancel(); load = viewModelScope.launch {
            try { val data = graph.quran.data(); mutable.value = QuranState(false, data.index, data.section(kind, number), 0, sectionKind = kind, sectionNumber = number, scrollRequest = mutable.value.scrollRequest + 1) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
        }
    }
    fun search(query: String) {
        if (query.trim().length < 2) return
        load?.cancel(); load = viewModelScope.launch {
            try { val data = graph.quran.data(); val matches = withContext(Dispatchers.Default) { data.search(query.take(80)) }; mutable.value = QuranState(false, data.index, matches, 0, scrollRequest = mutable.value.scrollRequest + 1) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
        }
    }
    suspend fun bookmark(ayah: AyahText, saved: Boolean) {
        graph.session.request(if (saved) "POST" else "DELETE", "quran/bookmarks", mapOf("surah" to ayah.position.surah, "ayah" to ayah.position.ayah))
        mutableBookmarks.value = if (saved) mutableBookmarks.value + ayah.position.globalIndex else mutableBookmarks.value - ayah.position.globalIndex
        graph.content.invalidate("quran/bookmarks")
    }
}

@Composable fun QuranScreen(graph: AppGraph, fontSize: Int) {
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(mode != null) { mode = null }
    Column(Modifier.fillMaxSize()) {
        if (mode == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "quran-hero") {
                    AtharHeroSurface(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.quran),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.quran_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { mode = "read" },
                            modifier = Modifier.heightIn(min = 52.dp),
                        ) {
                            Icon(AtharIcons.MenuBook, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.read))
                        }
                    }
                }
                item(key = "quran-tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AtharSectionHeader(stringResource(R.string.quran_tools))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                TextButton(
                    onClick = { mode = null },
                    Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(AtharIcons.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.quran_tools))
                }
            }
            Box(Modifier.weight(1f)) {
                when(mode) {
                    "read" -> QuranReaderScreen(graph, fontSize)
                    "tafsir" -> TafsirScreen(graph, fontSize)
                    else -> org.athar.app.media.AudioScreen(graph, mode == "radio")
                }
            }
        }
    }
}

@Composable private fun QuranReaderScreen(graph: AppGraph, fontSize: Int) {
    val vm: QuranViewModel = viewModel(key = "reader:${graph.session.userId}", factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = QuranViewModel(graph) as T })
    val state by vm.state.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollRequest) {
        if (state.verses.isNotEmpty()) listState.requestScrollToItem(state.verses.indexOfFirst { it.position.globalIndex == state.scrollTarget }.coerceAtLeast(0))
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selector by remember { mutableStateOf(false) }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) { if(searchMode && query.trim().length >= 2)vm.search(query) }
    var fontDialog by remember { mutableStateOf(false) }
    var numberDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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
                            if (position.pending && !position.conflict) TextButton(onClick = { scope.launch { try { graph.reading.sync() } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(R.string.retry)) }
                        }
                        if (position.conflict) {
                            Text(stringResource(R.string.reading_conflict), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true to R.string.reading_use_account, false to R.string.reading_keep_device).forEach { (remote, label) ->
                                    TextButton(onClick = { scope.launch { try { graph.reading.resolve(remote) } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }) { Text(stringResource(label)) }
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
                OutlinedTextField(query, { query = it.take(80) }, Modifier.weight(1f), label = { Text(stringResource(R.string.search_hint)) }, singleLine = true)
                Button(onClick = { vm.search(query) }, enabled = query.trim().length >= 2, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) { Text(stringResource(R.string.search)) }
            } else {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("surah" to R.string.surah,"juz" to R.string.juz,"hizb" to R.string.hizb,"page" to R.string.page).forEach { (kind,label) -> FilterChip(state.sectionKind == kind, { vm.section(kind, 1) }, label = { Text(stringResource(label)) }) }
                }
                val maximum = when(state.sectionKind) { "page" -> 604; "juz" -> 30; "hizb" -> 60; else -> 114 }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.section(state.sectionKind, state.sectionNumber - 1) }, enabled = state.sectionNumber > 1) { Text(stringResource(R.string.back)) }
                    TextButton(onClick = { if (state.sectionKind == "surah") selector = true else numberDialog = true }) { Text(state.index?.surahs?.getOrNull(state.surah - 1)?.name ?: "${state.sectionNumber}") }
                    TextButton(onClick = { vm.section(state.sectionKind, state.sectionNumber + 1) }, enabled = state.sectionNumber < maximum) { Text(stringResource(R.string.more)) }
                }
            }
            when {
                state.loading -> LoadingScreen()
                state.error -> FailurePanel(null) { vm.select(1) }
                state.verses.isEmpty() -> Text(stringResource(R.string.empty_state), Modifier.padding(24.dp))
                else -> LazyColumn(Modifier.fillMaxSize().testTag("quran-reader-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.verses, key = { it.position.globalIndex }, contentType = { "ayah" }) { ayah ->
                        Column(Modifier.fillMaxWidth().testTag("quran-ayah-${ayah.position.globalIndex}")) {
                            if (ayah.bismillah != null) Text(ayah.bismillah, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                            Text(ayah.text, Modifier.fillMaxWidth(), fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.95).sp, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${ayah.position.surah}:${ayah.position.ayah}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { scope.launch { try { graph.reading.save(ayah.position.surah, ayah.position.ayah); snackbar.showSnackbar(saved) } catch (e: CancellationException) { throw e } catch (_: Exception) { snackbar.showSnackbar(failed) } } }, enabled = reading?.conflict != true) { Text(stringResource(R.string.set_reading_position)) }
                                MarkToggle("ayah-${ayah.position.globalIndex}", ayah.position.globalIndex in bookmarks, false, snackbar) { selected -> vm.bookmark(ayah, selected) }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    item(key = "source", contentType = "source") { Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (selector) AlertDialog(onDismissRequest = { selector = false }, title = { Text(stringResource(R.string.surah)) }, text = {
        LazyColumn(Modifier.heightIn(max = 460.dp)) { items(state.index?.surahs.orEmpty(), key = { it.number }, contentType = { "surah" }) { surah -> TextButton(onClick = { vm.select(surah.number); selector = false }, Modifier.fillMaxWidth()) { Text("${surah.number}. ${surah.name}") } } }
    }, confirmButton = { TextButton(onClick = { selector = false }) { Text(stringResource(R.string.close)) } })
    if (fontDialog) {
        var size by remember(fontSize) { mutableFloatStateOf(fontSize.toFloat()) }
        AlertDialog(onDismissRequest = { fontDialog = false }, title = { Text(stringResource(R.string.font_size)) }, text = { Slider(size, { size = it }, valueRange = 18f..48f) }, confirmButton = { TextButton(onClick = { scope.launch { graph.settings.quranFontSize(size.toInt()) }; fontDialog = false }) { Text(stringResource(R.string.save)) } })
    }
    if (numberDialog) {
        var number by remember { mutableStateOf("${state.sectionNumber}") }
        val maximum = when(state.sectionKind) { "page" -> 604; "juz" -> 30; else -> 60 }
        AlertDialog(onDismissRequest = { numberDialog = false }, title = { Text(stringResource(when(state.sectionKind) { "page" -> R.string.page; "juz" -> R.string.juz; else -> R.string.hizb })) }, text = { OutlinedTextField(number, { number = it.filter(Char::isDigit).take(3) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) }, confirmButton = { TextButton(enabled = (number.toIntOrNull() ?: 0) in 1..maximum, onClick = { vm.section(state.sectionKind, number.toInt()); numberDialog = false }) { Text(stringResource(R.string.read)) } })
    }
}
