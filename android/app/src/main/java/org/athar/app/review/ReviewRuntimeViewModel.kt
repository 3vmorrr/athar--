package org.athar.app.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.athar.app.AppGraph

enum class ReviewMicrophoneStatus {
    IDLE,
    CAPTURING,
    DENIED,
    REVOKED,
    INTERRUPTED,
    ERROR,
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
) {
    val capturedMillis: Long
        get() = capturedSamples * 1000L /
            ReviewAudioCaptureContract.SAMPLE_RATE_HZ
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

    fun probe() {
        if (mutable.value.probing) return
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
        if (!capture.hasPermission()) {
            permissionDenied()
            return
        }
        if (mutable.value.microphoneStatus == ReviewMicrophoneStatus.CAPTURING) return

        requestedStop.set(null)
        mutable.update {
            it.copy(
                microphoneStatus = ReviewMicrophoneStatus.CAPTURING,
                capturedSamples = 0,
                peak = 0f,
            )
        }
        try {
            capture.start(
                scope = viewModelScope,
                onChunk = { chunk ->
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
                                            ReviewAudioCaptureContract.SAMPLE_RATE_HZ,
                                    ),
                                peak = ReviewAudioCaptureContract.peak(chunk),
                            )
                        }
                    }
                },
                onStopped = { reason ->
                    val override = requestedStop.getAndSet(null)
                    val status = when (override) {
                        "background" -> ReviewMicrophoneStatus.BACKGROUNDED
                        "user" -> ReviewMicrophoneStatus.IDLE
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
                    mutable.update {
                        it.copy(
                            microphoneStatus = status,
                            peak = 0f,
                        )
                    }
                },
            )
        } catch (_: SecurityException) {
            permissionDenied()
        } catch (_: Exception) {
            mutable.update {
                it.copy(
                    microphoneStatus = ReviewMicrophoneStatus.ERROR,
                    peak = 0f,
                )
            }
        }
    }

    fun stopMicrophone() {
        if (mutable.value.microphoneStatus != ReviewMicrophoneStatus.CAPTURING) return
        requestedStop.set("user")
        capture.stop()
    }

    fun onBackgrounded() {
        if (mutable.value.microphoneStatus != ReviewMicrophoneStatus.CAPTURING) return
        requestedStop.set("background")
        capture.stop()
    }

    fun recheckPermission() {
        if (
            mutable.value.microphoneStatus == ReviewMicrophoneStatus.CAPTURING &&
            !capture.hasPermission()
        ) {
            capture.stop()
        }
    }

    override fun onCleared() {
        requestedStop.set("user")
        capture.stop()
        super.onCleared()
    }
}
