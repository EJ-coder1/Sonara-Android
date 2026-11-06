package com.sonara.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonara.player.SonaraApp
import com.sonara.player.data.Accent
import com.sonara.player.data.MixBuilder
import com.sonara.player.data.Playlist
import com.sonara.player.data.ThemeMode
import com.sonara.player.data.Track
import com.sonara.player.data.UiPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface SongsState {
    data object NoPermission : SongsState
    data object Loading : SongsState
    data class Ready(val songs: List<Track>) : SongsState
    data class Error(val message: String) : SongsState
}

sealed interface OnlineState {
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Ready(val tracks: List<Track>) : OnlineState
    data class Error(val message: String) : OnlineState
}

private fun Throwable.friendlyMessage(): String = when (this) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Can't reach the music service. Check your internet connection."
    is IOException -> "The music service had a problem. Please try again."
    else -> "Something went wrong. Please try again."
}

/** App-wide state: local songs, favorites, playlists, recents and settings. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SonaraApp).container
    private val store = container.store

    private val _songs = MutableStateFlow<SongsState>(SongsState.Loading)
    val songs: StateFlow<SongsState> = _songs.asStateFlow()

    val favorites: StateFlow<List<Track>> =
        store.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<Track>> =
        store.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists: StateFlow<List<Playlist>> =
        store.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** false until saved playlists have been read from disk. */
    val libraryLoaded: StateFlow<Boolean> =
        store.playlists.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val themeMode: StateFlow<ThemeMode> =
        store.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> =
        store.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val uiPrefs: StateFlow<UiPrefs> =
        store.uiPrefs.stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefs())
    /** null until loaded. */
    val batteryHintShown: StateFlow<Boolean?> =
        store.batteryHintShown.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _searchRequest = MutableStateFlow<String?>(null)
    /** "Go to artist / album" asks the Search tab to show this text. */
    val searchRequest: StateFlow<String?> = _searchRequest.asStateFlow()

    /** null until the saved value has loaded (avoids flashing the welcome screen). */
    val onboardingDone: StateFlow<Boolean?> =
        store.onboardingDone.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            _songs.value = SongsState.NoPermission
        } else if (_songs.value !is SongsState.Ready) {
            refreshSongs()
        }
    }

    fun refreshSongs() {
        viewModelScope.launch {
            if (_songs.value !is SongsState.Ready) _songs.value = SongsState.Loading
            try {
                _songs.value = SongsState.Ready(container.local.loadAll())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _songs.value = SongsState.NoPermission
            } catch (e: Exception) {
                _songs.value = SongsState.Error("Couldn't read the music on this phone.")
            }
        }
    }

    fun toggleFavorite(track: Track) = viewModelScope.launch { store.toggleFavorite(track) }
    fun createPlaylist(name: String, first: Track? = null) = viewModelScope.launch { store.createPlaylist(name, first) }
    fun renamePlaylist(id: String, name: String) = viewModelScope.launch { store.renamePlaylist(id, name) }
    fun deletePlaylist(id: String) = viewModelScope.launch { store.deletePlaylist(id) }
    fun removeFromPlaylist(playlistId: String, trackId: String) =
        viewModelScope.launch { store.removeFromPlaylist(playlistId, trackId) }

    /** [onResult] gets true when added, false if it was already in the playlist. */
    fun addToPlaylist(playlistId: String, track: Track, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(store.addToPlaylist(playlistId, track)) }
    }

    fun clearRecents() = viewModelScope.launch { store.clearRecents() }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { store.setDynamicColor(enabled) }
    fun setAmoled(on: Boolean) = viewModelScope.launch { store.setAmoled(on) }
    fun setAccent(accent: Accent) = viewModelScope.launch { store.setAccent(accent) }
    fun setFluidBackdrop(on: Boolean) = viewModelScope.launch { store.setFluidBackdrop(on) }
    fun setWavySeekBar(on: Boolean) = viewModelScope.launch { store.setWavySeekBar(on) }
    fun setLyricsEnabled(on: Boolean) = viewModelScope.launch { store.setLyricsEnabled(on) }
    fun setLyricsScale(percent: Int) = viewModelScope.launch { store.setLyricsScale(percent) }
    fun setLyricsOffset(ms: Int) = viewModelScope.launch { store.setLyricsOffset(ms) }
    fun markBatteryHintShown() = viewModelScope.launch { store.setBatteryHintShown() }
    fun clearLyricsCache() = container.lyrics.clearCache()

    fun requestSearch(text: String) { _searchRequest.value = text }
    fun consumeSearchRequest() { _searchRequest.value = null }

    /**
     * Builds a queue that starts with [seed]. Local songs never leave the phone; an online seed
     * may add more songs by the same artist from Jamendo.
     */
    fun startMix(seed: Track, onReady: (List<Track>) -> Unit) {
        viewModelScope.launch {
            val library = (songs.value as? SongsState.Ready)?.songs.orEmpty()
            val extra = if (seed.isOnline && container.jamendo.isConfigured) {
                container.jamendo.search(seed.artist, 30).getOrNull().orEmpty()
            } else emptyList()
            onReady(MixBuilder.build(seed, library, extra))
        }
    }

    fun finishOnboarding() = viewModelScope.launch { store.setOnboardingDone() }
    fun deleteAllData(done: () -> Unit) {
        viewModelScope.launch {
            store.clearAll()
            // clearAll() also resets onboarding; keep the user in the app instead of replaying the welcome screen.
            store.setOnboardingDone()
            done()
        }
    }
}

