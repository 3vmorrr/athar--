package org.athar.app.media

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import java.util.Locale

val LocalAtharAudio = staticCompositionLocalOf<AudioViewModel?> { null }

@Composable
fun audioViewModel(graph: AppGraph): AudioViewModel {
    LocalAtharAudio.current?.let { return it }
    val app = LocalContext.current.applicationContext as android.app.Application
    return viewModel(key = "audio:${graph.partition}:${graph.session.userId}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AudioViewModel(graph, app) as T
    })
}

@Composable
private fun playbackPosition(vm: AudioViewModel): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var position by remember(vm) { mutableLongStateOf(vm.elapsed()) }
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { position = vm.elapsed(); delay(500) }
        }
    }
    return position
}

internal fun playbackTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
fun AtharMiniPlayer(vm: AudioViewModel, expand: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.hasMedia) return
    val position = playbackPosition(vm)
    AtharFloatingSurface(modifier.fillMaxWidth().testTag("athar-mini-player")) {
        Column {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = expand) { Icon(AtharIcons.Headphones, stringResource(R.string.expand_player), tint = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = expand, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(state.title.ifBlank { stringResource(R.string.listen) }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.artist.isNotBlank()) Text(state.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.error) Text(stringResource(R.string.audio_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = vm::toggle, enabled = !state.loading && !state.error) {
                    Icon(if (state.playing) AtharIcons.Pause else AtharIcons.PlayArrow, stringResource(if (state.playing) R.string.pause else R.string.play))
                }
                IconButton(onClick = vm::stop) { Icon(AtharIcons.Close, stringResource(R.string.stop)) }
            }
            if (state.buffer || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (!state.radio && state.durationMs > 0) LinearProgressIndicator(
                progress = { (position.toFloat() / state.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AtharExpandedPlayer(vm: AudioViewModel, state: AudioUiState, modifier: Modifier = Modifier) {
    val position = playbackPosition(vm)
    val seekLabel = stringResource(R.string.playback_position)
    var scrubbing by remember(state.mediaKey) { mutableStateOf<Float?>(null) }
    var speedMenu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth().testTag("athar-expanded-player"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(AtharSpacing.Xxl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(AtharIcons.Headphones, null, Modifier.padding(28.dp).size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(state.title.ifBlank { stringResource(R.string.listen) }, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (state.artist.isNotBlank()) Text(state.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.radio && state.durationMs > 0) {
                Column(Modifier.fillMaxWidth()) {
                    Slider(
                        value = scrubbing ?: position.toFloat().coerceIn(0f, state.durationMs.toFloat()),
                        onValueChange = { scrubbing = it },
                        onValueChangeFinished = { scrubbing?.let { vm.seek(it.toLong()) }; scrubbing = null },
                        valueRange = 0f..state.durationMs.toFloat(), enabled = state.seekable && !state.loading,
                        modifier = Modifier.testTag("player-seek").semantics { contentDescription = seekLabel },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(playbackTime((scrubbing?.toLong() ?: position)), style = MaterialTheme.typography.labelMedium)
                        Text(playbackTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (state.radio) AtharStatusPill(stringResource(R.string.radio), emphasized = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                FilledIconButton(onClick = vm::toggle, enabled = state.hasMedia && !state.error && !state.loading, modifier = Modifier.size(64.dp).testTag("player-toggle")) {
                    Icon(if (state.playing) AtharIcons.Pause else AtharIcons.PlayArrow,
                        stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(32.dp))
                }
                OutlinedIconButton(onClick = vm::stop, modifier = Modifier.size(64.dp)) { Icon(AtharIcons.Stop, stringResource(R.string.stop)) }
                if (!state.radio) {
                    OutlinedIconToggleButton(checked = state.repeatOne, onCheckedChange = { vm.repeat() }, enabled = vm.supportsRepeat(), modifier = Modifier.size(64.dp).testTag("player-repeat")) {
                        Icon(AtharIcons.Repeat, stringResource(R.string.repeat_recording))
                    }
                    Box {
                        OutlinedButton(onClick = { speedMenu = true }, enabled = vm.supportsSpeed(), modifier = Modifier.heightIn(min = 64.dp).testTag("player-speed")) {
                            Text(stringResource(R.string.playback_speed_value, state.speed))
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.playback_speed_value, speed)) }, onClick = { vm.speed(speed); speedMenu = false }, modifier = Modifier.testTag("player-speed-$speed"))
                            }
                        }
                    }
                }
            }
            if (state.buffer || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error) MessagePanel(stringResource(R.string.audio_failed), true)
        }
    }
}
