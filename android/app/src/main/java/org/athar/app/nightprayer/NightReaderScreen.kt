package org.athar.app.nightprayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.SessionLease
import org.athar.app.core.ui.*
import org.athar.app.quran.QuranAyahText
import org.athar.app.quran.QuranRange

internal sealed interface NightReaderEvent {
    data object Back : NightReaderEvent
    data object Retry : NightReaderEvent
    data object Resume : NightReaderEvent
    data object Start : NightReaderEvent
    data class Save(val index: Int, val version: Long) : NightReaderEvent
    data class Clear(val version: Long) : NightReaderEvent
    data class Reset(val version: Long) : NightReaderEvent
}

@Composable internal fun NightReaderScreen(graph: AppGraph, lease: SessionLease, scope: NightReadingScope, fontSize: Int, back: () -> Unit) {
    key(lease, scope) { NightReaderOwned(graph, lease, scope, fontSize, back) }
}

@Composable private fun NightReaderOwned(graph: AppGraph, lease: SessionLease, scope: NightReadingScope, fontSize: Int, back: () -> Unit) {
    val vm: NightReaderViewModel = viewModel(key = "night-reader:${scope.namespace}:${scope.owner}:${lease.generation}:${scope.kind}:${scope.id}:${scope.start}:${scope.end}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NightReaderViewModel(scope, object : NightReaderGateway {
            override fun requireActive() = graph.session.requireLease(lease)
            override fun changes() = graph.nightReading.observe(lease, scope)
            override suspend fun data() = graph.quran.data()
            override suspend fun read() = graph.nightReading.read(lease, scope)
            override suspend fun save(index: Int, expected: Long) = graph.nightReading.save(lease, scope, index, expected)
            override suspend fun clear(expected: Long) = graph.nightReading.clear(lease, scope, expected)
            override suspend fun reset(expected: Long) = graph.nightReading.resetChangedRange(lease, scope, expected)
        }) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm, lease) { graph.session.state.collect { if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate() } }
    BackHandler(onBack = back)
    if (runCatching { graph.session.requireLease(lease) }.isFailure) return
    NightReaderContent(state, fontSize) { event ->
        if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate()
        else when (event) {
            NightReaderEvent.Back -> back()
            NightReaderEvent.Retry -> vm.retry()
            NightReaderEvent.Resume -> vm.resume()
            NightReaderEvent.Start -> vm.start()
            is NightReaderEvent.Save -> vm.save(event.index, event.version)
            is NightReaderEvent.Clear -> vm.clear(event.version)
            is NightReaderEvent.Reset -> vm.reset(event.version)
        }
    }
}

