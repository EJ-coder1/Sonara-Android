package com.sonara.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sonara.player.R
import com.sonara.player.data.Track
import com.sonara.player.data.UiPrefs
import com.sonara.player.playback.AudioEffectsManager
import com.sonara.player.playback.PlayerController
import com.sonara.player.ui.OnlineState
import com.sonara.player.ui.theme.BrandDeep
import com.sonara.player.ui.theme.BrandPink
import com.sonara.player.ui.theme.BrandViolet
import com.sonara.player.util.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- app-wide hooks (provided once in SonaraRoot) ----------

val LocalPlayer = staticCompositionLocalOf<PlayerController> { error("PlayerController not provided") }

/** Look-and-feel and lyrics options chosen in Settings. */
val LocalUiPrefs = staticCompositionLocalOf { UiPrefs() }

/** Equalizer / bass / loudness controls. */
val LocalFx = staticCompositionLocalOf<AudioEffectsManager> { error("AudioEffectsManager not provided") }

/** Opens the "more options" sheet for a track. */
val LocalTrackMenu = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Shows a short snackbar message. */
val LocalMessage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Asks for the music (and notification) permission. */
val LocalRequestAudioAccess = staticCompositionLocalOf<() -> Unit> { {} }

// ---------- artwork ----------

private object ArtworkCache {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 10).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(context: Context, track: Track, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${track.id}@$sizePx"
        cache.get(key)?.let { return@withContext it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(Uri.parse(track.uri), Size(sizePx, sizePx), null)
            } else {
                val art = track.artworkUri ?: return@withContext null
                context.contentResolver.openInputStream(Uri.parse(art))?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        bmp
    }
}

/** Downloads (or reads from Coil's cache) an online cover as a plain Bitmap. */
private suspend fun loadOnlineBitmap(context: Context, url: String?, sizePx: Int): Bitmap? {
    if (url.isNullOrBlank()) return null
    return try {
        val request = ImageRequest.Builder(context).data(url).size(sizePx).allowHardware(false).build()
        val result = context.imageLoader.execute(request)
        ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * The cover of [track] as a Bitmap (local or online), or null while loading / when there is none.
 * Keeps showing the previous cover until the next one is ready, so the player never flashes empty.
 */
@Composable
fun rememberArtworkBitmap(track: Track?, sizePx: Int): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, track?.id, sizePx) {
        value = when {
            track == null -> null
            track.isOnline -> loadOnlineBitmap(context, track.artworkUri, sizePx)
            else -> ArtworkCache.load(context, track, sizePx)
        }
    }
    return bitmap
}

/** Average color of a bitmap as an ARGB int. */
fun Bitmap.averageColorArgb(): Int = Bitmap.createScaledBitmap(this, 1, 1, true).getPixel(0, 0)

val BrandGradient: Brush
    get() = Brush.linearGradient(listOf(BrandDeep, BrandViolet, BrandPink))

/** Album art for any track. Shows the brand gradient + note while loading or when there is no art. */
@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    sizePx: Int = 512,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxSize(0.38f),
        )
        if (track != null) {
            if (track.isOnline) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val context = LocalContext.current
                val bitmap by produceState<Bitmap?>(initialValue = null, track.id, sizePx) {
                    value = ArtworkCache.load(context, track, sizePx)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun SonaraLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "Sonara logo",
        modifier = modifier.size(size),
    )
}

// ---------- rows & cards ----------

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val openMenu = LocalTrackMenu.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track, Modifier.size(52.dp), corner = 10.dp, sizePx = 128)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Text(
                text = formatTime(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = { openMenu(track) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${track.title}")
            }
        }
    }
}

@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Artwork(track, Modifier.size(width), corner = 16.dp, sizePx = 384)
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TrackCardRow(
    tracks: List<Track>,
    onPlay: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
            TrackCard(track, onClick = { onPlay(index) })
        }
    }
}

// ---------- loading / error / empty states ----------

@Composable
fun ShimmerRow(modifier: Modifier = Modifier, count: Int = 4) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    LazyRow(
        modifier = modifier,
        userScrollEnabled = false,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count) {
            Column(Modifier.width(148.dp)) {
                Box(
                    Modifier
                        .size(148.dp)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(110.dp)
                        .height(12.dp)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
    }
}

@Composable
fun MessageCard(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.CloudOff,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Renders loading / error / empty / content for any online list. */
@Composable
fun OnlineTrackRow(
    state: OnlineState,
    onRetry: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Nothing to show right now.",
) {
    when (state) {
        OnlineState.Idle -> Unit
        OnlineState.Loading -> ShimmerRow(modifier)
        is OnlineState.Error -> MessageCard(state.message, modifier, "Retry", onRetry)
        is OnlineState.Ready ->
            if (state.tracks.isEmpty()) MessageCard(emptyMessage, modifier, icon = Icons.Rounded.MusicNote)
            else TrackCardRow(state.tracks, onPlay = { onPlay(state.tracks, it) }, modifier = modifier)
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier.height(48.dp), shape = CircleShape) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
