package org.athar.app.media

import android.content.ComponentName
import android.os.Bundle
import android.net.Uri
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
    val loading: Boolean = false, val error: Boolean = false, val recording: JsonElement? = null,
    val timings: List<AyahTiming> = emptyList(), val playing: Boolean = false,
    val buffer: Boolean = false, val clipStart: Long = 0, val mediaKey: String? = null,
    val radio: Boolean = false, val hasMedia: Boolean = false, val title: String = "",
    val artist: String = "", val durationMs: Long = 0, val seekable: Boolean = false,
    val speed: Float = 1f, val repeatOne: Boolean = false, val interrupted: Boolean = false,
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
        val continuePlayback = controller?.playWhenReady == true && !mutable.value.interrupted && !mutable.value.radio && mutable.value.mediaKey == previous.mediaKey
        mutableSelection.value = value
        if (value.mediaKey != previous.mediaKey && requests.selectionChanged(value.mediaKey)) {
            request?.cancel()
            timingRecovery?.cancel()
            requestError = false
            mutable.value = mutable.value.copy(loading = false, error = controller?.playerError != null)
        }
        if (continuePlayback && value.mediaKey != previous.mediaKey) {
            if (value.mediaKey == null) stop() else startSelection()
        }
    }
    fun startSelection() {
        val value = mutableSelection.value
        val reader = value.reader ?: return
        val edition = value.edition ?: return
        val source = runCatching { QuranAudioSource(reader.text("id").toInt(), edition.text("id").toInt(), value.surah, value.ayah.takeIf { value.verseMode }) }.getOrNull()
        if (source == null) { requestError = true; mutable.value = mutable.value.copy(error = true); return }
        load(source.reciter, source.edition, source.surah, source.ayah)
    }
    fun playSelection() {
        if (!mutable.value.radio && mutable.value.mediaKey == mutableSelection.value.mediaKey && mutable.value.recording != null && controller?.playerError == null) {
            requestError = false
            mutable.value = mutable.value.copy(error = false)
            mutable.value.mediaKey?.let(QuranAudioSource::fromKey)?.let { lastRequested = RequestedAudio.Quran(it) }
            toggle()
            if (mutable.value.timings.isEmpty()) mutable.value.mediaKey?.let(::recoverTimings)
        } else startSelection()
    }
    private val future = MediaController.Builder(application, SessionToken(application, ComponentName(application, PlaybackService::class.java)))
        .setConnectionHints(Bundle().apply { putLong(PLAYBACK_OWNER_HINT, owner ?: -1L) }).buildAsync()
    private var controller: MediaController? = null
    private var request: Job? = null
    private var timingRecovery: Job? = null
    private val requests = AudioRequests()
    private var requestError = false
    private sealed interface RequestedAudio {
        data class Quran(val source: QuranAudioSource) : RequestedAudio
        data class Station(val row: JsonElement) : RequestedAudio
    }
    private var lastRequested: RequestedAudio? = null
    private fun beginRequest(key: String? = null): Long {
        request?.cancel()
        timingRecovery?.cancel()
        requestError = false
        return requests.begin(key)
    }
    private fun current(generation: Long) = requests.current(generation) && ownerCurrent()
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
        if (!ownerCurrent()) { timingRecovery?.cancel(); mutable.value = AudioUiState(); return }
        val item = player.currentMediaItem
        val key = item?.mediaId
        val changed = key != mutable.value.mediaKey
        val radio = key?.startsWith("radio:") == true
        val metadata = player.mediaMetadata
        val origin = if (radio || item == null) 0L else
            (item.mediaMetadata.extras?.getLong("athar_clip_start_ms", item.clippingConfiguration.startPositionMs)
                ?: item.clippingConfiguration.startPositionMs).takeIf { it in 0..86_400_000L } ?: 0L
        val recording = if (item == null) null else if (changed || mutable.value.recording == null) JsonObject().apply {
            addProperty("name", metadata.title?.toString().orEmpty())
            if (!radio) addProperty("reciter_name", metadata.artist?.toString().orEmpty())
        } else mutable.value.recording
        mutable.value = mutable.value.copy(
            recording = recording,
            timings = if (changed) emptyList() else mutable.value.timings,
            clipStart = origin,
            hasMedia = player.mediaItemCount > 0,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            playing = player.isPlaying,
            buffer = player.playbackState == Player.STATE_BUFFERING,
            interrupted = player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            error = requestError || player.playerError != null,
            mediaKey = key,
            radio = radio,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            seekable = player.isCurrentMediaItemSeekable && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
            speed = player.playbackParameters.speed,
            repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE,
        )
        if (changed && key != null && request?.isActive != true) recoverTimings(key)
    }
    private fun recoverTimings(key: String) {
        timingRecovery?.cancel()
        val source = QuranAudioSource.fromKey(key) ?: return
        val generation = requests.generation
        timingRecovery = viewModelScope.launch {
            try {
                val data = graph.quran.data()
                val timings = fetchTimings(source, data.surah(source.surah).size)
                if (!current(generation) || controller?.currentMediaItem?.mediaId != key) return@launch
                if (source.ayah != null && timingForAyah(timings, source.ayah)?.start != mutable.value.clipStart) return@launch
                mutable.value = mutable.value.copy(timings = timings)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { /* Retained playback stays usable without timing. */ }
        }
    }
    private suspend fun fetchTimings(source: QuranAudioSource, verseCount: Int): List<AyahTiming> {
        graph.session.requireVerifiedOwner(owner)
        val rows = graph.content.get("quran/timing", mapOf("reciter" to "${source.reciter}", "edition" to "${source.edition}", "surah" to "${source.surah}"))
            .data.array("items")
        graph.session.requireVerifiedOwner(owner)
        return validatedAyahTimings(rows.map { AyahTiming(it.text("ayah").toInt(), it.text("start_ms").toLong(), it.text("end_ms").toLong()) }, verseCount)
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
        if (!ownerCurrent()) return
        val source = runCatching { QuranAudioSource(reciter, edition, surah, ayah) }.getOrNull()
        if (source == null) { beginRequest(); requestError = true; mutable.value = mutable.value.copy(error = true, loading = false); return }
        val generation = beginRequest(source.mediaKey)
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = false, buffer = false)
            try {
                lastRequested = RequestedAudio.Quran(source)
                val player = player()
                if (player.currentMediaItem?.mediaId != source.mediaKey && player.isPlaying) player.pause()
                val data = graph.quran.data()
                val verseCount = data.surah(surah).size
                if (ayah != null) data.index.global(surah, ayah)
                if (!current(generation)) return@launch
                val audio = graph.content.get("quran/audio", mapOf("reciter" to "$reciter", "edition" to "$edition", "surah" to "$surah")).data
                if (!current(generation)) return@launch
                val uri = Uri.parse(audio.text("url"))
                require(PlaybackService.approvedMedia(uri))
                fun publish(timings: List<AyahTiming>, clip: AyahTiming?) {
                    if (!current(generation)) return
                    val metadata = MediaMetadata.Builder().setTitle(data.index.surahs[surah - 1].name).setArtist(audio.text("reciter_name"))
                        .setExtras(Bundle().apply {
                            putLong("athar_clip_start_ms", clip?.start ?: 0L)
                            putString("athar_media_uri", uri.toString())
                            putInt("athar_surah", surah); putInt("athar_ayah", ayah ?: 0)
                        }).build()
                    val item = MediaItem.Builder().setMediaId(source.mediaKey).setUri(uri).setMediaMetadata(metadata)
                    if (clip != null) item.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.start).setEndPositionMs(clip.end).build())
                    player.stop(); player.clearMediaItems(); player.setMediaItem(item.build()); player.prepare(); player.play()
                    mutable.value = mutable.value.copy(recording = audio, loading = false, error = false, timings = timings,
                        clipStart = clip?.start ?: 0L, mediaKey = source.mediaKey, radio = false)
                }
                // Whole-surah playback does not wait for the optional timing service.
                if (ayah == null) publish(emptyList(), null)
                val timings = if (audio.text("tracking_available") == "true") {
                    try { fetchTimings(source, verseCount) }
                    catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { emptyList() }
                } else emptyList()
                if (!current(generation)) return@launch
                if (ayah == null) {
                    if (mutable.value.mediaKey == source.mediaKey) mutable.value = mutable.value.copy(timings = timings)
                } else {
                    val selected = timingForAyah(timings, ayah) ?: error("Timing unavailable")
                    publish(timings, selected)
                }
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { if (current(generation)) { requestError = true; mutable.value = mutable.value.copy(error = true, loading = false, buffer = false) } }
        }
    }
    fun radio(row: JsonElement) {
        if (!ownerCurrent()) return
        val station = row.deepCopy()
        val generation = beginRequest("radio:${station.text("id")}")
        lastRequested = RequestedAudio.Station(station)
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = false, buffer = false)
            try {
                val player = player()
                val uri = Uri.parse(station.text("url"))
                require(PlaybackService.approvedMedia(uri))
                require((station.text("id").toLongOrNull() ?: 0) > 0)
                if (!current(generation)) return@launch
                val metadata = MediaMetadata.Builder().setTitle(station.text("name"))
                    .setExtras(Bundle().apply { putString("athar_media_uri", uri.toString()) }).build()
                player.stop(); player.clearMediaItems()
                val key = "radio:${station.text("id")}"
                player.setMediaItem(MediaItem.Builder().setMediaId(key).setUri(uri).setMediaMetadata(metadata).build())
                player.prepare(); player.play()
                mutable.value = mutable.value.copy(recording = station, loading = false, error = false, timings = emptyList(),
                    clipStart = 0, mediaKey = key, radio = true)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { if (current(generation)) { requestError = true; mutable.value = mutable.value.copy(error = true, loading = false, buffer = false) } }
        }
    }
    fun retry() {
        if (!ownerCurrent() || mutable.value.loading) return
        when (val requested = lastRequested) {
            is RequestedAudio.Quran -> requested.source.let { load(it.reciter, it.edition, it.surah, it.ayah) }
            is RequestedAudio.Station -> radio(requested.row)
            null -> {
                val source = mutable.value.mediaKey?.let(QuranAudioSource::fromKey)
                if (source != null) { load(source.reciter, source.edition, source.surah, source.ayah); return }
                val generation = beginRequest()
                request = viewModelScope.launch {
                    try {
                        val player = player()
                        val item = requireNotNull(player.currentMediaItem)
                        val uri = item.localConfiguration?.uri ?: item.mediaMetadata.extras?.getString("athar_media_uri")?.let(Uri::parse)
                        require(uri != null && PlaybackService.approvedMedia(uri))
                        if (!current(generation)) return@launch
                        player.prepare(); player.play()
                        mutable.value = mutable.value.copy(error = false, loading = false)
                    } catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { if (current(generation)) { requestError = true; mutable.value = mutable.value.copy(error = true, loading = false) } }
                }
            }
        }
    }
    fun toggle() { if (ownerCurrent()) controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } } }
    fun stop() { beginRequest(); lastRequested = null; if (ownerCurrent()) { controller?.stop(); controller?.clearMediaItems() }; mutable.value = AudioUiState() }
    fun elapsed(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0
    fun seek(positionMs: Long) { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && it.isCurrentMediaItemSeekable }?.seekTo(positionMs.coerceIn(0, mutable.value.durationMs)) }
    fun speed(value: Float) { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) }?.setPlaybackSpeed(value.coerceIn(0.75f, 1.5f)) }
    fun repeat() { if (ownerCurrent()) controller?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) }?.let { it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE } }
    fun supportsSpeed(): Boolean = controller?.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH) == true
    fun supportsRepeat(): Boolean = controller?.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE) == true
    fun position(): Long = (controller?.currentPosition ?: 0) + mutable.value.clipStart
    override fun onCleared() { controller?.removeListener(listener); MediaController.releaseFuture(future) }
}
