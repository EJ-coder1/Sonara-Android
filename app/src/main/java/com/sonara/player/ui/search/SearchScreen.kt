package com.sonara.player.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.CatalogueState
import com.sonara.player.ui.Moods
import com.sonara.player.data.JamendoAlbum
import com.sonara.player.data.JamendoArtist
import com.sonara.player.data.JamendoPlaylist
import com.sonara.player.data.GlobalCatalogState
import com.sonara.player.data.GlobalCatalogTrack
import com.sonara.player.ui.OnlineState
import com.sonara.player.ui.SearchViewModel
import com.sonara.player.ui.SongsState
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.MessageCard
import com.sonara.player.ui.components.SectionHeader
import com.sonara.player.ui.components.ShimmerRow
import com.sonara.player.ui.components.TrackRow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Surface
import coil.compose.AsyncImage

@Composable
fun SearchScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    vm: SearchViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val showMessage = com.sonara.player.ui.components.LocalMessage.current

    val query by vm.query.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val onlineAlbums by vm.onlineAlbums.collectAsStateWithLifecycle()
    val onlineArtists by vm.onlineArtists.collectAsStateWithLifecycle()
    val onlinePlaylists by vm.onlinePlaylists.collectAsStateWithLifecycle()
    val worldwideCatalog by vm.worldwideCatalog.collectAsStateWithLifecycle()
    val songsState by appVm.songs.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()
    val history by appVm.searchHistory.collectAsStateWithLifecycle()

    // "Go to artist / album" from the track menu pre-fills the search box.
    val request by appVm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let {
            vm.setQuery(it)
            appVm.consumeSearchRequest()
        }
    }

    val q = query.trim()
    val songs = (songsState as? SongsState.Ready)?.songs.orEmpty()
    val localMatches = remember(songs, q) {
        if (q.length < 2) emptyList()
        else songs.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }.take(50)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "title") {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item(key = "field") {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Songs, artists or albums") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    appVm.addSearch(query)
                }),
            )
        }

        if (q.length < 2) {
            item(key = "hint") {
                SectionHeader(
                    "Start typing to search",
                    subtitle = "Search your phone, licensed independent music, and worldwide store catalogs.",
                )
            }
            if (history.isNotEmpty()) {
                item(key = "history-header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Recent searches",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { appVm.clearSearchHistory() }) { Text("Clear") }
                    }
                }
                item(key = "history") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(history, key = { it }) { term ->
                            AssistChip(
                                onClick = { vm.setQuery(term) },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                label = { Text(term) },
                            )
                        }
                    }
                }
            }
            if (vm.isConfigured) {
                item(key = "suggestions") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Moods, key = { it.label }) { mood ->
                            AssistChip(onClick = { vm.setQuery(mood.tag) }, label = { Text(mood.label) })
                        }
                    }
                }
            }
        } else {
            // ----- official worldwide music catalogs and provider handoffs -----
            item(key = "worldwide-services-title") {
                SectionHeader("Search worldwide music", subtitle = "Open results in the official music service")
            }
            item(key = "worldwide-services") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf("YouTube Music", "Spotify", "Apple Music")) { service ->
                        AssistChip(
                            onClick = {
                                val encoded = Uri.encode(q)
                                val url = when (service) {
                                    "YouTube Music" -> "https://music.youtube.com/search?q=$encoded"
                                    "Spotify" -> "https://open.spotify.com/search/$encoded"
                                    else -> "https://music.apple.com/search?term=$encoded"
                                }
                                openMusicUrl(context, url) { showMessage("Couldn't open $service. Check that a browser is installed.") }
                            },
                            label = { Text(service) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                        )
                    }
                }
            }
            item(key = "worldwide-catalog-title") {
                SectionHeader(
                    "Worldwide catalog",
                    subtitle = "Apple/iTunes song listings from PH, US, UK, Korea and your device storefront. Full playback opens the official store.",
                )
            }
            when (val global = worldwideCatalog) {
                GlobalCatalogState.Idle, GlobalCatalogState.Loading -> item(key = "worldwide-catalog-loading") { ShimmerRow() }
                is GlobalCatalogState.Error -> item(key = "worldwide-catalog-error") {
                    MessageCard(global.message, actionLabel = "Retry", onAction = vm::retry)
                }
                is GlobalCatalogState.Ready -> {
                    if (global.tracks.isEmpty()) {
                        item(key = "worldwide-catalog-empty") {
                            MessageCard("No store-catalog matches for \"$q\". Try another title or artist.", icon = Icons.Rounded.SearchOff)
                        }
                    } else {
                        items(global.tracks, key = { it.id }) { hit ->
                            ExternalCatalogRow(hit) {
                                openMusicUrl(context, hit.storeUrl) { showMessage("Couldn't open the official store link.") }
                            }
                        }
                    }
                }
            }

            // ----- on this phone -----
            if (localMatches.isNotEmpty()) {
                item(key = "local-header") { SectionHeader("On your phone") }
                items(localMatches, key = { "l-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        onClick = {
                            appVm.addSearch(q)
                            player.playQueue(localMatches, localMatches.indexOf(track))
                        },
                    )
                }
            }

            // ----- online -----
            if (vm.isConfigured) {
                item(key = "online-header") { SectionHeader("Free music online") }
                when (val s = online) {
                    OnlineState.Idle, OnlineState.Loading -> item(key = "online-loading") { ShimmerRow() }
                    is OnlineState.Error -> item(key = "online-error") {
                        MessageCard(s.message, actionLabel = "Retry", onAction = vm::retry)
                    }
                    is OnlineState.Ready ->
                        if (s.tracks.isEmpty()) {
                            item(key = "online-empty") {
                                MessageCard("No online results for \"$q\".", icon = Icons.Rounded.SearchOff)
                            }
                        } else {
                            items(s.tracks, key = { "o-" + it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    isCurrent = playerState.current?.id == track.id,
                                    onClick = {
                                        appVm.addSearch(q)
                                        player.playQueue(s.tracks, s.tracks.indexOf(track))
                                    },
                                )
                            }
                        }
                }

                when (val result = onlineAlbums) {
                    CatalogueState.Loading -> item(key = "album-search-loading") { SectionHeader("Albums online") }
                    is CatalogueState.Error -> item(key = "album-search-error") { MessageCard(result.message) }
                    is CatalogueState.Ready<JamendoAlbum> -> if (result.items.isNotEmpty()) {
                        item(key = "album-search-header") { SectionHeader("Albums online") }
                        items(result.items, key = { "sa-" + it.id }) { album ->
                            SearchCatalogueRow(album.name, album.artistName, album.imageUrl, "album") {
                                appVm.addSearch(q)
                                vm.playAlbum(album.id) { loaded -> loaded.onSuccess { tracks -> if (tracks.isNotEmpty()) player.playQueue(tracks, 0) else showMessage("No playable tracks were found in ${album.name}.") }.onFailure { showMessage(it.message ?: "Couldn't open this album.") } }
                            }
                        }
                    }
                }
                when (val result = onlineArtists) {
                    CatalogueState.Loading -> item(key = "artist-search-loading") { SectionHeader("Artists online") }
                    is CatalogueState.Error -> item(key = "artist-search-error") { MessageCard(result.message) }
                    is CatalogueState.Ready<JamendoArtist> -> if (result.items.isNotEmpty()) {
                        item(key = "artist-search-header") { SectionHeader("Artists online") }
                        items(result.items, key = { "sr-" + it.id }) { artist -> SearchCatalogueRow(artist.name, "Independent artist", artist.imageUrl, "artist") { appVm.addSearch(q); vm.playArtist(artist.id) { loaded -> loaded.onSuccess { tracks -> if (tracks.isNotEmpty()) player.playQueue(tracks, 0) else showMessage("No playable tracks were found for ${artist.name}.") }.onFailure { showMessage(it.message ?: "Couldn't open this artist.") } } } }
                    }
                }
                when (val result = onlinePlaylists) {
                    CatalogueState.Loading -> item(key = "playlist-search-loading") { SectionHeader("Playlists online") }
                    is CatalogueState.Error -> item(key = "playlist-search-error") { MessageCard(result.message) }
                    is CatalogueState.Ready<JamendoPlaylist> -> if (result.items.isNotEmpty()) {
                        item(key = "playlist-search-header") { SectionHeader("Playlists online") }
                        items(result.items, key = { "sp-" + it.id }) { playlist -> SearchCatalogueRow(playlist.name, "By ${playlist.creatorName}", null, "playlist") { appVm.addSearch(q); vm.playPlaylist(playlist.id) { loaded -> loaded.onSuccess { tracks -> if (tracks.isNotEmpty()) player.playQueue(tracks, 0) else showMessage("This playlist has no playable tracks right now.") }.onFailure { showMessage(it.message ?: "Couldn't open this playlist.") } } } }
                    }
                }
            } else if (localMatches.isEmpty()) {
                item(key = "none") {
                    MessageCard("Jamendo is not configured. Worldwide catalog results are shown above; add JAMENDO_CLIENT_ID to enable in-app independent-music streaming.", icon = Icons.Rounded.SearchOff)
                }
            }
        }
    }
}


private fun openMusicUrl(context: android.content.Context, url: String, onFailure: () -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { onFailure() }
}

@Composable
private fun ExternalCatalogRow(track: GlobalCatalogTrack, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                val duration = if (track.durationMs > 0L) {
                    val seconds = track.durationMs / 1_000L
                    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                } else null
                Text(
                    listOf(track.artist, track.album.takeIf { it.isNotBlank() }, track.storefront, duration)
                        .filterNotNull().joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text("Open in Apple Music / iTunes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open ${track.title} in Apple Music", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SearchCatalogueRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    kind: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    AsyncImage(model = imageUrl, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val icon = when (kind) {
                        "artist" -> Icons.Rounded.Person
                        "album" -> Icons.Rounded.Album
                        else -> Icons.Rounded.LibraryMusic
                    }
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
