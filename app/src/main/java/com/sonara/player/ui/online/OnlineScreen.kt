package com.sonara.player.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sonara.player.data.JamendoAlbum
import com.sonara.player.data.JamendoArtist
import com.sonara.player.data.JamendoPlaylist
import com.sonara.player.data.Track
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.CatalogueState
import com.sonara.player.ui.Mood
import com.sonara.player.ui.Moods
import com.sonara.player.ui.OnlineViewModel
import com.sonara.player.ui.PagedTracksState
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.MessageCard
import com.sonara.player.ui.components.SectionHeader
import com.sonara.player.ui.components.ShimmerRow
import com.sonara.player.ui.components.TrackCardRow

private data class Genre(val name: String, val tag: String)

private val genres = listOf(
    Genre("Pop", "pop"), Genre("Electronic", "electronic"), Genre("Rock", "rock"),
    Genre("Hip-Hop", "hiphop"), Genre("Jazz", "jazz"), Genre("Ambient", "ambient"),
    Genre("Indie", "indie"), Genre("Acoustic", "acoustic"), Genre("Classical", "classical"),
)

private val radioMoods = listOf(
    Mood("Chill Radio", "chillout"),
    Mood("Focus Radio", "ambient"),
    Mood("Energy Radio", "energetic"),
)

