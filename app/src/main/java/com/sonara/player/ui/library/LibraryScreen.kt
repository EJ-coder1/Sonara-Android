package com.sonara.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.Dp
import com.sonara.player.data.downloads.DownloadItem
import com.sonara.player.data.downloads.DownloadStatus
import com.sonara.player.data.downloads.EnqueueResult
import com.sonara.player.ui.components.LocalDownloadedIds
import com.sonara.player.ui.components.LocalDownloads
import com.sonara.player.ui.components.LocalOfflinePrefs
import com.sonara.player.ui.components.Artwork
import com.sonara.player.util.formatBytes
import com.sonara.player.data.Track
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.SongsState
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.LocalRequestAudioAccess
import com.sonara.player.ui.components.MessageCard
import com.sonara.player.ui.components.TrackRow

@Composable
fun LibraryScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onOpenPlaylist: (String) -> Unit,
) {
    val player = LocalPlayer.current
    val requestAccess = LocalRequestAudioAccess.current
    val songsState by appVm.songs.collectAsStateWithLifecycle()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val playlists by appVm.playlists.collectAsStateWithLifecycle()
    val playerState by player.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    val tabs = listOf("Songs", "Liked", "Playlists", "Downloads")

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding() + 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (tab == 0) {
                IconButton(onClick = { appVm.refreshSongs() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Rescan music on this phone")
                }
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }

        val listBottom = padding.calculateBottomPadding() + 24.dp
        when (tab) {
            0 -> when (val s = songsState) {
                SongsState.NoPermission -> CenteredMessage {
                    MessageCard(
                        "Allow access to see the songs on your phone. They stay on your device.",
                        actionLabel = "Allow",
                        onAction = requestAccess,
                        icon = Icons.Rounded.Lock,
                    )
                }
                SongsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SongsState.Error -> CenteredMessage {
                    MessageCard(s.message, actionLabel = "Retry", onAction = { appVm.refreshSongs() })
                }
                is SongsState.Ready ->
                    if (s.songs.isEmpty()) CenteredMessage {
                        MessageCard(
                            "No music found on this phone. Add songs, then tap the refresh button.",
                            icon = Icons.Rounded.MusicOff,
                        )
                    } else TrackList(
                        tracks = s.songs,
                        currentId = playerState.current?.id,
                        bottom = listBottom,
                        showShuffleBar = true,
                    )
            }
            1 -> if (favorites.isEmpty()) CenteredMessage {
                MessageCard(
                    "Tap the heart on any song and it will show up here.",
                    icon = Icons.Rounded.FavoriteBorder,
                )
            } else TrackList(
                tracks = favorites,
                currentId = playerState.current?.id,
                bottom = listBottom,
                showShuffleBar = true,
                onDownloadAll = rememberDownloadAll(favorites),
            )
            2 -> LazyColumn(contentPadding = PaddingValues(bottom = listBottom)) {
                item(key = "new") {
                    FilledTonalButton(
                        onClick = { showNewPlaylist = true },
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New playlist")
                    }
                }
                if (playlists.isEmpty()) {
                    item(key = "empty") {
                        MessageCard(
                            "Playlists you create will appear here.",
                            icon = Icons.Rounded.LibraryMusic,
                        )
                    }
                }
                items(playlists, key = { it.id }) { pl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(pl.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.LibraryMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (pl.tracks.size == 1) "1 song" else "${pl.tracks.size} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            else -> DownloadsTab(bottom = listBottom, currentId = playerState.current?.id)
        }
    }

    if (showNewPlaylist) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { appVm.createPlaylist(it) },
            onDismiss = { showNewPlaylist = false },
        )
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp)) { content() }
}

