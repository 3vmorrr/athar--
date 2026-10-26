package org.athar.app.nightprayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
fun NightSessionTimerCard(
    graph: AppGraph,
    nightViewModel: NightViewModel,
    identity: NightIdentity?,
    calculationMethod: String?,
) {
    val owner = requireNotNull(graph.session.userId)
    val timer: NightTimerViewModel = viewModel(
        key = "night-timer:$owner",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NightTimerViewModel(graph, owner) as T
        },
    )
    val state by timer.state.collectAsStateWithLifecycle()
    val nightState by nightViewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    var confirmCancel by remember { mutableStateOf(false) }
    var confirmUnreadableDiscard by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, timer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                timer.pauseForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            AtharSectionHeader(stringResource(R.string.night_timer_title))

            when {
                state.loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                state.unreadable -> {
                    MessagePanel(
                        stringResource(R.string.night_timer_unreadable),
                        true,
                    ) {
                        TextButton(
                            onClick = { confirmUnreadableDiscard = true },
                            enabled = !state.busy,
                        ) {
                            Text(stringResource(R.string.night_timer_discard))
                        }
                    }
                }

                state.snapshot == null -> {
                    Text(
                        stringResource(R.string.night_timer_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            val selected = identity ?: return@Button
                            val method = calculationMethod ?: return@Button
                            timer.start(selected, method)
                        },
                        enabled = !state.busy &&
                            identity?.inProgress == true &&
                            calculationMethod != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.night_timer_start))
                    }
                    if (identity?.inProgress != true) {
                        Text(
                            stringResource(R.string.night_timer_outside_night),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    val snapshot = requireNotNull(state.snapshot)
                    Text(
                        formatTimer(state.displayedMillis),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    AtharStatusPill(
                        stringResource(
                            when (snapshot.phase) {
                                "running" -> R.string.night_timer_running
                                "finished" -> R.string.night_timer_finished
                                else -> R.string.night_timer_paused
                            },
                        ),
                        emphasized = snapshot.phase != "running",
                    )

                    snapshot.pauseReason?.let { reason ->
                        Text(
                            stringResource(
                                when (reason) {
                                    "background" -> R.string.night_timer_paused_background
                                    "recovered" -> R.string.night_timer_paused_recovered
                                    "boundary" -> R.string.night_timer_paused_fajr
                                    else -> R.string.night_timer_paused_user
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        when (snapshot.phase) {
                            "running" -> {
                                Button(
                                    onClick = timer::pauseByUser,
                                    enabled = !state.busy,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.pause))
                                }
                                OutlinedButton(
                                    onClick = timer::finish,
                                    enabled = !state.busy,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.night_timer_finish))
                                }
                            }
                            "paused" -> {
                                Button(
                                    onClick = timer::resume,
                                    enabled = !state.busy,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.night_timer_resume))
                                }
                                OutlinedButton(
                                    onClick = timer::finish,
                                    enabled = !state.busy,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.night_timer_finish))
                                }
                            }
                            "finished" -> {
                                Button(
                                    onClick = { recording = true },
                                    enabled = !state.busy && !nightState.busy,
                                    modifier = Modifier.heightIn(min = 52.dp),
                                ) {
                                    Text(stringResource(R.string.night_timer_record))
                                }
                            }
                        }

                        TextButton(
                            onClick = { confirmCancel = true },
                            enabled = !state.busy,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            state.error?.let { code ->
                MessagePanel(
                    stringResource(
                        when (code) {
                            "outside_night" -> R.string.night_timer_outside_night
                            "too_short" -> R.string.night_timer_too_short
                            else -> R.string.night_timer_storage_error
                        },
                    ),
                    true,
                )
            }
        }
    }

    if (recording && state.snapshot?.phase == "finished") {
        NightEntryDialog(
            graph = graph,
            editing = null,
            busy = nightState.busy || state.busy,
            conflicted = false,
            dismiss = { recording = false },
            save = { payload ->
                nightViewModel.save(payload, null) {
                    timer.markRecorded()
                    recording = false
                }
            },
            measuredTimer = state.snapshot,
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.night_timer_cancel_title)) },
            text = { Text(stringResource(R.string.night_timer_cancel_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        timer.clear()
                        confirmCancel = false
                    },
                ) {
                    Text(
                        stringResource(R.string.night_timer_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }

    if (confirmUnreadableDiscard) {
        AlertDialog(
            onDismissRequest = { confirmUnreadableDiscard = false },
            title = { Text(stringResource(R.string.night_timer_discard)) },
            text = { Text(stringResource(R.string.night_timer_unreadable_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        timer.clearUnreadable()
                        confirmUnreadableDiscard = false
                    },
                ) {
                    Text(
                        stringResource(R.string.night_timer_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnreadableDiscard = false }) {
                    Text(stringResource(R.string.back))
                }
            },
        )
    }
}

private fun formatTimer(milliseconds: Long): String {
    val total = (milliseconds / 1_000L).coerceAtLeast(0L)
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