@Composable
fun OnlineScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onOpenSearch: () -> Unit,
    onlineVm: OnlineViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val showMessage = LocalMessage.current
    val recents by appVm.recents.collectAsStateWithLifecycle()
    val trending by onlineVm.trending.collectAsStateWithLifecycle()
    val latest by onlineVm.latest.collectAsStateWithLifecycle()
    val filipino by onlineVm.filipino.collectAsStateWithLifecycle()
    val forYou by onlineVm.forYou.collectAsStateWithLifecycle()
    val selectedMood by onlineVm.mood.collectAsStateWithLifecycle()
    val moodTracks by onlineVm.moodTracks.collectAsStateWithLifecycle()
    val albums by onlineVm.albums.collectAsStateWithLifecycle()
    val artists by onlineVm.artists.collectAsStateWithLifecycle()
    val filipinoArtists by onlineVm.filipinoArtists.collectAsStateWithLifecycle()
    val playlists by onlineVm.playlists.collectAsStateWithLifecycle()

    val onlineRecents = remember(recents) { recents.filter { it.isOnline }.take(20) }
    val recommendationSeed = onlineRecents.firstOrNull()
    LaunchedEffect(recommendationSeed?.id) { onlineVm.loadForYou(recommendationSeed) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 30.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SONARA ONLINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Discover", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Find your next favorite track.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onlineVm::refresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh discovery")
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onOpenSearch),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Search songs, artists and albums", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            DiscoveryHero(
                title = "A world of independent music",
                subtitle = "Explore fresh tracks, artists and playlists from Jamendo.",
                action = onOpenSearch,
            )
        }

        if (!onlineVm.isConfigured) {
            item {
                MessageCard(
                    "Online discovery is disabled. Add JAMENDO_CLIENT_ID to your GitHub Actions secret and rebuild Sonara.",
                    actionLabel = "Retry",
                    onAction = onlineVm::refresh,
                )
            }
        } else {
            if (onlineRecents.isNotEmpty()) {
                item { SectionHeader("Recently played online", subtitle = "Pick up where you left off") }
                item { TrackCardRow(onlineRecents, onPlay = { player.playQueue(onlineRecents, it) }) }
            }

            item {
                SectionHeader(
                    "For you",
                    subtitle = if (recommendationSeed != null) "More music like ${recommendationSeed.title}" else "A starting mix from this week's discoveries",
                )
            }
            item {
                TrackSection(
                    state = if (recommendationSeed == null && forYou.tracks.isEmpty()) trending else forYou,
                    onRetry = { onlineVm.loadForYou(recommendationSeed) },
                    onLoadMore = null,
                    onPlay = { tracks, index -> player.playQueue(tracks, index) },
                )
            }

            item { SectionHeader("Trending this week", subtitle = "Weekly favorites from independent artists") }
            item {
                TrackSection(
                    state = trending,
                    onRetry = onlineVm::loadTrending,
                    onLoadMore = onlineVm::loadMoreTrending,
                    onPlay = { tracks, index -> player.playQueue(tracks, index) },
                )
            }

            item { SectionHeader("New releases", subtitle = "Recently released tracks and singles") }
            item {
                TrackSection(
                    state = latest,
                    onRetry = onlineVm::loadLatest,
                    onLoadMore = onlineVm::loadMoreLatest,
                    onPlay = { tracks, index -> player.playQueue(tracks, index) },
                )
            }

            item {
                SectionHeader("Filipino & Philippines-based", subtitle = "Tracks charted for artists who declare the Philippines as their country")
            }
            item {
                TrackSection(
                    state = filipino,
                    onRetry = onlineVm::loadFilipino,
                    onLoadMore = onlineVm::loadMoreFilipino,
                    onPlay = { tracks, index -> player.playQueue(tracks, index) },
                    emptyMessage = "Jamendo has no Philippines chart results available right now.",
                )
            }
            item {
                when (val state = filipinoArtists) {
                    CatalogueState.Loading -> ShimmerRow(count = 3)
                    is CatalogueState.Error -> MessageCard(state.message)
                    is CatalogueState.Ready<JamendoArtist> -> if (state.items.isNotEmpty()) {
                        ArtistRow(state.items, onClick = { artist ->
                            onlineVm.playArtist(artist.id) { result ->
                                result.onSuccess { tracks ->
                                    if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
                                    else showMessage("No playable tracks were found for ${artist.name}.")
                                }.onFailure { showMessage(it.message ?: "Couldn't load artist tracks.") }
                            }
                        })
                    }
                }
            }

            item { SectionHeader("Pick a mood", subtitle = "Build a mix around how you feel") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Moods, key = { it.label }) { mood ->
                        FilterChip(
                            selected = selectedMood == mood,
                            onClick = { onlineVm.loadMood(if (selectedMood == mood) null else mood) },
                            label = { Text(mood.label) },
                        )
                    }
                }
            }
            if (selectedMood != null) {
                item { SectionHeader("${selectedMood.label} mix", subtitle = "Tracks matched by Jamendo mood tags") }
                item {
                    TrackSection(
                        state = moodTracks,
                        onRetry = { onlineVm.loadMood(selectedMood) },
                        onLoadMore = null,
                        onPlay = { tracks, index -> player.playQueue(tracks, index) },
                    )
                }
            }

            item { SectionHeader("Featured playlists", subtitle = "Collections picked from Jamendo's hot selection") }
            item {
                when (val state = playlists) {
                    CatalogueState.Loading -> ShimmerRow(count = 3)
                    is CatalogueState.Error -> MessageCard(state.message, actionLabel = "Retry", onAction = onlineVm::refresh)
                    is CatalogueState.Ready<JamendoPlaylist> -> PlaylistRow(state.items) { playlist ->
                        onlineVm.playPlaylist(playlist.id) { result ->
                            result.onSuccess { tracks ->
                                if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
                                else showMessage("This playlist has no playable tracks right now.")
                            }.onFailure { showMessage(it.message ?: "Couldn't load playlist.") }
                        }
                    }
                }
            }

            item { SectionHeader("Mood radio mixes", subtitle = "One-tap queues built from tracks that match your mood") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(radioMoods, key = { it.label }) { mood ->
                        RadioTile(mood.label, mood.tag) {
                            onlineVm.playRadio(mood) { result ->
                                result.onSuccess { tracks ->
                                    if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
                                    else showMessage("No tracks were available for this radio mix.")
                                }.onFailure { showMessage(it.message ?: "Couldn't start this mix.") }
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Popular albums", subtitle = "Full releases worth exploring") }
            item {
                when (val state = albums) {
                    CatalogueState.Loading -> ShimmerRow(count = 3)
                    is CatalogueState.Error -> MessageCard(state.message, actionLabel = "Retry", onAction = onlineVm::refresh)
                    is CatalogueState.Ready<JamendoAlbum> -> AlbumRow(state.items) { album ->
                        onlineVm.playAlbum(album.id) { result ->
                            result.onSuccess { tracks ->
                                if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
                                else showMessage("No playable tracks were found in ${album.name}.")
                            }.onFailure { showMessage(it.message ?: "Couldn't load album tracks.") }
                        }
                    }
                }
            }

            item { SectionHeader("Popular artists", subtitle = "Discover independent artists this week") }
            item {
                when (val state = artists) {
                    CatalogueState.Loading -> ShimmerRow(count = 3)
                    is CatalogueState.Error -> MessageCard(state.message, actionLabel = "Retry", onAction = onlineVm::refresh)
                    is CatalogueState.Ready<JamendoArtist> -> ArtistRow(state.items) { artist ->
                        onlineVm.playArtist(artist.id) { result ->
                            result.onSuccess { tracks ->
                                if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
                                else showMessage("No playable tracks were found for ${artist.name}.")
                            }.onFailure { showMessage(it.message ?: "Couldn't load artist tracks.") }
                        }
                    }
                }
            }

            item { SectionHeader("Explore genres", subtitle = "Find music by the sound you like") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(genres, key = { it.tag }) { genre ->
                        FilterChip(
                            selected = false,
                            onClick = { onlineVm.loadMood(Mood(genre.name, genre.tag)) },
                            label = { Text(genre.name) },
                        )
                    }
                }
            }

            item {
                Text(
                    "Music supplied by Jamendo. Availability, playback and download permissions depend on each artist's track and license settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryHero(title: String, subtitle: String, action: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(184.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF493078), Color(0xFF173D50), Color(0xFF172A37))))
            .clickable(onClick = action),
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(20.dp).fillMaxWidth(0.86f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Explore, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("YOUR NEXT LISTEN STARTS HERE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .82f))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .82f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Explore music", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TrackSection(
    state: PagedTracksState,
    onRetry: () -> Unit,
    onLoadMore: (() -> Unit)?,
    onPlay: (List<Track>, Int) -> Unit,
    emptyMessage: String = "Nothing to play here yet.",
) {
    when {
        state.isLoading -> ShimmerRow()
        state.error != null && state.tracks.isEmpty() -> MessageCard(state.error, actionLabel = "Retry", onAction = onRetry)
        state.tracks.isEmpty() -> MessageCard(emptyMessage, icon = Icons.Rounded.MusicNote)
        else -> {
            TrackCardRow(state.tracks, onPlay = { onPlay(state.tracks, it) })
            if (onLoadMore != null && !state.endReached) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Button(
                            onClick = onLoadMore,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Load more", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (state.error != null && state.tracks.isNotEmpty()) {
                Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun AlbumRow(albums: List<JamendoAlbum>, onClick: (JamendoAlbum) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album ->
            CatalogueTile(album.name, album.artistName, album.imageUrl, Icons.Rounded.Album) { onClick(album) }
        }
    }
}

@Composable
private fun ArtistRow(artists: List<JamendoArtist>, onClick: (JamendoArtist) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { it.id }) { artist ->
            CatalogueTile(artist.name, listOfNotNull(artist.city, artist.countryCode).joinToString(", ").ifBlank { "Independent artist" }, artist.imageUrl, Icons.Rounded.MusicNote, circular = true) { onClick(artist) }
        }
    }
}

@Composable
private fun PlaylistRow(playlists: List<JamendoPlaylist>, onClick: (JamendoPlaylist) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            CatalogueTile(playlist.name, "By ${playlist.creatorName}", null, Icons.Rounded.PlayArrow) { onClick(playlist) }
        }
    }
}

@Composable
private fun CatalogueTile(
    title: String,
    subtitle: String,
    image: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    circular: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(142.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(142.dp).clip(if (circular) RoundedCornerShape(50) else RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                AsyncImage(model = image, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RadioTile(title: String, tag: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(220.dp).height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Play a mood-based mix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
