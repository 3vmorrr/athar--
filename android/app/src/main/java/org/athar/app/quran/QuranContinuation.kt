package org.athar.app.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.ui.*

private sealed interface DailyAyahState {
    data object Loading : DailyAyahState
    data object Failed : DailyAyahState
    data class Ready(val ayah: AyahText, val offline: Boolean) : DailyAyahState
}

@Composable
internal fun QuranContinuation(graph: AppGraph, fontSize: Int, open: (Int, Int) -> Unit) {
    val owner = requireNotNull(graph.session.userId)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var retry by remember { mutableIntStateOf(0) }
    var readingRetry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                date = LocalDate.now()
                delay(60_000)
            }
        }
    }
    val retained = remember(graph, owner, readingRetry) { graph.reading.observe(owner).protectedReads() }
        .collectAsStateWithLifecycle(initialValue = ProtectedRead.Loading).value
    val daily by produceState<DailyAyahState>(DailyAyahState.Loading, graph, owner, date, retry) {
        value = DailyAyahState.Loading
        try {
            graph.session.requireVerifiedOwner(owner)
            val data = graph.quran.data()
            val ready = try {
                val result = graph.content.get("quran/daily", mapOf("date" to date.toString()))
                DailyAyahState.Ready(DailyAyah.verified(data, date, result.data), result.offline)
            } catch (failure: CancellationException) { throw failure }
            catch (_: IOException) { DailyAyahState.Ready(DailyAyah.local(data, date), true) }
            graph.session.requireVerifiedOwner(owner)
            value = ready
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { value = DailyAyahState.Failed }
    }
    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
        when (retained) {
            ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true) {
                TextButton(onClick = { readingRetry++ }) { Text(stringResource(R.string.retry)) }
            }
            is ProtectedRead.Ready -> retained.value?.let { position ->
                OutlinedButton(onClick = { open(position.surah, position.ayah) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.resume_reading) + " · ${position.surah}:${position.ayah}")
                }
            }
            ProtectedRead.Loading -> Unit
        }
        AtharSectionHeader(stringResource(R.string.daily_ayah))
        when (val state = daily) {
            DailyAyahState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            DailyAyahState.Failed -> FailurePanel(null) { retry++ }
            is DailyAyahState.Ready -> Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                    Text(state.ayah.text, fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9f).sp,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                    Text("${state.ayah.position.surah}:${state.ayah.position.ayah}", style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content))
                    Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall)
                    if (state.offline) MessagePanel(stringResource(R.string.daily_ayah_local), false) {
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                    TextButton(onClick = { open(state.ayah.position.surah, state.ayah.position.ayah) }) {
                        Text(stringResource(R.string.open_reader))
                    }
                }
            }
        }
    }
}
