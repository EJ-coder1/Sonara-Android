package com.sonara.player

import android.app.Application
import com.sonara.player.data.JamendoRepository
import com.sonara.player.data.LibraryStore
import com.sonara.player.data.LocalMusicRepository
import com.sonara.player.data.lyrics.LyricsRepository
import com.sonara.player.playback.AudioEffectsManager
import com.sonara.player.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app-wide singletons (no DI framework needed for an app this size). */
class AppContainer(app: Application) {
    val store = LibraryStore(app)
    val local = LocalMusicRepository(app)
    val jamendo = JamendoRepository(BuildConfig.JAMENDO_CLIENT_ID)
    val player = PlayerController(app)
    val fx = AudioEffectsManager(store)
    val lyrics = LyricsRepository(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Every track that starts playing goes into "Recently played".
        player.onTrackStarted = { track -> scope.launch { store.addRecent(track) } }
    }
}

class SonaraApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
