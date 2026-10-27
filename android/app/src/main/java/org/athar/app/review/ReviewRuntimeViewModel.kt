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
import org.athar.app.AppGraph

enum class ReviewMicrophoneStatus {
    IDLE,
    STARTING,
    CAPTURING,
    FINALIZING,
    DENIED,
    REVOKED,
    INTERRUPTED,
    ERROR,
    OVERLOADED,
    BACKGROUNDED,
}

data class ReviewRuntimeUiState(
    val probing: Boolean = false,
    val runtimeCompatible: Boolean = false,
    val runtimeVersion: String? = null,
    val runtimeInputBytes: Long = 0,
    val runtimeError: Boolean = false,
    val microphoneStatus: ReviewMicrophoneStatus = ReviewMicrophoneStatus.IDLE,
    val capturedSamples: Long = 0,
    val peak: Float = 0f,
    val inferenceFeatureFrames: Long = 0,
    val inferenceModelFrames: Long = 0,
    val inferenceTokens: Long = 0,
    val processedLens: Long = 0,
    val bufferedFeatureFrames: Int = 0,
) {
    val capturedMillis: Long
        get() = capturedSamples * 1000L /
            ReviewAudioCaptureContract.SAMPLE_RATE_HZ
}

object ReviewInferenceQueueContract {
    const val MAX_QUEUED_CHUNKS =
        ReviewAudioCaptureContract.MAX_CHUNKS_HELD_BY_CALLER
    const val MAX_QUEUED_SAMPLES =
        ReviewAudioCaptureContract.MAX_CALLER_BUFFERED_SAMPLES
}

class ReviewRuntimeViewModel(
    application: Application,
    private val graph: AppGraph,
    private val owner: Long,
) : AndroidViewModel(application) {
    private val runtime = ReviewNativeRuntime(graph.reviewModels)
    private val capture = ReviewAudioCapture(application)
    private val requestedStop = AtomicReference<String?>(null)
    private val mutable = MutableStateFlow(ReviewRuntimeUiState())
    val state = mutable.asStateFlow()

    @Volatile private var recognitionChannel: Channel<FloatArray>? = null
    @Volatile private var recognitionJob: Job? = null
    @Volatile private var recognizer: ReviewStreamingRecognizer? = null
    @Volatile private var startJob: Job? = null
    @Volatile private var terminalStatus = ReviewMicrophoneStatus.IDLE

    fun probe() {
        if (mutable.value.probing || microphoneBusy()) return
        viewModelScope.launch {
            mutable.update {
                it.copy(
                    probing = true,
                    runtimeError = false,
                    runtimeCompatible = false,
                )
            }
            try {
                graph.session.requireVerifiedOwner(owner)
                val result = runtime.probe()
                graph.session.requireVerifiedOwner(owner)
                mutable.update {
                    it.copy(
                        probing = false,
                        runtimeCompatible = true,
                        runtimeVersion = result.runtimeVersion,
                        runtimeInputBytes = result.boundedInputBytes,
                        runtimeError = false,
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.update {
                    it.copy(
                        probing = false,
                        runtimeCompatible = false,
                        runtimeVersion = null,
                        runtimeInputBytes = 0,
                        runtimeError = true,
                    )
                }
            }
        }
    }

    fun permissionDenied() {
        if (microphoneBusy()) return
        mutable.update {
            it.copy(
                microphoneStatus = ReviewMicrophoneStatus.DENIED,
                capturedSamples = 0,
                peak = 0f,
            )
        }
    }

    fun startMicrophone() {
        if (!mutable.value.runtimeCompatible) return
        if (microphoneBusy() || recognitionJob?.isActive == true) return
        if (!capture.hasPermission()) {
            permissionDenied()
            return
        }

        requestedStop.set(null)
        terminalStatus = ReviewMicrophoneStatus.IDLE
        mutable.update {
            it.copy(
                microphoneStatus = ReviewMicrophoneStatus.STARTING,
                capturedSamples = 0,
                peak = 0f,
                inferenceFeatureFrames = 0,
                inferenceModelFrames = 0,
                inferenceTokens = 0,
                processedLens = 0,
                bufferedFeatureFrames = 0,
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

                val channel = Channel<FloatArray>(
                    capacity =
                        ReviewInferenceQueueContract.MAX_QUEUED_CHUNKS,
                )
                recognizer = localRecognizer
                recognitionChannel = channel

                val worker = viewModelScope.launch(Dispatchers.Default) {
                    try {
                        for (chunk in channel) {
                            applyRecognition(
                                requireNotNull(localRecognizer).accept(chunk),
                            )
                        }
                        applyRecognition(
                            requireNotNull(localRecognizer).finish(),
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
                        if (recognitionChannel === channel) {
                            recognitionChannel = null
                        }
                        mutable.update { current ->
                            if (
                                current.microphoneStatus ==
                                ReviewMicrophoneStatus.FINALIZING ||
                                current.microphoneStatus ==
                                ReviewMicrophoneStatus.STARTING ||
                                current.microphoneStatus ==
                                ReviewMicrophoneStatus.CAPTURING
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
                recognitionJob = worker

                mutable.update {
                    it.copy(
                        microphoneStatus =
                            ReviewMicrophoneStatus.CAPTURING,
                    )
                }
                capture.start(
                    scope = viewModelScope,
                    onChunk = { chunk ->
                        val accepted = channel.trySend(chunk).isSuccess
                        if (!accepted) {
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
                                                chunk.size
                                            ).coerceAtMost(
                                                24L * 60L *
                                                    ReviewAudioCaptureContract
                                                        .SAMPLE_RATE_HZ,
                                            ),
                                        peak =
                                            ReviewAudioCaptureContract
                                                .peak(chunk),
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
                        channel.close()
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
                recognitionChannel?.close()
                recognitionJob?.cancel()
                runCatching { localRecognizer?.close() }
                throw failure
            } catch (_: SecurityException) {
                recognitionChannel?.close()
                recognitionJob?.cancel()
                runCatching { localRecognizer?.close() }
                mutable.update {
                    it.copy(
                        microphoneStatus =
                            ReviewMicrophoneStatus.REVOKED,
                        peak = 0f,
                    )
                }
            } catch (_: Exception) {
                recognitionChannel?.close()
                recognitionJob?.cancel()
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

    fun stopMicrophone() {
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

    private fun applyRecognition(chunk: ReviewRecognitionChunk) {
        mutable.update { current ->
            current.copy(
                inferenceFeatureFrames = (
                    current.inferenceFeatureFrames +
                        chunk.featureFrames
                    ).coerceAtMost(MAX_REPORTED_FRAMES),
                inferenceModelFrames = (
                    current.inferenceModelFrames +
                        chunk.modelFrames
                    ).coerceAtMost(MAX_REPORTED_FRAMES),
                inferenceTokens = (
                    current.inferenceTokens +
                        chunk.tokens.size
                    ).coerceAtMost(MAX_REPORTED_TOKENS),
                processedLens = chunk.processedLens,
                bufferedFeatureFrames = chunk.bufferedFeatureFrames,
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
        recognitionChannel?.close()
        recognitionJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_REPORTED_FRAMES = 1_000_000L
        private const val MAX_REPORTED_TOKENS = 1_000_000L
    }
}
