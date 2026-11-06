package com.sonara.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonara.player.MainActivity
import com.sonara.player.SonaraApp

/**
 * Owns the one real player. It plays in the background and drives the notification, lock screen,
 * Bluetooth and headset controls through a MediaSession.
 *
 * Everything that must keep working while the screen is off lives HERE, not in the UI:
 *  - moving on to the next song when a song can't be played
 *  - the cover art and metadata the notification shows (kept in sync by the session)
 *  - the audio effects (equalizer, bass, loudness) attached to the player's audio session
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var consecutiveErrors = 0

    private val serviceListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            // A broken file or an expired stream must not silently stop the music (and leave the
            // notification showing a song that isn't playing). Skip ahead, but never loop forever.
            val p = mediaSession?.player ?: return
            consecutiveErrors++
            if (p.hasNextMediaItem() && consecutiveErrors <= p.mediaItemCount) {
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            (application as SonaraApp).container.fx.attach(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep CPU + Wi-Fi awake while streaming
            .build()
        player.addListener(serviceListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtworkBitmapLoader(this))
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    // Make sure every item keeps its playable URI when it crosses from the UI to the service.
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration != null) item
                        else item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(resolved)
                }
            })
            .build()

        // Effects need the player's audio session id; it exists as soon as the player is built.
        (application as SonaraApp).container.fx.attach(player.audioSessionId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** If the user swipes the app away while paused, stop the service so nothing lingers. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        (application as SonaraApp).container.fx.release()
        mediaSession?.run {
            player.removeListener(serviceListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
