package org.athar.app.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph
import org.athar.app.quran.AyahText

data class ReviewRecitationUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val modelReady: Boolean = false,
    val passage: ReviewExpectedPassage? = null,
    val displayAyahs: List<AyahText> = emptyList(),
    val verdict: ReviewPassageVerdict? = null,
    val microphoneStatus: ReviewMicrophoneStatus =
        ReviewMicrophoneStatus.IDLE,
    val capturedSamples: Long = 0,
    val peak: Float = 0f,
    val modelFrames: Long = 0,
    val rawTokens: Long = 0,
) {
    val capturedMillis: Long
        get() = capturedSamples * 1000L /
            ReviewAudioCaptureContract.SAMPLE_RATE_HZ
}

class ReviewRecitationViewModel(
    application: Application,
    private val graph: AppGraph,
    private val owner: Long,
    private val surah: Int,
    private val ayah: Int,
) : AndroidViewModel(application) {
    private val runtime = ReviewNativeRuntime(graph.reviewModels)
    private val capture = ReviewAudioCapture(application)
    private val aligner = ReviewPassageAligner()
    private val mutable = MutableStateFlow(ReviewRecitationUiState())
    val state = mutable.asStateFlow()
    private val requestedStop = AtomicReference<String?>(null)

    @Volatile private var channel: Channel<FloatArray>? = null
    @Volatile private var inferenceJob: Job? = null
    @Volatile private var startJob: Job? = null
    @Volatile private var recognizer: ReviewStreamingRecognizer? = null
    @Volatile private var terminalStatus = ReviewMicrophoneStatus.IDLE

    init {
        loadExpectedPassage()
    }

    fun retryLoad() {
        if (microphoneBusy()) return
        loadExpectedPassage()
    }

    fun permissionDenied() {
        if (microphoneBusy()) return
        mutable.update {
            it.copy(microphoneStatus = ReviewMicrophoneStatus.DENIED)
        }
    }

    fun start() {
        val passage = mutable.value.passage ?: return
        if (
            !mutable.value.modelReady ||
            microphoneBusy() ||
            inferenceJob?.isActive == true
        ) return
        if (!capture.hasPermission()) {
            permissionDenied()
            return
        }

        requestedStop.set(null)
        terminalStatus = ReviewMicrophoneStatus.IDLE
        mutable.update {
            it.copy(
                microphoneStatus = ReviewMicrophoneStatus.STARTING,
                verdict = aligner.align(
                    passage,
                    emptyList(),
                    final = false,
                ),
                capturedSamples = 0,
                peak = 0f,
                modelFrames = 0,
                rawTokens = 0,
            )
        }

        startJob = viewModelScope.launch {
            var localRecognizer: ReviewStreamingRecognizer? = null
            try {
                graph.session.requireVerifiedOwner(owner)
                localRecognizer = runtime.openRecognizer()
                graph.session.requireVerifiedOwner(owner)
                if (!capture.hasPermission()) {
                    localRecognizer.close()
                    localRecognizer = null
                    mutable.update {
                        it.copy(
                            microphoneStatus =
                                ReviewMicrophoneStatus.REVOKED,
                        )
                    }
                    return@launch
                }

                val localChannel = Channel<FloatArray>(
                    capacity =
                        ReviewInferenceQueueContract.MAX_QUEUED_CHUNKS,
                )
                channel = localChannel
                recognizer = localRecognizer

                val worker = viewModelScope.launch(Dispatchers.Default) {
                    val heard = ArrayList<ReviewHeardChar>()
                    try {
                        for (samples in localChannel) {
                            val chunk = requireNotNull(localRecognizer)
                                .accept(samples)
                            appendAndAlign(
                                passage = passage,
                                heard = heard,
                                chunk = chunk,
                                final = false,
                            )
                        }
                        val tail = requireNotNull(localRecognizer).finish()
                        appendAndAlign(
                            passage = passage,
                            heard = heard,
                            chunk = tail,
                            final = true,
                        )
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        terminalStatus = ReviewMicrophoneStatus.ERROR
                        requestedStop.compareAndSet(null, "inference")
                        capture.stop()
                    } finally {
                        runCatching { localRecognizer?.close() }
                        if (recognizer === localRecognizer) {
                            recognizer = null
                        }
                        if (channel === localChannel) {
                            channel = null
                        }
                        mutable.update { current ->
                            if (
                                current.microphoneStatus in setOf(
                                    ReviewMicrophoneStatus.STARTING,
                                    ReviewMicrophoneStatus.CAPTURING,
                                    ReviewMicrophoneStatus.FINALIZING,
                                )
                            ) {
                                current.copy(
                                    microphoneStatus = terminalStatus,
                                    peak = 0f,
                                )
                            } else {
                                current
                            }
                        }
                    }
                }
                inferenceJob = worker

                mutable.update {
                    it.copy(
                        microphoneStatus =
                            ReviewMicrophoneStatus.CAPTURING,
                    )
                }
                capture.start(
                    scope = viewModelScope,
                    onChunk = { samples ->
                        if (!localChannel.trySend(samples).isSuccess) {
                            terminalStatus =
                                ReviewMicrophoneStatus.OVERLOADED
                            requestedStop.set("overload")
                            capture.stop()
                        } else {
                            mutable.update { current ->
                                if (
                                    current.microphoneStatus !=
                                    ReviewMicrophoneStatus.CAPTURING
                                ) {
                                    current
                                } else {
                                    current.copy(
                                        capturedSamples = (
                                            current.capturedSamples +
                                                samples.size
                                            ).coerceAtMost(
                                                24L * 60L *
                                                    ReviewAudioCaptureContract
                                                        .SAMPLE_RATE_HZ,
                                            ),
                                        peak =
                                            ReviewAudioCaptureContract
                                                .peak(samples),
                                    )
                                }
                            }
                        }
                    },
                    onStopped = { reason ->
                        val override = requestedStop.getAndSet(null)
                        terminalStatus = terminalStatus(
                            override,
                            reason,
                        )
                        localChannel.close()
                        mutable.update { current ->
                            current.copy(
                                microphoneStatus =
                                    if (worker.isActive) {
                                        ReviewMicrophoneStatus.FINALIZING
                                    } else {
                                        terminalStatus
                                    },
                                peak = 0f,
                            )
                        }
                    },
                )
            } catch (failure: CancellationException) {
                channel?.close()
                inferenceJob?.cancel()
                runCatching { localRecognizer?.close() }
                throw failure
            } catch (_: SecurityException) {
                channel?.close()
                inferenceJob?.cancel()
                runCatching { localRecognizer?.close() }
                mutable.update {
                    it.copy(
                        microphoneStatus =
                            ReviewMicrophoneStatus.REVOKED,
                        peak = 0f,
                    )
                }
            } catch (_: Exception) {
                channel?.close()
                inferenceJob?.cancel()
                runCatching { localRecognizer?.close() }
                mutable.update {
                    it.copy(
                        microphoneStatus = ReviewMicrophoneStatus.ERROR,
                        peak = 0f,
                    )
                }
            }
        }
    }

    fun stop() {
        when (mutable.value.microphoneStatus) {
            ReviewMicrophoneStatus.STARTING -> {
                terminalStatus = ReviewMicrophoneStatus.IDLE
                startJob?.cancel()
                mutable.update {
                    it.copy(
                        microphoneStatus = ReviewMicrophoneStatus.IDLE,
                        peak = 0f,
                    )
                }
            }
            ReviewMicrophoneStatus.CAPTURING -> {
                terminalStatus = ReviewMicrophoneStatus.IDLE
                requestedStop.set("user")
                capture.stop()
            }
            else -> Unit
        }
    }

    fun onBackgrounded() {
        when (mutable.value.microphoneStatus) {
            ReviewMicrophoneStatus.STARTING -> {
                terminalStatus = ReviewMicrophoneStatus.BACKGROUNDED
                startJob?.cancel()
                mutable.update {
                    it.copy(
                        microphoneStatus =
                            ReviewMicrophoneStatus.BACKGROUNDED,
                        peak = 0f,
                    )
                }
            }
            ReviewMicrophoneStatus.CAPTURING -> {
                terminalStatus = ReviewMicrophoneStatus.BACKGROUNDED
                requestedStop.set("background")
                capture.stop()
            }
            else -> Unit
        }
    }

    fun recheckPermission() {
        if (
            mutable.value.microphoneStatus ==
                ReviewMicrophoneStatus.CAPTURING &&
            !capture.hasPermission()
        ) {
            terminalStatus = ReviewMicrophoneStatus.REVOKED
            requestedStop.set("revoked")
            capture.stop()
        }
    }

    private fun loadExpectedPassage() {
        viewModelScope.launch {
            mutable.update {
                it.copy(
                    loading = true,
                    loadError = false,
                    modelReady = graph.reviewModels.installed(),
                )
            }
            try {
                graph.session.requireVerifiedOwner(owner)
                if (!graph.reviewModels.installed()) {
                    mutable.update {
                        it.copy(
                            loading = false,
                            modelReady = false,
                            passage = null,
                            displayAyahs = emptyList(),
                            verdict = null,
                        )
                    }
                    return@launch
                }

                val quran = graph.quran.data()
                val expected = withContext(Dispatchers.IO) {
                    ReviewCorpusPassageReader.read(
                        graph.reviewModels
                            .verifiedArtifactFile("corpus"),
                        surah = surah,
                        ayahStart = ayah,
                        ayahEnd = ayah,
                    )
                }
                val display = quran.range(
                    surah,
                    ayah,
                    surah,
                    ayah,
                )
                graph.session.requireVerifiedOwner(owner)
                mutable.update {
                    it.copy(
                        loading = false,
                        loadError = false,
                        modelReady = true,
                        passage = expected,
                        displayAyahs = display,
                        verdict = aligner.align(
                            expected,
                            emptyList(),
                            final = false,
                        ),
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.update {
                    it.copy(
                        loading = false,
                        loadError = true,
                        passage = null,
                        verdict = null,
                    )
                }
            }
        }
    }

    private fun appendAndAlign(
        passage: ReviewExpectedPassage,
        heard: MutableList<ReviewHeardChar>,
        chunk: ReviewRecognitionChunk,
        final: Boolean,
    ) {
        val expanded = ReviewCtcExpansion.expand(chunk.tokens)
        if (
            heard.size + expanded.size >
            ReviewPassageAligner.MAX_HEARD_CHARS
        ) {
            terminalStatus = ReviewMicrophoneStatus.OVERLOADED
            requestedStop.set("overload")
            capture.stop()
            return
        }
        heard += expanded
        val verdict = aligner.align(
            passage = passage,
            heard = heard,
            final = final,
        )
        mutable.update { current ->
            current.copy(
                verdict = verdict,
                modelFrames = (
                    current.modelFrames +
                        chunk.modelFrames
                    ).coerceAtMost(MAX_REPORTED_FRAMES),
                rawTokens = (
                    current.rawTokens +
                        chunk.tokens.size
                    ).coerceAtMost(MAX_REPORTED_TOKENS),
            )
        }
    }

    private fun microphoneBusy(): Boolean =
        mutable.value.microphoneStatus in setOf(
            ReviewMicrophoneStatus.STARTING,
            ReviewMicrophoneStatus.CAPTURING,
            ReviewMicrophoneStatus.FINALIZING,
        )

    private fun terminalStatus(
        override: String?,
        reason: ReviewAudioStopReason,
    ): ReviewMicrophoneStatus = when (override) {
        "background" -> ReviewMicrophoneStatus.BACKGROUNDED
        "user" -> ReviewMicrophoneStatus.IDLE
        "revoked" -> ReviewMicrophoneStatus.REVOKED
        "overload" -> ReviewMicrophoneStatus.OVERLOADED
        "inference" -> ReviewMicrophoneStatus.ERROR
        else -> when (reason) {
            ReviewAudioStopReason.User ->
                ReviewMicrophoneStatus.IDLE
            ReviewAudioStopReason.PermissionRevoked ->
                ReviewMicrophoneStatus.REVOKED
            ReviewAudioStopReason.Interrupted ->
                ReviewMicrophoneStatus.INTERRUPTED
            ReviewAudioStopReason.CaptureFailure ->
                ReviewMicrophoneStatus.ERROR
        }
    }

    override fun onCleared() {
        terminalStatus = ReviewMicrophoneStatus.IDLE
        requestedStop.set("user")
        startJob?.cancel()
        capture.stop()
        channel?.close()
        inferenceJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_REPORTED_FRAMES = 1_000_000L
        private const val MAX_REPORTED_TOKENS = 1_000_000L
    }
}
