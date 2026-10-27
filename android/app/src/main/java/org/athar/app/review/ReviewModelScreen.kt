package org.athar.app.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
fun ReviewModelScreen(graph: AppGraph) {
    val owner = requireNotNull(graph.session.userId)
    val manager = graph.reviewModels
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val runtimeVm: ReviewRuntimeViewModel = viewModel(
        key = "review-runtime:" + owner,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
            ): T = ReviewRuntimeViewModel(
                application,
                graph,
                owner,
            ) as T
        },
    )
    val runtimeState by runtimeVm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runtimeVm.startMicrophone()
        else runtimeVm.permissionDenied()
    }

    DisposableEffect(lifecycleOwner, runtimeVm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> runtimeVm.onBackgrounded()
                Lifecycle.Event.ON_RESUME -> runtimeVm.recheckPermission()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runtimeVm.onBackgrounded()
        }
    }
    val work by remember(owner) {
        manager.observe(owner)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val latest = work.lastOrNull()
    var localRevision by remember { mutableIntStateOf(0) }
    var acknowledged by rememberSaveable(owner) { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val accepted = remember(owner, localRevision) {
        manager.accepted(owner)
    }
    val installed = remember(latest?.state, localRevision) {
        manager.installed()
    }
    val activeVersion = remember(latest?.state, localRevision) {
        manager.activeVersion()
    }
    val previousVersion = remember(latest?.state, localRevision) {
        manager.previousVersion()
    }
    val hostReady = remember(manager.apiBaseUrl) { manager.hostReady() }
    val available = remember(latest?.state, localRevision) {
        manager.availableBytes()
    }
    val required = manager.requiredFreeBytes()
    val hasSpace = ReviewModelStoragePolicy.hasEnoughSpace(
        available,
        manager.manifest,
    )
    val running = latest?.state == WorkInfo.State.RUNNING ||
        latest?.state == WorkInfo.State.ENQUEUED ||
        latest?.state == WorkInfo.State.BLOCKED
    val downloaded = latest?.progress
        ?.getLong(ReviewModelDownloadWorker.DOWNLOADED, 0L)
        ?: 0L
    val total = latest?.progress
        ?.getLong(
            ReviewModelDownloadWorker.TOTAL,
            manager.manifest.totalBytes,
        )
        ?: manager.manifest.totalBytes
    val progress = if (total > 0L) {
        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val workReason = latest?.outputData
        ?.getString(ReviewModelDownloadWorker.REASON)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "review-model-hero", contentType = "hero") {
            AtharImmersiveHero(
                presentation = AtharHeroPresentation(
                    artwork = AtharHeroArtwork.Quran,
                    eyebrow = stringResource(R.string.quran),
                    title = stringResource(R.string.review_model_title),
                    supportingText = stringResource(
                        R.string.review_model_size,
                        manager.manifest.totalBytes / 1_000_000L,
                    ),
                    statusText = when {
                        installed -> stringResource(R.string.review_model_downloaded)
                        running -> stringResource(R.string.review_model_downloading)
                        accepted -> stringResource(R.string.review_model_license_accepted)
                        else -> stringResource(R.string.review_model_consent_required)
                    },
                    statusTone = when {
                        latest?.state == WorkInfo.State.FAILED -> AtharHeroStatusTone.Warning
                        !hostReady || !hasSpace -> AtharHeroStatusTone.Warning
                        else -> AtharHeroStatusTone.Neutral
                    },
                    visualState = if (
                        latest?.state == WorkInfo.State.FAILED ||
                        !hostReady ||
                        !hasSpace
                    ) {
                        AtharHeroVisualState.Error
                    } else {
                        AtharHeroVisualState.Ready
                    },
                    transitionKey = "review-model:" + owner,
                ),
            )
        }

        item(key = "review-model-license", contentType = "license") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                AtharSectionHeader(stringResource(R.string.review_model_license))
                MessagePanel(
                    stringResource(R.string.review_model_npl_summary),
                    false,
                )
                TextButton(
                    onClick = { showLicense = !showLicense },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (showLicense) {
                                R.string.review_model_hide_license
                            } else {
                                R.string.review_model_view_license
                            },
                        ),
                    )
                }
                if (showLicense) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        SelectionContainer {
                            Text(
                                manager.licenseText(),
                                modifier = Modifier.padding(AtharSpacing.Lg),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                if (!accepted) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                        )
                        Text(
                            stringResource(R.string.review_model_acknowledge),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = {
                            if (busy || !acknowledged) return@Button
                            busy = true
                            localError = null
                            scope.launch {
                                try {
                                    manager.accept(owner)
                                    localRevision++
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    localError = "consent"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = acknowledged && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.review_model_accept_license))
                    }
                }
            }
        }

        item(key = "review-model-status", contentType = "status") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                AtharSectionHeader(stringResource(R.string.review_model_storage))

                ReviewModelInfoLine(
                    stringResource(R.string.review_model_version),
                    manager.manifest.version,
                )
                ReviewModelInfoLine(
                    stringResource(R.string.review_model_runtime),
                    stringResource(
                        if (manager.manifest.runtimeVerified) {
                            R.string.review_model_runtime_verified
                        } else {
                            R.string.review_model_runtime_pending
                        },
                    ),
                )
                ReviewModelInfoLine(
                    stringResource(R.string.review_model_free_space),
                    stringResource(
                        R.string.review_model_megabytes,
                        available / 1_000_000L,
                    ),
                )
                ReviewModelInfoLine(
                    stringResource(R.string.review_model_required_space),
                    stringResource(
                        R.string.review_model_megabytes,
                        required / 1_000_000L,
                    ),
                )
                activeVersion?.let {
                    ReviewModelInfoLine(
                        stringResource(R.string.review_model_active_version),
                        it,
                    )
                }

                if (!hostReady) {
                    MessagePanel(
                        stringResource(R.string.review_model_https_required),
                        true,
                    )
                }
                if (!hasSpace) {
                    MessagePanel(
                        stringResource(R.string.review_model_low_storage),
                        true,
                    )
                }
                if (latest?.state == WorkInfo.State.FAILED) {
                    MessagePanel(
                        stringResource(
                            R.string.review_model_download_failed,
                            workReason ?: "download_failed",
                        ),
                        true,
                    )
                }
                localError?.let {
                    MessagePanel(
                        stringResource(R.string.review_model_action_failed),
                        true,
                    )
                }

                if (running) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.review_model_download_progress,
                            downloaded / 1_000_000L,
                            total / 1_000_000L,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            manager.cancel(owner)
                            localRevision++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                } else if (!installed) {
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            localError = null
                            scope.launch {
                                try {
                                    manager.enqueue(owner)
                                    localRevision++
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    localError = "download"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = accepted && hostReady && hasSpace && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.review_model_download))
                    }
                } else {
                    AtharStatusPill(
                        stringResource(R.string.review_model_verified_download),
                        emphasized = true,
                    )
                    MessagePanel(
                        stringResource(R.string.review_model_inference_pending),
                        false,
                    )
                    if (previousVersion != null) {
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                busy = true
                                localError = null
                                scope.launch {
                                    try {
                                        if (!manager.rollback(owner)) {
                                            localError = "rollback"
                                        }
                                        localRevision++
                                    } catch (failure: CancellationException) {
                                        throw failure
                                    } catch (_: Exception) {
                                        localError = "rollback"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.review_model_rollback))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (busy) return@OutlinedButton
                            busy = true
                            localError = null
                            scope.launch {
                                try {
                                    manager.remove(owner)
                                    localRevision++
                                } catch (failure: CancellationException) {
                                    throw failure
                                } catch (_: Exception) {
                                    localError = "remove"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.review_model_remove))
                    }
                }
            }
        }


        if (installed) {
            item(key = "review-native-runtime", contentType = "runtime") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharSectionHeader(
                        stringResource(R.string.review_runtime_title),
                    )
                    MessagePanel(
                        stringResource(R.string.review_runtime_probe_explainer),
                        false,
                    )

                    if (runtimeState.probing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    when {
                        runtimeState.runtimeCompatible -> {
                            AtharStatusPill(
                                stringResource(
                                    R.string.review_runtime_compatible,
                                    runtimeState.runtimeVersion.orEmpty(),
                                ),
                                emphasized = true,
                            )
                            Text(
                                stringResource(
                                    R.string.review_runtime_input_memory,
                                    runtimeState.runtimeInputBytes / 1024L,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        runtimeState.runtimeError -> MessagePanel(
                            stringResource(R.string.review_runtime_failed),
                            true,
                        )
                    }

                    val microphoneBusy =
                        runtimeState.microphoneStatus in setOf(
                            ReviewMicrophoneStatus.STARTING,
                            ReviewMicrophoneStatus.CAPTURING,
                            ReviewMicrophoneStatus.FINALIZING,
                        )

                    Button(
                        onClick = runtimeVm::probe,
                        enabled = !runtimeState.probing &&
                            !microphoneBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text(
                            stringResource(
                                if (runtimeState.runtimeCompatible) {
                                    R.string.review_runtime_retest
                                } else {
                                    R.string.review_runtime_test
                                },
                            ),
                        )
                    }

                    HorizontalDivider()

                    Text(
                        stringResource(R.string.review_microphone_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    when (runtimeState.microphoneStatus) {
                        ReviewMicrophoneStatus.STARTING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(
                                    R.string.review_microphone_starting,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = runtimeVm::stopMicrophone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                        ReviewMicrophoneStatus.CAPTURING -> {
                            LinearProgressIndicator(
                                progress = { runtimeState.peak },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(
                                    R.string.review_microphone_duration,
                                    runtimeState.capturedMillis / 1000L,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.review_local_inference_counts,
                                    runtimeState.inferenceModelFrames,
                                    runtimeState.inferenceTokens,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = runtimeVm::stopMicrophone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) {
                                Text(stringResource(R.string.review_microphone_stop))
                            }
                        }
                        ReviewMicrophoneStatus.FINALIZING -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                stringResource(
                                    R.string.review_microphone_finalizing,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.review_local_inference_counts,
                                    runtimeState.inferenceModelFrames,
                                    runtimeState.inferenceTokens,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        else -> {
                            val statusText = when (
                                runtimeState.microphoneStatus
                            ) {
                                ReviewMicrophoneStatus.DENIED ->
                                    R.string.review_microphone_denied
                                ReviewMicrophoneStatus.REVOKED ->
                                    R.string.review_microphone_revoked
                                ReviewMicrophoneStatus.INTERRUPTED ->
                                    R.string.review_microphone_interrupted
                                ReviewMicrophoneStatus.ERROR ->
                                    R.string.review_microphone_failed
                                ReviewMicrophoneStatus.BACKGROUNDED ->
                                    R.string.review_microphone_background_stopped
                                ReviewMicrophoneStatus.OVERLOADED ->
                                    R.string.review_microphone_overloaded
                                ReviewMicrophoneStatus.IDLE,
                                ReviewMicrophoneStatus.STARTING,
                                ReviewMicrophoneStatus.CAPTURING,
                                ReviewMicrophoneStatus.FINALIZING -> null
                            }
                            statusText?.let {
                                MessagePanel(
                                    stringResource(it),
                                    runtimeState.microphoneStatus in setOf(
                                        ReviewMicrophoneStatus.ERROR,
                                        ReviewMicrophoneStatus.REVOKED,
                                        ReviewMicrophoneStatus.OVERLOADED,
                                    ),
                                )
                            }
                            if (
                                runtimeState.inferenceModelFrames > 0 ||
                                runtimeState.inferenceTokens > 0
                            ) {
                                Text(
                                    stringResource(
                                        R.string.review_local_inference_counts,
                                        runtimeState.inferenceModelFrames,
                                        runtimeState.inferenceTokens,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = {
                                    val granted = ContextCompat
                                        .checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        runtimeVm.startMicrophone()
                                    } else {
                                        microphonePermission.launch(
                                            Manifest.permission.RECORD_AUDIO,
                                        )
                                    }
                                },
                                enabled = runtimeState.runtimeCompatible &&
                                    !runtimeState.probing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.review_microphone_test,
                                    ),
                                )
                            }
                        }
                    }

                    MessagePanel(
                        stringResource(
                            R.string.review_alignment_still_disabled,
                        ),
                        false,
                    )
                }
            }
        }

        item(key = "review-model-privacy", contentType = "privacy") {
            Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                MessagePanel(
                    stringResource(R.string.review_model_privacy_note),
                    false,
                )
            }
        }
    }
}

@Composable
private fun ReviewModelInfoLine(
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(AtharSpacing.Md))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
