package com.sonara.player.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.sonara.player.SonaraApp
import com.sonara.player.data.Track
import com.sonara.player.data.lyrics.LyricsState
import com.sonara.player.playback.PlayerUiState
import com.sonara.player.ui.components.Artwork
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.LocalTrackMenu
import com.sonara.player.ui.components.LocalUiPrefs
import com.sonara.player.ui.components.TrackRow
import com.sonara.player.util.formatTime
import java.util.Locale

private val OnBackdrop = Color.White
private val OnBackdropSoft = Color.White.copy(alpha = 0.72f)
private val GlassFill = Color.White.copy(alpha = 0.14f)
private val GlassFillOn = Color.White.copy(alpha = 0.30f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    isFavorite: Boolean,
    onToggleFavorite: (Track) -> Unit,
    onClose: () -> Unit,
    onSetLyricsScale: (Int) -> Unit,
    onSetLyricsOffset: (Int) -> Unit,
    onEnableLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val player = LocalPlayer.current
    val openMenu = LocalTrackMenu.current
    val prefs = LocalUiPrefs.current
    val context = LocalContext.current
    val lyricsRepo = remember { (context.applicationContext as SonaraApp).container.lyrics }

    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var showAudio by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showOffset by rememberSaveable { mutableStateOf(false) }
    var lyricsRetry by remember { mutableIntStateOf(0) }

    // Lyrics are only requested once the lyrics view is opened, and only when enabled in Settings.
    val lyricsState by produceState<LyricsState>(LyricsState.Loading, track.id, prefs.lyricsEnabled, showLyrics, lyricsRetry) {
        value = when {
            !prefs.lyricsEnabled -> LyricsState.Off
            !showLyrics -> LyricsState.Loading
            else -> {
                value = LyricsState.Loading
                lyricsRepo.load(track)
            }
        }
    }

    val duration = state.durationMs.coerceAtLeast(1L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val dragging = dragFraction
    val shownPosition = if (dragging != null) (dragging * duration).toLong() else state.positionMs

    Box(modifier.fillMaxSize()) {
        if (prefs.fluidBackdrop) {
            FluidBackdrop(track = track, animate = state.isPlaying, dim = if (showLyrics) 0.55f else 0.38f)
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest))
        }

        CompositionLocalProvider(LocalContentColor provides OnBackdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                // ---- top bar ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(onClick = onClose, description = "Close player") {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (showLyrics) "Lyrics" else "Now playing",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnBackdrop,
                        )
                        Text(
                            if (track.isOnline) "Free music from Jamendo" else "From your phone",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnBackdropSoft,
                        )
                    }
                    GlassButton(onClick = { openMenu(track) }, description = "More options") {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                }

                // ---- middle: cover art or lyrics ----
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (showLyrics) {
                        Column(Modifier.fillMaxSize()) {
                            LyricsToolbar(
                                state = lyricsState,
                                scalePercent = prefs.lyricsScalePercent,
                                offsetMs = prefs.lyricsOffsetMs,
                                onCycleScale = { onSetLyricsScale(nextLyricsScale(prefs.lyricsScalePercent)) },
                                onOpenOffset = { showOffset = true },
                            )
                            LyricsView(
                                state = lyricsState,
                                positionMs = state.positionMs,
                                isPlaying = state.isPlaying,
                                speed = state.speed,
                                durationMs = state.durationMs,
                                offsetMs = prefs.lyricsOffsetMs,
                                scalePercent = prefs.lyricsScalePercent,
                                onSeek = { player.seekTo(it) },
                                onRetry = { lyricsRetry++ },
                                onEnable = onEnableLyrics,
                                modifier = Modifier
                                    .weight(1f)
                                    .fadeEdges(),
                            )
                        }
                    } else {
                        CoverArt(track = track, playing = state.playWhenReady)
                    }
                }

                // ---- title / artist / like ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OnBackdrop,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = OnBackdropSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassButton(
                        onClick = { onToggleFavorite(track) },
                        description = if (isFavorite) "Remove from liked songs" else "Add to liked songs",
                        size = 48,
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else OnBackdrop,
                        )
                    }
                }

                // ---- seek bar ----
                WavySeekBar(
                    fraction = fraction,
                    isPlaying = state.isPlaying,
                    onSeekPreview = { dragFraction = it },
                    onSeekFinished = { player.seekTo((it * duration).toLong()) },
                    wavy = prefs.wavySeekBar,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(shownPosition), style = MaterialTheme.typography.labelMedium, color = OnBackdropSoft)
                    Text(
                        "-" + formatTime((duration - shownPosition).coerceAtLeast(0L)),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnBackdropSoft,
                    )
                }

                // ---- quality badge (tap for details) ----
                val badge = state.audioSpecs?.badge
                Text(
                    badge ?: if (track.isOnline) "Streaming from Jamendo" else "Playing from this phone",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnBackdropSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Show details and audio specs") { showDetails = true }
                        .padding(vertical = 6.dp),
                )

                // ---- main controls ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(onClick = player::previous, description = "Previous track", size = 64) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                    Button(
                        onClick = player::togglePlayPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        shape = RoundedCornerShape(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OnBackdrop, contentColor = Color.Black),
                    ) {
                        if (state.isBuffering && !state.isPlaying && state.playWhenReady) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp,
                                color = Color.Black,
                            )
                        } else {
                            Icon(
                                if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.playWhenReady) "Pause" else "Play",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    GlassButton(onClick = player::next, description = "Next track", size = 64) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }

                // ---- shuffle / repeat / lyrics / audio / queue ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(
                        onClick = player::toggleShuffle,
                        description = if (state.shuffle) "Shuffle on" else "Shuffle off",
                        selected = state.shuffle,
                        size = 52,
                    ) { Icon(Icons.Rounded.Shuffle, contentDescription = null) }
                    GlassButton(
                        onClick = player::cycleRepeat,
                        description = when (state.repeatMode) {
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            else -> "Repeat off"
                        },
                        selected = state.repeatMode != Player.REPEAT_MODE_OFF,
                        size = 52,
                    ) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = null,
                        )
                    }
                    GlassButton(
                        onClick = { showLyrics = !showLyrics },
                        description = if (showLyrics) "Hide lyrics" else "Show lyrics",
                        selected = showLyrics,
                        size = 52,
                    ) { Icon(Icons.Rounded.FormatQuote, contentDescription = null) }
                    GlassButton(
                        onClick = { showAudio = true },
                        description = "Audio and playback settings",
                        size = 52,
                    ) { Icon(Icons.Rounded.Tune, contentDescription = null) }
                    GlassButton(
                        onClick = { showQueue = true },
                        description = "Open queue",
                        size = 52,
                    ) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) }
                }
            }
        }
    }

    if (showQueue) QueueSheet(state = state, onDismiss = { showQueue = false })
    if (showAudio) {
        AudioPlaybackSheet(
            onDismiss = { showAudio = false },
            onOpenSleepTimer = { showSleep = true },
        )
    }
    if (showDetails) TrackDetailsDialog(track = track, specs = state.audioSpecs, onDismiss = { showDetails = false })
    if (showOffset) {
        LyricsOffsetDialog(
            offsetMs = prefs.lyricsOffsetMs,
            onChange = onSetLyricsOffset,
            onDismiss = { showOffset = false },
        )
    }
    if (showSleep) {
        SleepTimerDialog(
            active = state.sleepRemainingMs > 0L,
            remainingMs = state.sleepRemainingMs,
            onPick = { minutes ->
                player.startSleepTimer(minutes)
                showSleep = false
            },
            onCancelTimer = {
                player.cancelSleepTimer()
                showSleep = false
            },
            onDismiss = { showSleep = false },
        )
    }
}