data class Mood(val label: String, val tag: String)

val Moods = listOf(
    Mood("Chill", "chillout"),
    Mood("Focus", "ambient"),
    Mood("Energy", "energetic"),
    Mood("Happy", "happy"),
    Mood("Sad", "sad"),
    Mood("Sleep", "sleep"),
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SonaraApp).container.jamendo

    val isConfigured: Boolean get() = repo.isConfigured

    private val _trending = MutableStateFlow<OnlineState>(OnlineState.Loading)
    val trending: StateFlow<OnlineState> = _trending.asStateFlow()

    private val _newReleases = MutableStateFlow<OnlineState>(OnlineState.Loading)
    val newReleases: StateFlow<OnlineState> = _newReleases.asStateFlow()

    private val _selectedMood = MutableStateFlow<Mood?>(null)
    val selectedMood: StateFlow<Mood?> = _selectedMood.asStateFlow()

    private val _moodTracks = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val moodTracks: StateFlow<OnlineState> = _moodTracks.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        if (!repo.isConfigured) {
            _trending.value = OnlineState.Idle
            _newReleases.value = OnlineState.Idle
            return
        }
        load(_trending) { repo.trending() }
        load(_newReleases) { repo.newReleases() }
        _selectedMood.value?.let { selectMood(it) }
    }

    fun retryTrending() = load(_trending) { repo.trending() }
    fun retryNewReleases() = load(_newReleases) { repo.newReleases() }

    fun selectMood(mood: Mood?) {
        _selectedMood.value = mood
        if (mood == null) {
            _moodTracks.value = OnlineState.Idle
            return
        }
        load(_moodTracks) { repo.byMood(mood.tag) }
    }

    private fun load(target: MutableStateFlow<OnlineState>, call: suspend () -> Result<List<Track>>) {
        viewModelScope.launch {
            target.value = OnlineState.Loading
            val result = call()
            target.value = result.fold(
                onSuccess = { OnlineState.Ready(it) },
                onFailure = { OnlineState.Error(it.friendlyMessage()) },
            )
        }
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SonaraApp).container.jamendo

    val isConfigured: Boolean get() = repo.isConfigured

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _online = MutableStateFlow<OnlineState>(OnlineState.Idle)
    val online: StateFlow<OnlineState> = _online.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(450)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { q -> runSearch(q) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun retry() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
    }

    private suspend fun runSearch(q: String) {
        if (q.length < 2 || !repo.isConfigured) {
            _online.value = OnlineState.Idle
            return
        }
        _online.value = OnlineState.Loading
        _online.value = repo.search(q).fold(
            onSuccess = { OnlineState.Ready(it) },
            onFailure = { OnlineState.Error(it.friendlyMessage()) },
        )
    }
}
