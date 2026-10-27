package org.athar.app.review

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object ReviewAudioCaptureContract {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_COUNT = 1
    const val CHUNK_SAMPLES = 1_600
    const val CHUNK_MILLIS = 100
    const val MAX_CHUNKS_HELD_BY_CALLER = 4
    const val MAX_CALLER_BUFFERED_SAMPLES =
        CHUNK_SAMPLES * MAX_CHUNKS_HELD_BY_CALLER

    fun normalizePcm16(
        source: ShortArray,
        count: Int,
    ): FloatArray {
        require(count in 1..minOf(source.size, CHUNK_SAMPLES))
        return FloatArray(count) { index ->
            (source[index].toFloat() / 32768f).coerceIn(-1f, 1f)
        }
    }

    fun peak(samples: FloatArray): Float {
        require(samples.size in 1..CHUNK_SAMPLES)
        var peak = 0f
        samples.forEach { value ->
            val magnitude = kotlin.math.abs(value)
            if (magnitude > peak) peak = magnitude
        }
        return peak.coerceIn(0f, 1f)
    }
}

sealed interface ReviewAudioStopReason {
    data object User : ReviewAudioStopReason
    data object PermissionRevoked : ReviewAudioStopReason
    data object Interrupted : ReviewAudioStopReason
    data object CaptureFailure : ReviewAudioStopReason
}

class ReviewAudioCapture(private val context: Context) {
    private val app = context.applicationContext
    private val stopping = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var job: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(
        scope: CoroutineScope,
        onChunk: (FloatArray) -> Unit,
        onStopped: (ReviewAudioStopReason) -> Unit,
    ) {
        require(hasPermission())
        check(job?.isActive != true)
        stopping.set(false)

        val minBufferBytes = AudioRecord.getMinBufferSize(
            ReviewAudioCaptureContract.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16,
        )
        require(minBufferBytes > 0)
        val bufferBytes = max(
            minBufferBytes,
            ReviewAudioCaptureContract.CHUNK_SAMPLES *
                Short.SIZE_BYTES * 2,
        )
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16)
                    .setSampleRate(ReviewAudioCaptureContract.SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED)
        recorder = audioRecord

        job = scope.launch(Dispatchers.IO) {
            var reason: ReviewAudioStopReason = ReviewAudioStopReason.User
            try {
                audioRecord.startRecording()
                check(
                    audioRecord.recordingState ==
                        AudioRecord.RECORDSTATE_RECORDING,
                )
                val pcm = ShortArray(ReviewAudioCaptureContract.CHUNK_SAMPLES)
                var capturedSamples = 0L
                val maximumSamples =
                    24L * 60L * ReviewAudioCaptureContract.SAMPLE_RATE_HZ
                while (
                    isActive &&
                    !stopping.get() &&
                    capturedSamples < maximumSamples
                ) {
                    if (!hasPermission()) {
                        reason = ReviewAudioStopReason.PermissionRevoked
                        break
                    }
                    val count = audioRecord.read(
                        pcm,
                        0,
                        pcm.size,
                        AudioRecord.READ_BLOCKING,
                    )
                    when {
                        count > 0 -> {
                            capturedSamples += count
                            onChunk(
                                ReviewAudioCaptureContract.normalizePcm16(
                                    pcm,
                                    count,
                                ),
                            )
                        }
                        count == AudioRecord.ERROR_DEAD_OBJECT -> {
                            reason = ReviewAudioStopReason.Interrupted
                            break
                        }
                        count < 0 -> {
                            reason = ReviewAudioStopReason.CaptureFailure
                            break
                        }
                    }
                }
            } catch (failure: CancellationException) {
                reason = ReviewAudioStopReason.User
                throw failure
            } catch (_: SecurityException) {
                reason = ReviewAudioStopReason.PermissionRevoked
            } catch (_: Exception) {
                reason = ReviewAudioStopReason.CaptureFailure
            } finally {
                runCatching {
                    if (
                        audioRecord.recordingState ==
                        AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        audioRecord.stop()
                    }
                }
                audioRecord.release()
                synchronized(this@ReviewAudioCapture) {
                    if (recorder === audioRecord) recorder = null
                    job = null
                }
                onStopped(reason)
            }
        }
    }

    @Synchronized
    fun stop() {
        stopping.set(true)
        runCatching {
            recorder?.takeIf {
                it.recordingState == AudioRecord.RECORDSTATE_RECORDING
            }?.stop()
        }
        job?.cancel()
    }
}
