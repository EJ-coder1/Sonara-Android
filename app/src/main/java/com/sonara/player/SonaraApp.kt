package com.sonara.player

import android.app.Application
import android.os.SystemClock
import com.sonara.player.data.JamendoRepository
import com.sonara.player.data.LibraryStore
import com.sonara.player.data.LocalMusicRepository
import com.sonara.player.data.SessionWindow
import com.sonara.player.data.SessionSnapshot
import com.sonara.player.data.cacheKey
import com.sonara.player.data.downloads.DownloadsManager
import com.sonara.player.data.lyrics.LyricsRepository
import com.sonara.player.playback.AudioEffectsManager
import com.sonara.player.playback.PlayCache
import com.sonara.player.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Holds the app-wide singletons (no DI framework needed for an app this size). */
class AppContainer(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val store = LibraryStore(app)
    val local = LocalMusicRepository(app)
    val jamendo = JamendoRepository(BuildConfig.JAMENDO_CLIENT_ID)
    val player = PlayerController(app)
    val fx = AudioEffectsManager(store)
    val lyrics = LyricsRepository(app)
    val downloads = DownloadsManager(app, store, jamendo, scope)

    @Volatile private var resumeEnabled = true
    @Volatile private var cacheLimitBytes = 512L * 1024L * 1024L

    init {
        // Downloaded songs play from the phone instead of streaming.
        player.trackResolver = { track -> downloads.resolve(track) }

        // Every track that starts playing goes into "Recently played" (and the play cache gets a size check).
        player.onTrackStarted = { track ->
            scope.launch { store.addRecent(track) }
            trimPlayCache()
        }

        scope.launch {
            store.offlinePrefs.collect { prefs ->
                resumeEnabled = prefs.resumeSession
                cacheLimitBytes = prefs.cacheLimitMb.toLong() * 1024L * 1024L
                trimPlayCache()
            }
        }

        restoreLastSession()
        keepSavingSession()
    }

    /** Deletes the oldest cached songs when the cache is bigger than the limit chosen in Settings. */
    fun trimPlayCache() {
        val playingKey = player.state.value.current?.cacheKey()
        val limit = cacheLimitBytes
        scope.launch(Dispatchers.IO) {
            PlayCache.trim(app, limit, setOfNotNull(playingKey))
        }
    }

    /** Opens the last queue and song (paused) when the app starts, if the user wants that. */
    private fun restoreLastSession() {
        scope.launch {
            if (!store.offlinePrefs.first().resumeSession) return@launch
            val snapshot = store.session.first() ?: return@launch
            player.restoreSession(snapshot)
        }
    }

    /**
     * Saves what is playing when the song or queue changes, when playback pauses or resumes,
     * and every 15 seconds while playing (so the position is never far behind).
     */
    private fun keepSavingSession() {
        scope.launch {
            var lastKey = ""
            var lastSavedAt = 0L
            player.state.collect { s ->
                val current = s.current ?: return@collect
                if (!resumeEnabled || s.queueIndex < 0) return@collect
                val key = s.queue.size.toString() + ":" + s.queueIndex + ":" + current.id + ":" + s.isPlaying
                val now = SystemClock.elapsedRealtime()
                if (key == lastKey && !(s.isPlaying && now - lastSavedAt > 15_000L)) return@collect
                lastKey = key
                lastSavedAt = now
                val (from, to) = SessionWindow.window(s.queue.size, s.queueIndex)
                store.saveSession(
                    SessionSnapshot(
                        tracks = s.queue.subList(from, to),
                        index = s.queueIndex - from,
                        positionMs = s.positionMs,
                        shuffle = s.shuffle,
                        repeatMode = s.repeatMode,
                    )
                )
            }
        }
    }
}

class SonaraApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
