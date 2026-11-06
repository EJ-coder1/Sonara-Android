package com.sonara.player.ui.home

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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sonara.player.BuildConfig
import com.sonara.player.data.Track
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.HomeViewModel
import com.sonara.player.ui.Moods
import com.sonara.player.ui.SongsState
import com.sonara.player.ui.components.Artwork
import com.sonara.player.ui.components.BrandGradient
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.LocalRequestAudioAccess
import com.sonara.player.ui.components.MessageCard
import com.sonara.player.ui.components.OnlineTrackRow
import com.sonara.player.ui.components.SectionHeader
import com.sonara.player.ui.components.TrackCardRow
import com.sonara.player.util.greeting
import com.sonara.player.util.todayLabel

@Composable
fun HomeScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenLibrary: () -> Unit,
    homeVm: HomeViewModel = viewModel(),
) {
    val player = LocalPlayer.current
    val requestAccess = LocalRequestAudioAccess.current

    val songsState by appVm.songs.collectAsStateWithLifecycle()
    val recents by appVm.recents.collectAsStateWithLifecycle()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val trending by homeVm.trending.collectAsStateWithLifecycle()
    val newReleases by homeVm.newReleases.collectAsStateWithLifecycle()
    val selectedMood by homeVm.selectedMood.collectAsStateWithLifecycle()
    val moodTracks by homeVm.moodTracks.collectAsStateWithLifecycle()

    val songs = (songsState as? SongsState.Ready)?.songs.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- greeting ----
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        todayLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }

        // ---- hero ----
        item(key = "hero") {
            val last = recents.firstOrNull()
            HeroCard(
                last = last,
                songsState = songsState,
                onResume = { player.playQueue(recents, 0) },
                onShuffleAll = { player.playQueue(songs, 0, shuffle = true) },
                onAllowAccess = requestAccess,
            )
        }

        // ---- quick picks ----
        item(key = "quick") {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        title = "Liked songs",
                        subtitle = "${favorites.size} saved",
                        icon = Icons.Rounded.Favorite,
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        title = "Playlists",
                        subtitle = "Your collections",
                        icon = Icons.Rounded.LibraryMusic,
                        onClick = onOpenLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile(
                        title = "Shuffle all",
                        subtitle = if (songs.isEmpty()) "No songs yet" else "${songs.size} songs",
                        icon = Icons.Rounded.Shuffle,
                        enabled = songs.isNotEmpty(),
                        onClick = { player.playQueue(songs, 0, shuffle = true) },
                        modifier = Modifier.weight(1f),
                    )
                    QuickTile(
                        title = "Recently added",
                        subtitle = "Newest on your phone",
                        icon = Icons.Rounded.Schedule,
                        enabled = songs.isNotEmpty(),
                        onClick = {
                            val newest = songs.sortedByDescending { it.addedAt }.take(100)
                            player.playQueue(newest, 0, shuffle = false)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---- recently played ----
        if (recents.isNotEmpty()) {
            item(key = "recents-header") { SectionHeader("Recently played") }
            item(key = "recents") {
                TrackCardRow(recents, onPlay = { player.playQueue(recents, it) })
            }
        }

        // ---- online ----
        if (homeVm.isConfigured) {
            item(key = "moods-header") {
                SectionHeader("Pick a mood", subtitle = "Free music matched to how you feel")
            }
            item(key = "moods") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Moods, key = { it.label }) { mood ->
                        FilterChip(
                            selected = selectedMood == mood,
                            onClick = { homeVm.selectMood(if (selectedMood == mood) null else mood) },
                            label = { Text(mood.label) },
                        )
                    }
                }
            }
            if (selectedMood != null) {
                item(key = "mood-tracks") {
                    OnlineTrackRow(
                        state = moodTracks,
                        onRetry = { homeVm.selectMood(selectedMood) },
                        onPlay = { list, i -> player.playQueue(list, i) },
                    )
                }
            }

            item(key = "trending-header") {
                SectionHeader("Trending this week", subtitle = "Free music from independent artists")
            }
            item(key = "trending") {
                OnlineTrackRow(
                    state = trending,
                    onRetry = homeVm::retryTrending,
                    onPlay = { list, i -> player.playQueue(list, i) },
                )
            }

            item(key = "new-header") { SectionHeader("New releases") }
            item(key = "new") {
                OnlineTrackRow(
                    state = newReleases,
                    onRetry = homeVm::retryNewReleases,
                    onPlay = { list, i -> player.playQueue(list, i) },
                )
            }

            item(key = "credit") {
                Text(
                    "Free music provided by Jamendo. Each track is shared by its artist under a Creative Commons license. Open a track's menu to see its license.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else if (BuildConfig.DEBUG) {
            item(key = "dev-note") {
                MessageCard("Online music is off. Add JAMENDO_CLIENT_ID to local.properties (see README), then rebuild.")
            }
        }
    }
}

@Composable
private fun HeroCard(
    last: Track?,
    songsState: SongsState,
    onResume: () -> Unit,
    onShuffleAll: () -> Unit,
    onAllowAccess: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(208.dp)
            .clip(shape)
            .background(BrandGradient)
    ) {
        if (last != null) {
            Artwork(last, Modifier.fillMaxSize(), corner = 0.dp, sizePx = 768)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.78f))
                        )
                    )
            )
        }

        val title: String
        val body: String
        val actionLabel: String
        val actionIcon: ImageVector
        val action: () -> Unit
        when {
            last != null -> {
                title = last.title
                body = last.artist
                actionLabel = "Resume"
                actionIcon = Icons.Rounded.PlayArrow
                action = onResume
            }
            songsState is SongsState.Ready && songsState.songs.isNotEmpty() -> {
                title = "Your music is ready"
                body = "${songsState.songs.size} songs found on this phone"
                actionLabel = "Shuffle my music"
                actionIcon = Icons.Rounded.Shuffle
                action = onShuffleAll
            }
            songsState is SongsState.NoPermission -> {
                title = "Play the music on your phone"
                body = "Allow access to your audio files. They never leave your device."
                actionLabel = "Allow access"
                actionIcon = Icons.Rounded.Lock
                action = onAllowAccess
            }
            else -> {
                title = "Find something new"
                body = "Browse free music below, or add songs to your phone."
                actionLabel = ""
                actionIcon = Icons.Rounded.PlayArrow
                action = {}
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (last != null) {
                Text(
                    "Continue listening",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionLabel.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.clickable(onClick = action),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    contentColor = Color(0xFF2A0B6B),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
