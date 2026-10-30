package org.athar.app.nightprayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.collect
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.SessionLease
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.*
import java.time.Instant

internal sealed interface NightTimerEvent {
    data object Retry : NightTimerEvent
    data class Start(val version: Long, val identity: NightIdentity, val method: String) : NightTimerEvent
    data class Resume(val version: Long) : NightTimerEvent
    data class Pause(val version: Long) : NightTimerEvent
    data class Finish(val version: Long) : NightTimerEvent
    data class Clear(val version: Long) : NightTimerEvent
    data class Record(val version: Long) : NightTimerEvent
}

/** Owned by Tonight itself, outside its lazy item: scrolling the card away is not app background. */
@Composable internal fun nightTimerViewModel(graph: AppGraph, lease: SessionLease): NightTimerViewModel {
    val vm: NightTimerViewModel = viewModel(key = "night-timer:${graph.partition}:${lease.owner}:${lease.generation}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = NightTimerViewModel(lease.owner, graph.partition, object : NightTimerGateway {
            override fun requireActive() = graph.session.requireLease(lease)
            override fun changes() = graph.nightTimer.observe(lease)
            override suspend fun read() = graph.nightTimer.read(lease)
            override suspend fun restore() = graph.nightTimer.restore(lease)
            override suspend fun start(expected: Long, identity: NightIdentity, method: String) = graph.nightTimer.start(lease, expected, identity, method)
            override suspend fun resume(expected: Long, selected: NightMeasuredTimer) = graph.nightTimer.resume(lease, expected, selected)
            override suspend fun checkpoint(expected: Long, selected: NightMeasuredTimer, sampled: NightMeasuredTimer) = graph.nightTimer.checkpoint(lease, expected, selected, sampled)
            override suspend fun finish(expected: Long, selected: NightMeasuredTimer) = graph.nightTimer.finish(lease, expected, selected)
            override suspend fun clear(expected: Long) = graph.nightTimer.clear(lease, expected)
            override suspend fun record(expected: Long, selected: NightMeasuredTimer, entry: JsonObject) = graph.nightTimer.record(lease, expected, selected, entry)
        }, android.os.SystemClock::elapsedRealtime, Instant::now) as T
    })
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lease) { graph.session.state.collect { if (runCatching { graph.session.requireLease(lease) }.isFailure) vm.clearPrivate() } }
    DisposableEffect(vm, lifecycle) {
        vm.foreground(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.foreground(true)
            if (event == Lifecycle.Event.ON_STOP) vm.foreground(false)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); vm.foreground(false) }
    }
    return vm
}

internal fun NightTimerViewModel.handle(event: NightTimerEvent) = when (event) {
    NightTimerEvent.Retry -> retry()
    is NightTimerEvent.Start -> start(event.version, event.identity, event.method)
    is NightTimerEvent.Resume -> resume(event.version)
    is NightTimerEvent.Pause -> pause(event.version)
    is NightTimerEvent.Finish -> finish(event.version)
    is NightTimerEvent.Clear -> clear(event.version)
    is NightTimerEvent.Record -> openRecord(event.version)
}