/** A list of tracks with Play and Shuffle buttons on top. */
@Composable
fun TrackList(
    tracks: List<Track>,
    currentId: String?,
    bottom: androidx.compose.ui.unit.Dp,
    showShuffleBar: Boolean,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    /** When set, a download button is shown (online songs that can still be downloaded). */
    onDownloadAll: (() -> Unit)? = null,
    trailing: ((Track) -> (@Composable () -> Unit))? = null,
) {
    val player = LocalPlayer.current
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = bottom)) {
        if (header != null) item(key = "header") { header() }
        if (showShuffleBar) {
            item(key = "actions") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { player.playQueue(tracks, 0, shuffle = false) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = { player.playQueue(tracks, 0, shuffle = true) }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                    if (onDownloadAll != null) {
                        IconButton(onClick = onDownloadAll) {
                            Icon(Icons.Rounded.Download, contentDescription = "Download the online songs in this list")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (tracks.size == 1) "1 song" else "${tracks.size} songs",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrent = currentId == track.id,
                onClick = { player.playQueue(tracks, tracks.indexOf(track)) },
                trailing = trailing?.invoke(track),
            )
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim())
                    onDismiss()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Playlist page. The id "favorites" shows the Liked songs list. */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    padding: PaddingValues,
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val player = LocalPlayer.current
    val message = LocalMessage.current
    val playerState by player.state.collectAsStateWithLifecycle()
    val favorites by appVm.favorites.collectAsStateWithLifecycle()
    val playlists by appVm.playlists.collectAsStateWithLifecycle()
    val libraryLoaded by appVm.libraryLoaded.collectAsStateWithLifecycle()

    val isFavorites = playlistId == FAVORITES_ID
    val playlist = playlists.firstOrNull { it.id == playlistId }
    val tracks = if (isFavorites) favorites else playlist?.tracks.orEmpty()
    val name = if (isFavorites) "Liked songs" else playlist?.name.orEmpty()

    // If the playlist was deleted, leave the page.
    LaunchedEffect(libraryLoaded, isFavorites, playlist == null) {
        if (libraryLoaded && !isFavorites && playlist == null) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!isFavorites) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menuOpen = false; confirmDelete = true })
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            CenteredMessage {
                MessageCard(
                    if (isFavorites) "Tap the heart on any song and it will show up here."
                    else "This playlist is empty. Use a song's menu and choose Add to playlist.",
                    icon = if (isFavorites) Icons.Rounded.FavoriteBorder else Icons.Rounded.LibraryMusic,
                )
            }
        } else {
            TrackList(
                tracks = tracks,
                currentId = playerState.current?.id,
                bottom = padding.calculateBottomPadding() + 24.dp,
                showShuffleBar = true,
                onDownloadAll = rememberDownloadAll(tracks),
                trailing = { track ->
                    {
                        IconButton(onClick = {
                            if (isFavorites) appVm.toggleFavorite(track)
                            else appVm.removeFromPlaylist(playlistId, track.id)
                            message("Removed \"${track.title}\"")
                        }) {
                            Icon(
                                if (isFavorites) Icons.Rounded.Favorite else Icons.Rounded.Close,
                                contentDescription = if (isFavorites) "Remove ${track.title} from liked songs"
                                else "Remove ${track.title} from playlist",
                            )
                        }
                    }
                },
            )
        }
    }

    if (renaming && playlist != null) {
        NameDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initial = playlist.name,
            onConfirm = { appVm.renamePlaylist(playlistId, it) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    appVm.deletePlaylist(playlistId)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

const val FAVORITES_ID = "favorites"


/** A "download all" action for [tracks], or null when there is nothing online left to download. */
@Composable
fun rememberDownloadAll(tracks: List<Track>): (() -> Unit)? {
    val downloads = LocalDownloads.current
    val downloaded = LocalDownloadedIds.current
    val message = LocalMessage.current
    val wifiOnly = LocalOfflinePrefs.current.wifiOnlyDownloads
    val todo = tracks.filter { it.isOnline && it.id !in downloaded }
    if (todo.isEmpty()) return null
    return {
        var pending = todo.size
        var started = 0
        var refused = 0
        todo.forEach { track ->
            downloads.request(track) { result ->
                if (result == EnqueueResult.STARTED) started++
                if (result == EnqueueResult.NOT_ALLOWED) refused++
                pending--
                if (pending == 0) {
                    message(
                        when {
                            started > 0 && refused > 0 -> "Downloading $started songs. $refused aren't allowed by their artists."
                            started > 0 -> "Downloading $started " + (if (started == 1) "song" else "songs") + (if (wifiOnly) " (starts on Wi-Fi)" else "")
                            refused > 0 -> "The artists haven't allowed downloads for these songs"
                            else -> "Nothing new to download"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsTab(bottom: Dp, currentId: String?) {
    val downloads = LocalDownloads.current
    val player = LocalPlayer.current
    val message = LocalMessage.current
    val items by downloads.items.collectAsStateWithLifecycle()
    val disk by downloads.diskBytes.collectAsStateWithLifecycle()
    val done = items.filter { it.status == DownloadStatus.DONE }.map { it.track }

    if (items.isEmpty()) {
        CenteredMessage {
            MessageCard(
                "No downloads yet. Open the menu of an online song and choose Download for offline.",
                icon = Icons.Rounded.Download,
            )
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "summary") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done.isNotEmpty()) {
                    Button(onClick = { player.playQueue(done, 0, shuffle = false) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${done.size} saved, ${formatBytes(disk)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.track.id }) { item ->
            DownloadRow(
                item = item,
                isCurrent = currentId == item.track.id,
                onClick = {
                    if (item.status == DownloadStatus.DONE) {
                        player.playQueue(done, done.indexOfFirst { it.id == item.track.id }.coerceAtLeast(0))
                    } else {
                        message("This song isn't downloaded yet")
                    }
                },
                onRemove = {
                    downloads.remove(item.track.id)
                    message(if (item.status == DownloadStatus.DONE) "Download removed" else "Download cancelled")
                },
                onRetry = { downloads.request(item.track) { } },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.track, Modifier.size(52.dp), corner = 10.dp, sizePx = 128)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (item.status) {
                DownloadStatus.DONE -> Text(
                    "Saved on this phone, ${formatBytes(item.bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DownloadStatus.RUNNING -> {
                    if (item.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp))
                    }
                    Text(
                        if (item.totalBytes > 0L) "Downloading, " + (item.progress * 100).toInt() + "%" else "Downloading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DownloadStatus.QUEUED -> Text(
                    "Waiting to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DownloadStatus.FAILED -> Text(
                    "Download failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (item.status == DownloadStatus.FAILED) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = if (item.status == DownloadStatus.DONE) "Delete the download of ${item.track.title}"
                else "Cancel the download of ${item.track.title}",
            )
        }
    }
}
