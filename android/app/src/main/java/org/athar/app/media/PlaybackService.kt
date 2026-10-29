package org.athar.app.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.athar.app.AtharApplication
import org.athar.app.MainActivity
import org.athar.app.auth.AuthState
import org.athar.app.core.network.ApiException

/** The public service never accepts arbitrary URLs or private API bearer tokens. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val ownership = PlaybackOwnership()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun synchronizeOwner(): Long? {
        val auth = (application as AtharApplication).graph.session
        val candidate = (auth.state.value as? AuthState.SignedIn)?.user?.id
        val owner = try { candidate?.let { auth.requireVerifiedOwner(it) } } catch (_: ApiException) { null }
        if (ownership.update(owner)) session?.player?.run { stop(); clearMediaItems() }
        return ownership.owner
    }
    private fun allowed(controller: MediaSession.ControllerInfo, replaceMedia: Boolean = false): Boolean {
        synchronizeOwner()
        val hints = controller.connectionHints
        val requestedOwner = if (hints.containsKey(PLAYBACK_OWNER_HINT)) hints.getLong(PLAYBACK_OWNER_HINT, -1L) else null
        return if (replaceMedia) ownership.allowsMediaReplacement(applicationInfo.uid, controller.uid, controller.isTrusted, requestedOwner)
            else ownership.allows(applicationInfo.uid, controller.uid, controller.isTrusted, requestedOwner)
    }
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        val sessionActivity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(sessionActivity).setCallback(object : MediaSession.Callback {
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if (!allowed(controller)) return MediaSession.ConnectionResult.reject()
                val result = super.onConnect(session, controller)
                if (!result.isAccepted || controller.uid == applicationInfo.uid) return result
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(result.availableSessionCommands)
                    .setAvailablePlayerCommands(result.availablePlayerCommands.buildUpon()
                        .remove(Player.COMMAND_SET_MEDIA_ITEM).remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).build())
                    .build()
            }
            override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int =
                if (allowed(controller)) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_PERMISSION_DENIED
            override fun onAddMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
                if (!allowed(controller, replaceMedia = true)) return Futures.immediateFailedFuture(SecurityException("playback_session_changed"))
                // Reject the full request so filtering cannot change its selected index.
                if (mediaItems.any { item -> item.localConfiguration?.uri?.let(::approvedMedia) != true })
                    return Futures.immediateFailedFuture(IllegalArgumentException("unapproved_media"))
                return Futures.immediateFuture(mediaItems)
            }
        }).build()
        scope.launch { (application as AtharApplication).graph.session.state.collect { synchronizeOwner() } }
        scope.launch { (application as AtharApplication).graph.deletionStore.blockedOwners.collect { synchronizeOwner() } }
    }
    // Media-button startup can provide a placeholder ControllerInfo. Verify the real
    // controller in onConnect, while this service callback checks the live account.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = if (synchronizeOwner() != null) session else null
    override fun onDestroy() { scope.cancel(); session?.run { player.release(); release() }; session = null; super.onDestroy() }
    companion object {
        fun approvedMedia(uri: Uri): Boolean {
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 && (host == "mp3quran.net" || host.endsWith(".mp3quran.net") || host == "qurango.net" || host.endsWith(".qurango.net") || host == "stream.radiojar.com")
        }
    }
}