/** Cover art that gently grows while playing and settles back when paused. */
@Composable
private fun CoverArt(track: Track, playing: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        Artwork(
            track = track,
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), clip = false),
            corner = 28.dp,
            sizePx = 1024,
        )
    }
}

/** Fades the top and bottom edge of scrolling content into the background. */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.07f to Color.Black,
                0.86f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private fun nextLyricsScale(current: Int): Int = when {
    current < 95 -> 100
    current < 110 -> 120
    else -> 85
}

@Composable
private fun LyricsToolbar(
    state: LyricsState,
    scalePercent: Int,
    offsetMs: Int,
    onCycleScale: () -> Unit,
    onOpenOffset: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = when (state) {
            is LyricsState.Synced -> "LINE SYNC \u2022 lrclib.net"
            is LyricsState.Plain -> "LYRICS \u2022 lrclib.net"
            else -> ""
        }
        if (label.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(50), color = GlassFill, contentColor = OnBackdrop) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCycleScale) {
            Text("Aa $scalePercent%", color = OnBackdrop, style = MaterialTheme.typography.labelLarge)
        }
        if (state is LyricsState.Synced) {
            IconButton(onClick = onOpenOffset) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = "Lyrics timing, now ${formatOffset(offsetMs)}",
                    tint = OnBackdrop,
                )
            }
        }
    }
}

private fun formatOffset(ms: Int): String = String.format(Locale.US, "%+.1f s", ms / 1000f)

@Composable
private fun LyricsOffsetDialog(offsetMs: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics timing") },
        text = {
            Column {
                Text(
                    "If the words show too early or too late, shift them. Positive numbers make lyrics appear later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatOffset(offsetMs),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChange((offsetMs - 250).coerceAtLeast(-5000)) }) { Text("-0.25 s") }
                    TextButton(onClick = { onChange((offsetMs + 250).coerceAtMost(5000)) }) { Text("+0.25 s") }
                    TextButton(onClick = { onChange(0) }, enabled = offsetMs != 0) { Text("Reset") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Round translucent button that sits on the animated backdrop. */
@Composable
private fun GlassButton(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Int = 44,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (selected) GlassFillOn else GlassFill)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides OnBackdrop) { content() }
    }
}

@Composable
private fun SleepTimerDialog(
    active: Boolean,
    remainingMs: Long,
    onPick: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    if (active) "Music will pause in ${formatTime(remainingMs)}." else "Music will pause when the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    TextButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$minutes minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (active) TextButton(onClick = onCancelTimer) { Text("Turn off timer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerUiState, onDismiss: () -> Unit) {
    val player = LocalPlayer.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Up next",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (state.queue.isEmpty()) {
            Text(
                "Your queue is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = state.queueIndex.coerceAtLeast(0),
            )
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
                itemsIndexed(state.queue, key = { index, t -> "$index-${t.id}" }) { index, t ->
                    val isCurrent = index == state.queueIndex
                    if (isCurrent) {
                        TrackRow(track = t, isCurrent = true, onClick = {})
                    } else {
                        TrackRow(
                            track = t,
                            onClick = { player.skipToQueueItem(index) },
                            trailing = {
                                IconButton(onClick = { player.removeFromQueue(index) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${t.title} from queue")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Compact player shown above the bottom navigation while something is loaded. */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val player = LocalPlayer.current
    val fraction = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                Modifier
                    .clickable(onClickLabel = "Open player", onClick = onOpen)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track, Modifier.size(46.dp), corner = 12.dp, sizePx = 128)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(onClick = player::togglePlayPause) {
                    Icon(
                        if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.playWhenReady) "Pause" else "Play",
                    )
                }
                IconButton(onClick = player::next) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                trackColor = Color.Transparent,
            )
        }
    }
}