@Composable internal fun NightTimerCardContent(state: NightTimerUiState, identity: NightIdentity?, method: String?, resumeAllowed: Boolean,
    modifier: Modifier = Modifier, event: (NightTimerEvent) -> Unit) {
    var clearVersion by remember { mutableStateOf<Long?>(null) }
    val frame = state.frame
    val timer = frame?.timer
    val enabled = !state.busy && !state.needsRecovery && state.recording == null
    Surface(modifier.fillMaxWidth().testTag("night-timer"), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(AtharSpacing.Xl), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            AtharSectionHeader(stringResource(R.string.night_timer_title))
            if (state.busy || state.read == ProtectedRead.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.read == ProtectedRead.Unreadable -> MessagePanel(stringResource(R.string.local_data_unreadable), true) {
                    TextButton(onClick = { event(NightTimerEvent.Retry) }, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
                }
                frame != null && timer == null -> {
                    Text(stringResource(R.string.night_timer_intro))
                    Button(onClick = { if (identity != null && method != null) event(NightTimerEvent.Start(frame.version, identity, method)) }, enabled = enabled && identity?.inProgress == true && method != null,
                        modifier = Modifier.testTag("night-timer-start")) { Text(stringResource(R.string.night_timer_start)) }
                    if (identity?.inProgress != true) Text(stringResource(R.string.night_timer_outside_night), style = MaterialTheme.typography.bodySmall)
                }
                frame != null && timer != null -> {
                    Text(timerDuration(state.displayedMillis), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("night-timer-duration"))
                    Text("${timer.date} · ${timer.zone}", style = MaterialTheme.typography.bodySmall)
                    AtharStatusPill(stringResource(if (state.needsRecovery) R.string.night_timer_paused else when (timer.phase) { "running" -> R.string.night_timer_running; "finished" -> R.string.night_timer_finished; else -> R.string.night_timer_paused }))
                    timer.reason?.let { Text(stringResource(when (it) {
                        "background" -> R.string.night_timer_paused_background
                        "recovered" -> R.string.night_timer_paused_recovered
                        "boundary" -> R.string.night_timer_paused_fajr
                        "clock" -> R.string.night_timer_paused_clock
                        else -> R.string.night_timer_paused_user
                    }), style = MaterialTheme.typography.bodySmall) }
                    if (state.needsRecovery) MessagePanel(stringResource(R.string.night_timer_review_checkpoint), true) {
                        TextButton(onClick = { event(NightTimerEvent.Retry) }, enabled = !state.busy) { Text(stringResource(R.string.retry)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                        if (timer.phase == "running") Button(onClick = { event(NightTimerEvent.Pause(frame.version)) }, enabled = enabled, modifier = Modifier.testTag("night-timer-pause")) { Text(stringResource(R.string.night_timer_pause)) }
                        if (timer.phase == "paused") Button(onClick = { event(NightTimerEvent.Resume(frame.version)) }, enabled = enabled && resumeAllowed, modifier = Modifier.testTag("night-timer-resume")) { Text(stringResource(R.string.night_timer_resume)) }
                        if (timer.phase != "finished") OutlinedButton(onClick = { event(NightTimerEvent.Finish(frame.version)) }, enabled = enabled, modifier = Modifier.testTag("night-timer-finish")) { Text(stringResource(R.string.night_timer_finish)) }
                        if (timer.phase == "finished") Button(onClick = { event(NightTimerEvent.Record(frame.version)) }, enabled = enabled, modifier = Modifier.testTag("night-timer-record")) { Text(stringResource(R.string.night_timer_record)) }
                        TextButton(onClick = { clearVersion = frame.version }, enabled = enabled, modifier = Modifier.testTag("night-timer-clear")) { Text(stringResource(R.string.night_timer_discard)) }
                    }
                }
            }
            state.feedback?.let { Text(stringResource(when (it) {
                NightTimerFeedback.CHANGED -> R.string.night_timer_changed
                NightTimerFeedback.UNCONFIRMED -> R.string.night_timer_unconfirmed
                NightTimerFeedback.INVALID -> R.string.invalid_input
                NightTimerFeedback.CLOCK -> R.string.night_timer_check_clock
                NightTimerFeedback.OUTSIDE -> R.string.night_timer_outside_night
                NightTimerFeedback.TOO_SHORT -> R.string.night_timer_too_short
                NightTimerFeedback.RECORDED -> R.string.night_plan_recorded_locally
                NightTimerFeedback.CLEARED -> R.string.night_timer_cleared
            })) }
        }
    }
    clearVersion?.let { version -> AlertDialog(onDismissRequest = { clearVersion = null }, title = { Text(stringResource(R.string.night_timer_cancel_title)) },
        text = { Text(stringResource(R.string.night_timer_cancel_body)) }, confirmButton = { TextButton(onClick = { clearVersion = null; event(NightTimerEvent.Clear(version)) }, enabled = enabled, modifier = Modifier.testTag("night-timer-clear-confirm")) { Text(stringResource(R.string.night_timer_discard)) } },
        dismissButton = { TextButton(onClick = { clearVersion = null }) { Text(stringResource(R.string.back)) } }) }
}

@Composable internal fun NightTimerRecordingDialog(graph: AppGraph, state: NightTimerUiState, vm: NightTimerViewModel) {
    val selected = state.recording?.timer ?: return
    if (state.frozenEntry == null) NightMeasuredEntryDialog(graph, selected, state.busy, vm::closeRecord) { body ->
        val entry = JsonObject()
        listOf("entry_type", "start_index", "end_index", "verse_count", "note").forEach { key -> body[key]?.let { entry.add(key, it.deepCopy()) } }
        vm.record(entry)
    } else AlertDialog(onDismissRequest = vm::closeRecord, title = { Text(stringResource(R.string.night_timer_record)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
            Text(stringResource(R.string.night_timer_frozen_record)); Text("${selected.date} · ${selected.zone}"); Text(timerDuration(selected.millis))
            Text(state.frozenEntry["verse_count"].asString)
            state.feedback?.let { Text(stringResource(when (it) {
                NightTimerFeedback.CHANGED -> R.string.night_timer_changed
                NightTimerFeedback.CLOCK -> R.string.night_timer_check_clock
                else -> R.string.night_timer_unconfirmed
            })) }
        }
    }, confirmButton = { TextButton(onClick = { vm.record() }, enabled = !state.busy, modifier = Modifier.testTag("night-timer-record-retry")) { Text(stringResource(R.string.retry)) } },
        dismissButton = { TextButton(onClick = vm::closeRecord, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } })
}

private fun timerDuration(millis: Long): String = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", millis / 3600000, millis / 60000 % 60, millis / 1000 % 60)
