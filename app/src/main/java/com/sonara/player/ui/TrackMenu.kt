package com.sonara.player.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonara.player.data.Playlist
import com.sonara.player.data.Track
import com.sonara.player.ui.components.Artwork
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.library.NameDialog

private fun openLink(context: Context, url: String, onFail: () -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        onFail()
    }
}

/** "More options" for any track (long list, mini player, now playing...). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    track: Track,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartMix: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onShowDetails: () -> Unit,
) {
    val player = LocalPlayer.current
    val message = LocalMessage.current
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track, Modifier.size(56.dp), corner = 12.dp, sizePx = 160)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Highlighted first action (like a "radio" for this song).
            Surface(
                onClick = {
                    onStartMix()
                    message("Starting a mix")
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(14.dp))
                    Text("Start mix with this song", style = MaterialTheme.typography.titleMedium)
                }
            }

            MenuItem("Play next", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                player.playNext(track)
                message("Will play next")
                onDismiss()
            }
            MenuItem("Add to queue", Icons.AutoMirrored.Rounded.QueueMusic) {
                player.addToQueue(track)
                message("Added to queue")
                onDismiss()
            }
            MenuItem("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                onDismiss()
                onAddToPlaylist()
            }
            MenuItem(
                if (isFavorite) "Remove from liked songs" else "Add to liked songs",
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            ) {
                onToggleFavorite()
                message(if (isFavorite) "Removed from liked songs" else "Added to liked songs")
                onDismiss()
            }
            if (track.artist.isNotBlank() && !track.artist.equals("Unknown artist", ignoreCase = true)) {
                MenuItem("Go to artist (${track.artist})", Icons.Rounded.Person) {
                    onDismiss()
                    onGoToArtist()
                }
            }
            if (track.album.isNotBlank() && !track.album.equals("Unknown album", ignoreCase = true)) {
                MenuItem("Go to album (${track.album})", Icons.Rounded.Album) {
                    onDismiss()
                    onGoToAlbum()
                }
            }
            MenuItem("Details & audio specs", Icons.Rounded.Info) {
                onDismiss()
                onShowDetails()
            }
            if (track.isOnline && track.shareUrl != null) {
                MenuItem("Copy link", Icons.Rounded.ContentCopy) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Song link", track.shareUrl))
                    message("Link copied")
                    onDismiss()
                }
                MenuItem("Share", Icons.Rounded.Share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${track.title} by ${track.artist}\n${track.shareUrl}")
                    }
                    context.startActivity(Intent.createChooser(send, "Share song"))
                    onDismiss()
                }
            }
            if (track.isOnline && track.licenseUrl != null) {
                MenuItem("License and credits", Icons.Rounded.Info) {
                    openLink(context, track.licenseUrl) { message("No app found to open this link") }
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: Track,
    playlists: List<Playlist>,
    appVm: AppViewModel,
    onDismiss: () -> Unit,
) {
    val message = LocalMessage.current
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            MenuItem("New playlist", Icons.Rounded.Add) { creating = true }
            LazyColumn {
                items(playlists, key = { it.id }) { pl ->
                    MenuItem(pl.name, Icons.AutoMirrored.Rounded.QueueMusic) {
                        appVm.addToPlaylist(pl.id, track) { added ->
                            message(if (added) "Added to ${pl.name}" else "Already in ${pl.name}")
                        }
                        onDismiss()
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            confirmLabel = "Create and add",
            onConfirm = { name ->
                appVm.createPlaylist(name, first = track)
                message("Added to $name")
                onDismiss()
            },
            onDismiss = { creating = false },
        )
    }
}
