package org.athar.app.media

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.*
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AyahTiming(val ayah: Int, val start: Long, val end: Long)
data class AudioUiState(
    val loading: Boolean = false,
    val error: Boolean = false,
    val recording: JsonElement? = null,
    val timings: List<AyahTiming> = emptyList(),
    val playing: Boolean = false,
    val buffer: Boolean = false,
    val interrupted: Boolean = false,
    val clipStart: Long = 0,
    val mediaKey: String? = null,
    val radio: Boolean = false,
)

class AudioViewModel(private val graph: AppGraph, application: android.app.Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(AudioUiState())
    val state = mutable.asStateFlow()
    private val mutableSelection = MutableStateFlow(AudioSelection())
    val selection = mutableSelection.asStateFlow()
    fun select(value: AudioSelection) {
        val previous = mutableSelection.value
        val continuePlayback = mutable.value.playing && !mutable.value.radio && mutable.value.mediaKey == previous.mediaKey
        mutableSelection.value = value
        if (continuePlayback && value.mediaKey != previous.mediaKey) {
            if (value.mediaKey == null) stop() else startSelection()
        }
    }
    fun startSelection() {
        val value = mutableSelection.value
        val reader = value.reader ?: return
        val edition = value.edition ?: return
        load(reader.text("id").toInt(), edition.text("id").toInt(), value.surah, if (value.verseMode) value.ayah else null)
    }
    fun playSelection() {
        if (!mutable.value.error && !mutable.value.radio && mutable.value.mediaKey == mutableSelection.value.mediaKey && mutable.value.recording != null) toggle()
        else startSelection()
    }
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null
    private var request: Job? = null
    private var requestGeneration: Long = 0L

    private fun nextRequestGeneration(): Long {
        requestGeneration += 1
        return requestGeneration
    }

    private fun isCurrentRequest(generation: Long): Boolean =
        generation == requestGeneration
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutable.value = mutable.value.copy(playing = isPlaying)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            mutable.value = mutable.value.copy(
                buffer = playbackState == Player.STATE_BUFFERING,
            )
        }
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            mutable.value = mutable.value.copy(
                interrupted = playbackSuppressionReason !=
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            )
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let(::hydrateFromPlayer)
        }
        override fun onPlayerError(error: PlaybackException) {
            mutable.value = mutable.value.copy(error = true, loading = false)
        }
    }

    init {
        viewModelScope.launch {
            try {
                hydrateFromPlayer(player())
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // The screen can still select fresh media if a stale service session is unavailable.
            }
        }
    }
    private suspend fun player(): MediaController {
        controller?.let { return it }
        val ready = suspendCancellableCoroutine<MediaController> { continuation ->
            future.addListener({ if (continuation.isActive) try { continuation.resume(future.get()) } catch (error: Exception) { continuation.resumeWithException(error) } }, ContextCompat.getMainExecutor(getApplication()))
        }
        controller = ready
        ready.addListener(listener)
        hydrateFromPlayer(ready)
        return ready
    }

    private fun hydrateFromPlayer(player: Player) {
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return
        val radioItem = mediaId.startsWith("radio:")
        val metadata = item.mediaMetadata
        val recording = JsonObject().apply {
            if (radioItem) {
                addProperty("name", metadata.title?.toString().orEmpty())
            } else {
                addProperty("name", metadata.title?.toString().orEmpty())
                addProperty("reciter_name", metadata.artist?.toString().orEmpty())
            }
        }
        mutable.value = mutable.value.copy(
            loading = false,
            error = false,
            recording = recording,
            playing = player.isPlaying,
            buffer = player.playbackState == Player.STATE_BUFFERING,
            interrupted = player.playbackSuppressionReason !=
                Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            clipStart = metadata.extras?.getLong("athar_clip_start_ms", 0L) ?: 0L,
            mediaKey = mediaId,
            radio = radioItem,
        )
    }
    fun load(reciter: Int, edition: Int, surah: Int, ayah: Int? = null) {
        request?.cancel()
        val generation = nextRequestGeneration()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                loading = true,
                error = false,
                buffer = false,
            )
            try {
                val player = player()
                val mediaKey = "${reciter}:${edition}:${surah}:${ayah ?: 0}"
                if (player.currentMediaItem?.mediaId != mediaKey && player.isPlaying) {
                    player.pause()
                }
                val query = mapOf(
                    "reciter" to "$reciter",
                    "edition" to "$edition",
                    "surah" to "$surah",
                )
                val quranData = graph.quran.data()
                val verseCount = quranData.surah(surah).size
                val audio = graph.content.get("quran/audio", query).data
                if (!isCurrentRequest(generation)) return@launch

                val uri = Uri.parse(audio.text("url"))
                require(PlaybackService.approvedMedia(uri))

                var timings = emptyList<AyahTiming>()
                if (audio.text("tracking_available") == "true") {
                    try {
                        val timingRows = graph.content
                            .get("quran/timing", query)
                            .data
                            .array("items")
                        if (!isCurrentRequest(generation)) return@launch
                        timings = validatedAyahTimings(
                            timingRows.map {
                                AyahTiming(
                                    it.text("ayah").toInt(),
                                    it.text("start_ms").toLong(),
                                    it.text("end_ms").toLong(),
                                )
                            },
                            verseCount,
                        )
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        // Whole-surah playback remains usable without timing.
                        timings = emptyList()
                    }
                }

                val selected = ayah?.let { number ->
                    timingForAyah(timings, number)
                        ?: throw IllegalArgumentException("Timing unavailable")
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(quranData.index.surahs[surah - 1].name)
                    .setArtist(audio.text("reciter_name"))
                    .setExtras(
                        Bundle().apply {
                            putLong("athar_clip_start_ms", selected?.start ?: 0L)
                            putInt("athar_surah", surah)
                            putInt("athar_ayah", ayah ?: 0)
                        },
                    )
                    .build()
                val item = MediaItem.Builder()
                    .setMediaId(mediaKey)
                    .setUri(uri)
                    .setMediaMetadata(metadata)

                if (selected != null) {
                    item.setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(selected.start)
                            .setEndPositionMs(selected.end)
                            .build(),
                    )
                }
                if (!isCurrentRequest(generation)) return@launch

                player.stop()
                player.clearMediaItems()
                player.setMediaItem(item.build())
                player.prepare()
                player.play()

                mutable.value = AudioUiState(
                    recording = audio,
                    loading = false,
                    timings = timings,
                    clipStart = selected?.start ?: 0L,
                    mediaKey = mediaKey,
                    radio = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (isCurrentRequest(generation)) {
                    mutable.value = mutable.value.copy(
                        error = true,
                        loading = false,
                        buffer = false,
                    )
                }
            }
        }
    }

    fun radio(row: JsonElement) {
        request?.cancel()
        val generation = nextRequestGeneration()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                loading = true,
                error = false,
                radio = true,
            )
            try {
                val player = player()
                val mediaId = "radio:${row.text("id")}"
                val uri = Uri.parse(row.text("url"))
                require(PlaybackService.approvedMedia(uri))
                if (!isCurrentRequest(generation)) return@launch

                player.stop()
                player.clearMediaItems()
                player.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(row.text("name"))
                                .build(),
                        )
                        .build(),
                )
                player.prepare()
                player.play()
                mutable.value = AudioUiState(
                    recording = row,
                    mediaKey = mediaId,
                    radio = true,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (isCurrentRequest(generation)) {
                    mutable.value = mutable.value.copy(
                        error = true,
                        loading = false,
                        buffer = false,
                    )
                }
            }
        }
    }

    fun retry() {
        if (mutable.value.loading) return
        val selection = mutableSelection.value
        if (!mutable.value.radio && selection.mediaKey != null) {
            startSelection()
            return
        }

        request?.cancel()
        val generation = nextRequestGeneration()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = false)
            try {
                val player = player()
                val item = player.currentMediaItem ?: throw IllegalStateException()
                val uri = item.localConfiguration?.uri ?: throw IllegalStateException()
                require(PlaybackService.approvedMedia(uri))
                if (!isCurrentRequest(generation)) return@launch

                player.prepare()
                player.play()
                mutable.value = mutable.value.copy(
                    loading = false,
                    error = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (isCurrentRequest(generation)) {
                    mutable.value = mutable.value.copy(
                        loading = false,
                        error = true,
                        buffer = false,
                    )
                }
            }
        }
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } } }
    fun seekTo(positionMs: Long) {
        controller?.let { player ->
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
            player.seekTo(positionMs.coerceIn(0L, duration))
        }
    }
    fun duration(): Long = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }
    fun playbackSpeed(): Float = controller?.playbackParameters?.speed ?: 1f
    fun stop() {
        request?.cancel()
        nextRequestGeneration()
        controller?.stop()
        controller?.clearMediaItems()
        mutable.value = AudioUiState()
    }
    fun position(): Long = (controller?.currentPosition ?: 0) + mutable.value.clipStart
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
