package com.sonara.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonara.player.SonaraApp
import com.sonara.player.data.JamendoAlbum
import com.sonara.player.data.JamendoArtist
import com.sonara.player.data.JamendoPlaylist
import com.sonara.player.data.LocationRepository
import com.sonara.player.data.MusicRegion
import com.sonara.player.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** State for a horizontally browsable section with explicit offset-based pagination. */
data class PagedTracksState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val nextOffset: Int = 0,
    val error: String? = null,
)

sealed interface CatalogueState<out T> {
    data object Loading : CatalogueState<Nothing>
    data class Ready<T>(val items: List<T>) : CatalogueState<T>
    data class Error(val message: String) : CatalogueState<Nothing>
}

private fun Throwable.onlineMessage(): String = when (this) {
    is UnknownHostException, is ConnectException, is SocketTimeoutException ->
        "Can't reach Jamendo. Check your internet connection and try again."
    is IOException -> "Jamendo couldn't complete this request. Try again in a moment."
    else -> "Something went wrong while loading music. Please try again."
}

class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as SonaraApp).container.jamendo
    private val locationRepository = LocationRepository(app)
    private val _musicRegion = MutableStateFlow(MusicRegion.fromDeviceRegion())
    val musicRegion: StateFlow<MusicRegion> = _musicRegion.asStateFlow()
    val isConfigured: Boolean get() = repository.isConfigured

    private val pageSize = 30

    private val _trending = MutableStateFlow(PagedTracksState())
    val trending: StateFlow<PagedTracksState> = _trending.asStateFlow()

    private val _latest = MutableStateFlow(PagedTracksState())
    val latest: StateFlow<PagedTracksState> = _latest.asStateFlow()

    private val _regionalTracks = MutableStateFlow(PagedTracksState())
    val regionalTracks: StateFlow<PagedTracksState> = _regionalTracks.asStateFlow()

    private val _forYou = MutableStateFlow(PagedTracksState(isLoading = false))
    val forYou: StateFlow<PagedTracksState> = _forYou.asStateFlow()

    private val _mood = MutableStateFlow<Mood?>(null)
    val mood: StateFlow<Mood?> = _mood.asStateFlow()

    private val _moodTracks = MutableStateFlow<PagedTracksState>(PagedTracksState(isLoading = false))
    val moodTracks: StateFlow<PagedTracksState> = _moodTracks.asStateFlow()

    private val _albums = MutableStateFlow<CatalogueState<JamendoAlbum>>(CatalogueState.Loading)
    val albums: StateFlow<CatalogueState<JamendoAlbum>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<CatalogueState<JamendoArtist>>(CatalogueState.Loading)
    val artists: StateFlow<CatalogueState<JamendoArtist>> = _artists.asStateFlow()

    private val _regionalArtists = MutableStateFlow<CatalogueState<JamendoArtist>>(CatalogueState.Loading)
    val regionalArtists: StateFlow<CatalogueState<JamendoArtist>> = _regionalArtists.asStateFlow()

    private val _playlists = MutableStateFlow<CatalogueState<JamendoPlaylist>>(CatalogueState.Loading)
    val playlists: StateFlow<CatalogueState<JamendoPlaylist>> = _playlists.asStateFlow()

    init {
        if (isConfigured) {
            loadTrending()
            loadLatest()
            loadRegionalTracks()
            loadAlbums()
            loadArtists()
            loadRegionalArtists()
            loadPlaylists()
        } else {
            _trending.value = PagedTracksState(isLoading = false, error = "Add JAMENDO_CLIENT_ID to enable online discovery.")
            _latest.value = _trending.value
            _regionalTracks.value = _trending.value
            _albums.value = CatalogueState.Error("Online music is not configured.")
            _artists.value = CatalogueState.Error("Online music is not configured.")
            _regionalArtists.value = CatalogueState.Error("Online music is not configured.")
            _playlists.value = CatalogueState.Error("Online music is not configured.")
        }
    }

    fun refresh() {
        loadTrending()
        loadLatest()
        loadRegionalTracks()
        loadAlbums()
        loadArtists()
        loadRegionalArtists()
        loadPlaylists()
    }

    fun loadTrending() = loadPage(_trending, append = false) { limit, offset -> repository.trending(limit, offset) }
    fun loadMoreTrending() = loadPage(_trending, append = true) { limit, offset -> repository.trending(limit, offset) }

    fun loadLatest() = loadPage(_latest, append = false) { limit, offset -> repository.newReleases(limit, offset) }
    fun loadMoreLatest() = loadPage(_latest, append = true) { limit, offset -> repository.newReleases(limit, offset) }

    fun loadRegionalTracks() {
        val country = _musicRegion.value.countryIso3
        loadPage(_regionalTracks, append = false) { limit, offset -> repository.regionalTracks(country, limit, offset) }
    }
    fun loadMoreRegionalTracks() {
        val country = _musicRegion.value.countryIso3
        loadPage(_regionalTracks, append = true) { limit, offset -> repository.regionalTracks(country, limit, offset) }
    }

    fun loadLocationRecommendations(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val detected = locationRepository.detectMusicRegion()
            if (detected == null) {
                onResult(Result.failure(IllegalStateException("Sonara couldn't determine your country. Check that Location is enabled, then try again.")))
                return@launch
            }
            _musicRegion.value = detected
            loadRegionalTracks()
            loadRegionalArtists()
            onResult(Result.success(detected.displayName))
        }
    }

    fun loadForYou(seed: Track?) {
        if (seed == null || !seed.isOnline) {
            _forYou.value = _trending.value.copy(isLoading = false)
            return
        }
        loadPage(_forYou, append = false) { limit, offset -> repository.similar(seed.id, limit, offset) }
    }

    fun loadMood(mood: Mood?) {
        _mood.value = mood
        if (mood == null) {
            _moodTracks.value = PagedTracksState(isLoading = false)
        } else {
            loadPage(_moodTracks, append = false) { limit, offset -> repository.byMood(mood.tag, limit, offset) }
        }
    }

    fun playRadio(mood: Mood, onResult: (Result<List<Track>>) -> Unit) {
        viewModelScope.launch { onResult(repository.byMood(mood.tag, limit = 60)) }
    }

    fun playPlaylist(id: String, onResult: (Result<List<Track>>) -> Unit) {
        viewModelScope.launch { onResult(repository.playlistTracks(id)) }
    }

    fun playAlbum(id: String, onResult: (Result<List<Track>>) -> Unit) {
        viewModelScope.launch { onResult(repository.albumTracks(id)) }
    }

    fun playArtist(id: String, onResult: (Result<List<Track>>) -> Unit) {
        viewModelScope.launch { onResult(repository.artistTracks(id)) }
    }

    fun retry(section: DiscoveryTrackSection) {
        when (section) {
            DiscoveryTrackSection.TRENDING -> loadTrending()
            DiscoveryTrackSection.LATEST -> loadLatest()
            DiscoveryTrackSection.REGIONAL -> loadRegionalTracks()
            DiscoveryTrackSection.FOR_YOU -> _forYou.value = _forYou.value.copy(error = null)
        }
    }

    private fun loadAlbums() = loadCatalogue(_albums) { repository.popularAlbums(24) }
    private fun loadArtists() = loadCatalogue(_artists) { repository.popularArtists(24) }
    private fun loadRegionalArtists() {
        val country = _musicRegion.value.countryIso3
        loadCatalogue(_regionalArtists) { repository.regionalArtists(country, 24) }
    }
    private fun loadPlaylists() = loadCatalogue(_playlists) { repository.featuredPlaylists(16) }

    private fun <T> loadCatalogue(
        target: MutableStateFlow<CatalogueState<T>>,
        call: suspend () -> Result<List<T>>,
    ) {
        viewModelScope.launch {
            target.value = CatalogueState.Loading
            val result = call()
            target.value = result.fold(
                onSuccess = { CatalogueState.Ready(it) },
                onFailure = { CatalogueState.Error(it.onlineMessage()) },
            )
        }
    }

    private fun loadPage(
        target: MutableStateFlow<PagedTracksState>,
        append: Boolean,
        call: suspend (limit: Int, offset: Int) -> Result<List<Track>>,
    ) {
        viewModelScope.launch {
            val before = target.value
            if (append && (before.isLoadingMore || before.endReached)) return@launch
            val offset = if (append) before.nextOffset else 0
            target.value = if (append) before.copy(isLoadingMore = true, error = null)
            else PagedTracksState(isLoading = true)
            val result = call(pageSize, offset)
            target.value = result.fold(
                onSuccess = { page ->
                    val combined = if (append) (before.tracks + page).distinctBy { it.id } else page.distinctBy { it.id }
                    PagedTracksState(
                        tracks = combined,
                        isLoading = false,
                        isLoadingMore = false,
                        endReached = page.size < pageSize,
                        nextOffset = offset + page.size,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (append) before.copy(isLoading = false, isLoadingMore = false, error = error.onlineMessage())
                    else PagedTracksState(isLoading = false, error = error.onlineMessage())
                },
            )
        }
    }
}

enum class DiscoveryTrackSection { TRENDING, LATEST, REGIONAL, FOR_YOU }
