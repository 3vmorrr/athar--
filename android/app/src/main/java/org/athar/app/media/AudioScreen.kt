package org.athar.app.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import org.athar.app.quran.AyahText

@Composable fun AudioScreen(graph: AppGraph, radio: Boolean = false, expandedOnly: Boolean = false) {
    val vm = audioViewModel(graph)
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val surah = selection.surah; val ayah = selection.ayah
    val selected = selection.reader; val edition = selection.edition
    val verseMode = selection.verseMode; val follow = selection.follow
    var selector by rememberSaveable { mutableStateOf(false) }; var surahSelector by rememberSaveable { mutableStateOf(false) }
    val selectedIsLoaded = !state.radio && state.mediaKey == selection.mediaKey
    fun update(value: AudioSelection) = vm.select(value)
    var verses by remember { mutableStateOf<List<AyahText>>(emptyList()) }
    var names by remember { mutableStateOf<List<org.athar.app.quran.SurahReference>>(emptyList()) }
    LaunchedEffect(surah) { val data = graph.quran.data(); verses = data.surah(surah); names = data.index.surahs }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val controlsHeight = maxHeight * if (radio) 0.4f else if (follow && selectedIsLoaded) 0.6f else 1f
      Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().heightIn(max = controlsHeight).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AtharScreenIdentity(stringResource(if (radio) R.string.radio else R.string.listen), AtharIcons.Headphones)
            if (state.hasMedia && (expandedOnly || state.radio == radio)) AtharExpandedPlayer(vm, state)
            if (expandedOnly && !state.hasMedia) QuietEmptyState(stringResource(R.string.empty_state))
            if (expandedOnly) {
                // This route controls the existing session, without creating a second selection/player.
            } else if (radio) {
                if (state.radio) Text(state.recording?.text("name").orEmpty(), style = MaterialTheme.typography.titleMedium)
            } else {
                OutlinedButton(onClick = { surahSelector = true }, modifier = Modifier.testTag("audio-surah")) { Text(names.getOrNull(surah - 1)?.name ?: stringResource(R.string.surah)) }
                OutlinedButton(onClick = { selector = true }, modifier = Modifier.testTag("audio-reciter")) { Text(selected?.text("name") ?: stringResource(R.string.choose_reciter)) }
                if (selected != null) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selection.supported(), key = { it.text("id") }, contentType = { "edition" }) { item ->
                        FilterChip(edition?.text("id") == item.text("id"), { update(selection.copy(edition = item)) }, label = { Text(item.text("name")) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!verseMode, { update(selection.copy(verseMode = false)) }, label = { Text(stringResource(R.string.full_surah)) })
                    FilterChip(verseMode, { update(selection.copy(verseMode = true)) }, label = { Text(stringResource(R.string.one_ayah)) })
                }
                if (verseMode) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { update(selection.copy(ayah = ayah - 1)) }, enabled = ayah > 1) { Text("−") }
                    Text(stringResource(R.string.ayah) + " $ayah", Modifier.padding(12.dp))
                    TextButton(onClick = { update(selection.copy(ayah = ayah + 1)) }, enabled = ayah < verses.size) { Text("+") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.playSelection() }, enabled = selected != null && edition != null && !state.loading) { Text(stringResource(if (selectedIsLoaded && state.playing) R.string.pause else R.string.play)) }
                    OutlinedButton(onClick = vm::stop) { Text(stringResource(R.string.stop)) }
                }
                Row { Checkbox(follow, { vm.select(selection.copy(follow = it)) }); Text(stringResource(R.string.follow_ayah), Modifier.padding(top = 12.dp)) }
            }
            if (state.loading || state.buffer) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error) MessagePanel(stringResource(R.string.audio_failed), true)
            if (!radio && selectedIsLoaded && state.recording != null && state.timings.isEmpty() && !state.loading) Text(stringResource(R.string.no_timing), style = MaterialTheme.typography.bodySmall)
        }
        if (radio && !expandedOnly) Box(Modifier.weight(1f)) { DataScreen(graph, "quran/radios", mapOf("per_page" to "50")) { data, _ -> LazyColumn(contentPadding = PaddingValues(16.dp)) { items(data.array("items"), key = { it.text("id") }, contentType = { "station" }) { station -> TextButton(onClick = { vm.radio(station) }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(station.text("name")) } } } } }
        else if (!expandedOnly && follow && selectedIsLoaded) Box(Modifier.weight(1f)) { FollowAlong(vm, state, verses) }
      }
    }
    if (selector) ReaderDialog(graph, surah, { selector = false }) { reader -> update(selection.withReader(reader)); selector = false }
    if (surahSelector) AlertDialog(onDismissRequest = { surahSelector = false }, title = { Text(stringResource(R.string.surah)) }, text = { LazyColumn(Modifier.heightIn(max = 420.dp)) { items(names, key = { it.number }, contentType = { "surah" }) { row -> TextButton(onClick = { update(selection.withSurah(row.number)); surahSelector = false }, Modifier.fillMaxWidth()) { Text("${row.number}. ${row.name}") } } } }, confirmButton = { TextButton(onClick = { surahSelector = false }) { Text(stringResource(R.string.close)) } })
}

@Composable private fun FollowAlong(vm: AudioViewModel, state: AudioUiState, verses: List<AyahText>) {
    val reduced = reducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var current by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm, state.timings, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { val position = vm.position(); current = state.timings.firstOrNull { position >= it.start && position < it.end }?.ayah ?: 0; delay(200) }
        }
    }
    val scroll = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current, reduced) { if (current > 0) {
        val target = (current - 1).coerceIn(0, (verses.size - 1).coerceAtLeast(0))
        if (reduced) scroll.scrollToItem(target) else scroll.animateScrollToItem(target)
    } }
    LazyColumn(state = scroll, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(verses, key = { it.position.globalIndex }, contentType = { "ayah" }) { verse -> Surface(color = if (verse.position.ayah == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) { Text(verse.text, Modifier.fillMaxWidth().padding(12.dp), fontFamily = QuranFont, style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 50.sp)) } } }
}

@Composable private fun ReaderDialog(graph: AppGraph, surah: Int, dismiss: () -> Unit, select: (JsonElement) -> Unit) {
    var input by remember { mutableStateOf("") }; var query by remember { mutableStateOf("") }; var page by remember { mutableIntStateOf(1) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.choose_reciter)) }, text = {
        Column(Modifier.height(440.dp)) {
            Row { OutlinedTextField(input, { input = it.take(80) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.search)) }); TextButton(onClick = { query = input; page = 1 }) { Text(stringResource(R.string.search)) } }
            DataScreen(graph, "quran/reciters", mapOf("surah" to "$surah", "q" to query, "page" to "$page", "per_page" to "25")) { data, _ ->
                LazyColumn { items(data.array("items"), key = { it.text("id") }, contentType = { "reader" }) { reader -> TextButton(onClick = { select(reader) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(reader.text("name")) } }
                    item { Row { TextButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.back)) }; TextButton(onClick = { page++ }, enabled = data.asJsonObject["pagination"].text("has_more") == "true") { Text(stringResource(R.string.more)) } } }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
}
