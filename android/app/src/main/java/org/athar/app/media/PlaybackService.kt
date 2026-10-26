package org.athar.app.media

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState

/** The public service never accepts arbitrary URLs or private API bearer tokens. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (controller.packageName != packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return super.onConnect(session, controller)
            }
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                val safe = mediaItems.filter { item -> item.localConfiguration?.uri?.let(::approvedMedia) == true }.toMutableList()
                return Futures.immediateFuture(safe)
            }
        }).build()
        scope.launch { (application as AtharApplication).graph.session.state.collect { state -> if (state !is AuthState.SignedIn) { player.stop(); player.clearMediaItems() } } }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = if ((application as AtharApplication).graph.session.state.value is AuthState.SignedIn) session else null
    override fun onDestroy() { scope.cancel(); session?.run { player.release(); release() }; session = null; super.onDestroy() }
    companion object {
        fun approvedMedia(uri: Uri): Boolean {
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && (host == "mp3quran.net" || host.endsWith(".mp3quran.net") || host == "qurango.net" || host.endsWith(".qurango.net") || host == "stream.radiojar.com")
        }
    }
}
