package org.athar.app.media

import android.content.ComponentName
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
data class AudioUiState(val loading: Boolean = false, val error: Boolean = false, val recording: JsonElement? = null, val timings: List<AyahTiming> = emptyList(), val playing: Boolean = false, val buffer: Boolean = false, val clipStart: Long = 0, val mediaKey: String? = null, val radio: Boolean = false)

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
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutable.value = mutable.value.copy(playing = isPlaying)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            mutable.value = mutable.value.copy(
                buffer = playbackState == Player.STATE_BUFFERING,
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
            mediaKey = mediaId,
            radio = radioItem,
        )
    }
    fun load(reciter: Int, edition: Int, surah: Int, ayah: Int? = null) {
        request?.cancel()
        request = viewModelScope.launch {
            mutable.value = AudioUiState(loading = true)
            try {
                val player = player(); player.stop(); player.clearMediaItems()
                val query = mapOf("reciter" to "$reciter", "edition" to "$edition", "surah" to "$surah")
                val audio = graph.content.get("quran/audio", query).data
                val mediaKey = "$reciter:$edition:$surah:${ayah ?: 0}"
                val item = MediaItem.Builder().setMediaId(mediaKey).setUri(audio.text("url"))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(graph.quran.data().index.surahs[surah - 1].name).setArtist(audio.text("reciter_name")).build())
                if (ayah == null) {
                    mutable.value = AudioUiState(recording = audio, mediaKey = mediaKey)
                    player.setMediaItem(item.build()); player.prepare(); player.play()
                }
                var timings = emptyList<AyahTiming>()
                if (audio.text("tracking_available") == "true") {
                    try { timings = graph.content.get("quran/timing", query).data.array("items").map { AyahTiming(it.text("ayah").toInt(), it.text("start_ms").toLong(), it.text("end_ms").toLong()) } }
                    catch (failure: CancellationException) { throw failure } catch (_: Exception) { /* Whole-surah playback remains usable without timing. */ }
                }
                val selected = ayah?.let { number -> timings.firstOrNull { it.ayah == number } ?: throw IllegalArgumentException("Timing unavailable") }
                if (selected != null) item.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(selected.start).setEndPositionMs(selected.end).build())
                mutable.value = mutable.value.copy(recording = audio, loading = false, timings = timings, clipStart = selected?.start ?: 0, mediaKey = mediaKey)
                if (selected != null) { player.setMediaItem(item.build()); player.prepare(); player.play() }
            } catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = mutable.value.copy(error = true, loading = false) }
        }
    }
    fun radio(row: JsonElement) {
        request?.cancel(); request = viewModelScope.launch {
            mutable.value = AudioUiState(loading = true, radio = true)
            try {
                val player = player()
                player.stop()
                player.clearMediaItems()
                val mediaId = "radio:${row.text("id")}"
                mutable.value = AudioUiState(
                    recording = row,
                    mediaKey = mediaId,
                    radio = true,
                )
                player.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(row.text("url"))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(row.text("name"))
                                .build(),
                        )
                        .build(),
                )
                player.prepare()
                player.play()
            }
            catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = AudioUiState(error = true) }
        }
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } } }
    fun stop() { request?.cancel(); controller?.stop(); mutable.value = AudioUiState() }
    fun position(): Long = (controller?.currentPosition ?: 0) + mutable.value.clipStart
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
