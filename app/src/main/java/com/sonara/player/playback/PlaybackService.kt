package com.sonara.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sonara.player.BuildConfig
import com.sonara.player.MainActivity
import com.sonara.player.SonaraApp

/**
 * Owns the one real player. It plays in the background and drives the notification, lock screen,
 * Bluetooth and headset controls through a MediaSession.
 *
 * Everything that must keep working while the screen is off lives HERE, not in the UI:
 *  - moving on to the next song when a song can't be played
 *  - the cover art and metadata the notification shows (kept in sync by the session, see [updateTrackMetadata])
 *  - crossfade between songs (see [CrossfadeController])
 *  - the audio effects (equalizer, bass, loudness) attached to the player's audio session
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var artworkLoader: ArtworkBitmapLoader? = null
    private var crossfade: CrossfadeController? = null
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

        // Every way a song can change ends up in one of these two callbacks: it ended by itself
        // (auto-next), Next / Previous in the app, the notification, the lock screen, a headset or
        // Bluetooth button, a tap in the queue, or a crossfade starting. All of them go through
        // the same single function below.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateTrackMetadata(mediaItem)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateTrackMetadata(mediaSession?.player?.currentMediaItem)
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
            .setMediaSourceFactory(buildMediaSourceFactory())
            .build()
        player.addListener(serviceListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        PlaybackBridge.attach(player)

        val loader = ArtworkBitmapLoader(this)
        artworkLoader = loader

        // Crossfade: the session player stays the one real player; a second player is only created
        // for the few seconds two songs overlap (see CrossfadeController).
        crossfade = CrossfadeController(player, CrossfadePrefs.get(this)) { buildStandbyPlayer(audioAttributes) }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setBitmapLoader(loader)
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

    /**
     * THE one function every song change goes through (auto-next, Next, Previous, notification,
     * lock screen, Bluetooth, queue taps, crossfade).
     *
     * With Media3 the session metadata (title, artist, album, cover) and the MediaStyle notification
     * are built from the player's current item, so what this function does is:
     *  1. warms the cover cache for this song and the next one, so the notification and the
     *     lock-screen session get title and cover together, with no waiting on disk or network;
     *  2. asks the notification to refresh right now. Media3 re-posts it and keeps the service in the
     *     foreground while playback is running (the equivalent of notify() + startForeground()).
     */
    private fun updateTrackMetadata(item: MediaItem?) {
        val session = mediaSession ?: return
        val player = session.player
        artworkLoader?.prefetch(item)
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET) artworkLoader?.prefetch(player.getMediaItemAt(nextIndex))
        triggerNotificationUpdate()
    }

    /** The second player, used only while two songs overlap. The main player owns audio focus. */
    private fun buildStandbyPlayer(attributes: AudioAttributes): ExoPlayer =
        ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .setMediaSourceFactory(buildMediaSourceFactory())
            .build()

    /**
     * Online songs go through the play cache (saved while streaming, so replays are free).
     * Songs on the phone and downloaded files are read directly and are never cached.
     */
    private fun buildMediaSourceFactory(): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent("Sonara/" + BuildConfig.VERSION_NAME)
        val cache = PlayCache.getOrNull(this)
        val network: DataSource.Factory = if (cache != null) {
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(http)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            http
        }
        // DefaultDataSource sends http(s) through [network] and reads content:// and file:// itself.
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(this, network))
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
        PlaybackBridge.detach()
        crossfade?.release()
        crossfade = null
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