/** Runtime scrolling is deliberately not SavedState; explicit markers live in owned encrypted storage. */
@Composable internal fun NightReaderContent(state: NightReaderUiState, fontSize: Int = 28, event: (NightReaderEvent) -> Unit) {
    val list = remember { LazyListState() }
    var clearVersion by remember { mutableStateOf<Long?>(null) }
    var resetVersion by remember { mutableStateOf<Long?>(null) }
    val enabled = state.ready && !state.busy
    LaunchedEffect(state.scrollRequest, state.ready) {
        if (state.ready && state.scrollRequest > 0) {
            val offset = state.verses.indexOfFirst { it.position.globalIndex == state.scrollIndex }
            if (offset >= 0) list.scrollToItem(offset + 1)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("night-reader"), state = list,
        contentPadding = PaddingValues(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
        item(key = "identity", contentType = "heading") { Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            TextButton(onClick = { event(NightReaderEvent.Back) }) { Text(stringResource(R.string.back)) }
            AtharSectionHeader(stringResource(R.string.night_reader_title))
            state.range?.let { Text(readerReference(it), style = MaterialTheme.typography.titleMedium) }
            Text(stringResource(R.string.night_reader_notice), style = MaterialTheme.typography.bodySmall)
            if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error -> MessagePanel(stringResource(when (error) {
                NightReaderError.UNREADABLE -> R.string.local_data_unreadable
                NightReaderError.SOURCE_CHANGED -> R.string.night_reader_source_changed
                NightReaderError.RANGE_CHANGED -> R.string.night_reader_range_changed
                NightReaderError.DATA_UNAVAILABLE -> R.string.network_error
            }), true) {
                if (error == NightReaderError.RANGE_CHANGED && state.review != null) {
                    state.reviewRange?.let { Text(stringResource(R.string.night_reader_old_range, readerReference(it))) }
                    state.range?.let { Text(stringResource(R.string.night_reader_new_range, readerReference(it))) }
                    TextButton(onClick = { resetVersion = state.review.version }, enabled = !state.busy, modifier = Modifier.testTag("night-reader-reset")) { Text(stringResource(R.string.night_reader_reset_range)) }
                }
                TextButton(onClick = { event(NightReaderEvent.Retry) }, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
            } }
            state.feedback?.let { feedback -> Text(stringResource(when (feedback) {
                NightReaderFeedback.SAVED -> R.string.saved
                NightReaderFeedback.CLEARED -> R.string.night_reader_cleared
                NightReaderFeedback.CHANGED -> R.string.night_reader_marker_changed
                NightReaderFeedback.UNCONFIRMED -> R.string.night_plan_unconfirmed
                NightReaderFeedback.INVALID -> R.string.invalid_input
            })) }
            if (state.ready) {
                val snapshot = requireNotNull(state.snapshot)
                if (snapshot.recordedComplete) Text(stringResource(R.string.night_reader_recorded_complete))
                snapshot.position.index?.let { index ->
                    val position = state.verses.first { it.position.globalIndex == index }.position
                    Text(stringResource(R.string.night_reader_position, "${position.surah}:${position.ayah}"), modifier = Modifier.testTag("night-reader-marker"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    TextButton(onClick = { event(NightReaderEvent.Start) }, enabled = enabled) { Text(stringResource(R.string.night_reader_start)) }
                    if (snapshot.position.index != null) {
                        TextButton(onClick = { event(NightReaderEvent.Resume) }, enabled = enabled) { Text(stringResource(R.string.resume_reading)) }
                        TextButton(onClick = { clearVersion = snapshot.position.version }, enabled = enabled, modifier = Modifier.testTag("night-reader-clear")) { Text(stringResource(R.string.night_reader_clear)) }
                    }
                }
            }
        } }
        if (state.ready) items(state.verses, key = { it.position.globalIndex }, contentType = { "ayah" }) { ayah ->
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().testTag("night-reader-ayah-${ayah.position.globalIndex}"), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                Text("${ayah.position.surah}:${ayah.position.ayah}", style = MaterialTheme.typography.labelMedium)
                QuranAyahText(ayah, fontSize, Modifier.fillMaxWidth())
                TextButton(onClick = { event(NightReaderEvent.Save(ayah.position.globalIndex, requireNotNull(state.snapshot).position.version)) }, enabled = enabled,
                    modifier = Modifier.testTag("night-reader-save-${ayah.position.globalIndex}")) { Text(stringResource(R.string.night_reader_save)) }
                HorizontalDivider()
            }
        }
        item(key = "source", contentType = "source") { Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall) }
    }
    clearVersion?.let { version -> ReaderConfirmation(R.string.night_reader_clear_confirm, !state.busy, { clearVersion = null }) { clearVersion = null; event(NightReaderEvent.Clear(version)) } }
    resetVersion?.let { version -> ReaderConfirmation(R.string.night_reader_reset_confirm, !state.busy, { resetVersion = null }) { resetVersion = null; event(NightReaderEvent.Reset(version)) } }
}

private fun readerReference(range: QuranRange) = "${range.start.surah}:${range.start.ayah} — ${range.end.surah}:${range.end.ayah}"
@Composable private fun ReaderConfirmation(text: Int, enabled: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.night_reader_title)) }, text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = confirm, enabled = enabled, modifier = Modifier.testTag("night-reader-confirm")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
