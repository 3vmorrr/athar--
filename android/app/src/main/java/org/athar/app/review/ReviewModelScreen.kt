package org.athar.app.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
