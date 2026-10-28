package org.athar.app.media

import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.*
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AyahTiming(val ayah: Int, val start: Long, val end: Long)
data class AudioUiState(
    val loading: Boolean = false, val error: Boolean = false, val recording: JsonElement? = null,
    val timings: List<AyahTiming> = emptyList(), val playing: Boolean = false,
    val buffer: Boolean = false, val clipStart: Long = 0, val mediaKey: String? = null,
    val radio: Boolean = false, val hasMedia: Boolean = false, val title: String = "",
    val artist: String = "", val durationMs: Long = 0, val seekable: Boolean = false,
    val speed: Float = 1f, val repeatOne: Boolean = false,
)

class AudioViewModel(private val graph: AppGraph, application: android.app.Application) : AndroidViewModel(application) {
    private val owner = graph.session.userId
    private fun ownerCurrent(): Boolean = try { graph.session.requireVerifiedOwner(owner); true } catch (_: org.athar.app.core.network.ApiException) { false }
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
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setConnectionHints(Bundle().apply { putLong(PLAYBACK_OWNER_HINT, owner ?: -1L) }).buildAsync()
    private var controller: MediaController? = null
    private var request: Job? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { readPlayer(player) }
        override fun onPlayerError(error: PlaybackException) { mutable.value = mutable.value.copy(error = true, loading = false) }
    }
    init {
        // Connect once at the signed-in shell, including when a surviving service already plays.
        viewModelScope.launch {
            try { readPlayer(player()) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = true) }
        }
    }
    private fun readPlayer(player: Player) {
        if (!ownerCurrent()) { mutable.value = AudioUiState(); return }
        mutable.value = mutable.value.copy(
            hasMedia = player.mediaItemCount > 0,
            title = player.mediaMetadata.title?.toString().orEmpty(),
            artist = player.mediaMetadata.artist?.toString().orEmpty(),
            playing = player.isPlaying,
            buffer = player.playbackState == Player.STATE_BUFFERING,
            error = player.playerError != null,
            mediaKey = player.currentMediaItem?.mediaId,
            radio = player.currentMediaItem?.mediaId?.startsWith("radio:") == true,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            seekable = player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            speed = player.playbackParameters.speed,
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
        )
    }
    private suspend fun player(): MediaController {
        graph.session.requireVerifiedOwner(owner)
        controller?.let { return it }
        val ready = suspendCancellableCoroutine<MediaController> { continuation ->
            future.addListener({ if (continuation.isActive) try { continuation.resume(future.get()) } catch (error: Exception) { continuation.resumeWithException(error) } }, ContextCompat.getMainExecutor(getApplication()))
        }
        graph.session.requireVerifiedOwner(owner)
        controller = ready; ready.addListener(listener); readPlayer(ready); return ready
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
            try { val player = player(); player.stop(); player.clearMediaItems(); mutable.value = AudioUiState(recording = row, radio = true); player.setMediaItem(MediaItem.Builder().setMediaId("radio:${row.text("id")}").setUri(row.text("url")).setMediaMetadata(MediaMetadata.Builder().setTitle(row.text("name")).build()).build()); player.prepare(); player.play() }
            catch (failure: CancellationException) { throw failure } catch (_: Exception) { mutable.value = AudioUiState(error = true) }
        }
    }
    fun toggle() { if (ownerCurrent()) controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } } }
    fun stop() { request?.cancel(); if (ownerCurrent()) { controller?.stop(); controller?.clearMediaItems() }; mutable.value = AudioUiState() }
    fun elapsed(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0
    fun seek(positionMs: Long) { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && it.isCurrentMediaItemSeekable }?.seekTo(positionMs.coerceIn(0, mutable.value.durationMs)) }
    fun speed(value: Float) { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) }?.setPlaybackSpeed(value.coerceIn(0.75f, 1.5f)) }
    fun repeat() { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) }?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE } }
    fun supportsSpeed(): Boolean = controller?.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) == true
    fun supportsRepeat(): Boolean = controller?.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) == true
    fun position(): Long = (controller?.currentPosition ?: 0) + mutable.value.clipStart
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
