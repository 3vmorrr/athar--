package org.athar.app.review

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
fun ReviewRecitationScreen(
    graph: AppGraph,
    surah: Int,
    ayah: Int,
    quranFontSize: Int,
    onBack: () -> Unit,
) {
    val owner = requireNotNull(graph.session.userId)
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: ReviewRecitationViewModel = viewModel(
        key = "review-recitation:" + owner + ":" + surah + ":" + ayah,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
            ): T = ReviewRecitationViewModel(
                application = application,
                graph = graph,
                owner = owner,
                surah = surah,
                ayah = ayah,
            ) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.start() else vm.permissionDenied()
    }

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onBackgrounded()
                Lifecycle.Event.ON_RESUME -> vm.recheckPermission()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onBackgrounded()
        }
    }
    BackHandler {
        vm.stop()
        onBack()
    }

    val busy = state.microphoneStatus in setOf(
        ReviewMicrophoneStatus.STARTING,
        ReviewMicrophoneStatus.CAPTURING,
        ReviewMicrophoneStatus.FINALIZING,
    )

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            TextButton(
                onClick = {
                    vm.stop()
                    onBack()
                },
                enabled = state.microphoneStatus !=
                    ReviewMicrophoneStatus.FINALIZING,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.back))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
        ) {
            item(key = "review-hero", contentType = "hero") {
                AtharImmersiveHero(
                    presentation = AtharHeroPresentation(
                        artwork = AtharHeroArtwork.Quran,
                        eyebrow = stringResource(R.string.review_recitation_title),
                        title = stringResource(
                            R.string.review_recitation_reference,
                            surah,
                            ayah,
                        ),
                        supportingText = stringResource(
                            R.string.review_recitation_expected_note,
                        ),
                        statusText = when {
                            state.microphoneStatus ==
                                ReviewMicrophoneStatus.CAPTURING ->
                                stringResource(R.string.review_recitation_listening)
                            state.microphoneStatus ==
                                ReviewMicrophoneStatus.FINALIZING ->
                                stringResource(R.string.review_microphone_finalizing)
                            else -> null
                        },
                        statusTone = when (state.microphoneStatus) {
                            ReviewMicrophoneStatus.ERROR,
                            ReviewMicrophoneStatus.OVERLOADED,
                            ReviewMicrophoneStatus.REVOKED ->
                                AtharHeroStatusTone.Warning
                            else -> AtharHeroStatusTone.Neutral
                        },
                    ),
                )
            }

            item(key = "review-guidance", contentType = "guidance") {
                Column(
                    Modifier.padding(horizontal = AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    MessagePanel(
                        stringResource(R.string.review_recitation_privacy),
                        false,
                    )
                    when {
                        state.loading -> LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                        )
                        !state.modelReady -> MessagePanel(
                            stringResource(R.string.review_recitation_model_required),
                            true,
                        )
                        state.loadError -> MessagePanel(
                            stringResource(R.string.review_recitation_load_failed),
                            true,
                        ) {
                            TextButton(onClick = vm::retryLoad) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }

            state.displayAyahs.forEach { displayAyah ->
                val verdict = state.verdict?.ayahs?.firstOrNull {
                    it.surah == displayAyah.position.surah &&
                        it.ayah == displayAyah.position.ayah
                }
                item(
                    key = "review-ayah-" + displayAyah.position.globalIndex,
                    contentType = "review-ayah",
                ) {
                    ReviewAyahCard(
                        reference = displayAyah.position.surah.toString() +
                            ":" + displayAyah.position.ayah,
                        quranText = displayAyah.text,
                        words = verdict?.words.orEmpty(),
                        quranFontSize = quranFontSize,
                    )
                }
            }

            if (state.modelReady && state.passage != null) {
                item(key = "review-controls", contentType = "controls") {
                    Column(
                        Modifier.padding(horizontal = AtharSpacing.Lg),
                        verticalArrangement =
                            Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        when (state.microphoneStatus) {
                            ReviewMicrophoneStatus.STARTING,
                            ReviewMicrophoneStatus.FINALIZING -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            ReviewMicrophoneStatus.CAPTURING -> {
                                LinearProgressIndicator(
                                    progress = { state.peak },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    stringResource(
                                        R.string.review_microphone_duration,
                                        state.capturedMillis / 1000L,
                                    ),
                                    style =
                                        MaterialTheme.typography.bodySmall,
                                    color =
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                )
                            }
                            else -> Unit
                        }

                        if (
                            state.modelFrames > 0 ||
                            state.rawTokens > 0
                        ) {
                            Text(
                                stringResource(
                                    R.string.review_local_inference_counts,
                                    state.modelFrames,
                                    state.rawTokens,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        ReviewRecitationStatus(state)

                        if (
                            state.microphoneStatus ==
                            ReviewMicrophoneStatus.CAPTURING
                        ) {
                            OutlinedButton(
                                onClick = vm::stop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp),
                            ) {
                                Text(stringResource(R.string.review_microphone_stop))
                            }
                        } else if (!busy) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat
                                        .checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        vm.start()
                                    } else {
                                        permission.launch(
                                            Manifest.permission.RECORD_AUDIO,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.review_recitation_start,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewAyahCard(
    reference: String,
    quranText: String,
    words: List<ReviewWordVerdict>,
    quranFontSize: Int,
) {
    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = AtharSpacing.Lg)
            .fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Text(
                reference,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                quranText,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = QuranFont,
                fontSize = quranFontSize.sp,
                lineHeight = (quranFontSize * 1.9f).sp,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = TextDirection.Rtl,
                ),
            )
            FlowRow(
                horizontalArrangement =
                    Arrangement.spacedBy(AtharSpacing.Sm),
                verticalArrangement =
                    Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                words.forEach { word ->
                    ReviewWordSurface(word)
                }
            }
        }
    }
}

@Composable
private fun ReviewWordSurface(word: ReviewWordVerdict) {
    val container = when (word.state) {
        ReviewWordState.CONFIRMED ->
            MaterialTheme.colorScheme.primaryContainer
        ReviewWordState.UNCERTAIN ->
            MaterialTheme.colorScheme.tertiaryContainer
        ReviewWordState.WRONG,
        ReviewWordState.SKIPPED ->
            MaterialTheme.colorScheme.errorContainer
        ReviewWordState.PENDING ->
            MaterialTheme.colorScheme.surfaceVariant
        ReviewWordState.EMPTY ->
            MaterialTheme.colorScheme.surface
    }
    val content = when (word.state) {
        ReviewWordState.CONFIRMED ->
            MaterialTheme.colorScheme.onPrimaryContainer
        ReviewWordState.UNCERTAIN ->
            MaterialTheme.colorScheme.onTertiaryContainer
        ReviewWordState.WRONG,
        ReviewWordState.SKIPPED ->
            MaterialTheme.colorScheme.onErrorContainer
        ReviewWordState.PENDING ->
            MaterialTheme.colorScheme.onSurfaceVariant
        ReviewWordState.EMPTY ->
            MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (
            word.state == ReviewWordState.EMPTY
        ) 0.dp else 1.dp,
    ) {
        Column(
            Modifier.padding(
                horizontal = AtharSpacing.Md,
                vertical = AtharSpacing.Sm,
            ),
        ) {
            Text(
                word.glyph,
                fontFamily = QuranFont,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(reviewWordStateLabel(word.state)),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ReviewRecitationStatus(state: ReviewRecitationUiState) {
    val status = state.microphoneStatus
    val statusMessage = when (status) {
        ReviewMicrophoneStatus.DENIED ->
            R.string.review_microphone_denied
        ReviewMicrophoneStatus.REVOKED ->
            R.string.review_microphone_revoked
        ReviewMicrophoneStatus.INTERRUPTED ->
            R.string.review_microphone_interrupted
        ReviewMicrophoneStatus.ERROR ->
            R.string.review_microphone_failed
        ReviewMicrophoneStatus.OVERLOADED ->
            R.string.review_microphone_overloaded
        ReviewMicrophoneStatus.BACKGROUNDED ->
            R.string.review_microphone_background_stopped
        else -> null
    }
    statusMessage?.let {
        MessagePanel(
            stringResource(it),
            status in setOf(
                ReviewMicrophoneStatus.REVOKED,
                ReviewMicrophoneStatus.ERROR,
                ReviewMicrophoneStatus.OVERLOADED,
            ),
        )
    }

    val verdict = state.verdict
    if (verdict?.final == true && verdict.heardCharacters > 0) {
        val message = when {
            verdict.hasProblem ->
                R.string.review_recitation_possible_issue
            verdict.hasUncertainty ->
                R.string.review_recitation_uncertain_summary
            else ->
                R.string.review_recitation_clear_summary
        }
        MessagePanel(
            stringResource(message),
            verdict.hasProblem,
        )
    }
}

private fun reviewWordStateLabel(state: ReviewWordState): Int =
    when (state) {
        ReviewWordState.EMPTY -> R.string.review_word_empty
        ReviewWordState.PENDING -> R.string.review_word_pending
        ReviewWordState.CONFIRMED -> R.string.review_word_confirmed
        ReviewWordState.UNCERTAIN -> R.string.review_word_uncertain
        ReviewWordState.WRONG -> R.string.review_word_possible_mismatch
        ReviewWordState.SKIPPED -> R.string.review_word_possible_skipped
    }
