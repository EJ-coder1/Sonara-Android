package com.sonara.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonara.player.data.MiniShape
import com.sonara.player.playback.PlayerUiState
import com.sonara.player.ui.components.Artwork
import com.sonara.player.ui.components.LocalPlayer
import com.sonara.player.ui.components.LocalUiPrefs
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The compact player above the bottom bar: art that turns while music plays (shape and ring are
 * chosen in Theme Studio), play/pause and next. Swipe it left for the next song, right for the previous one.
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return
    val player = LocalPlayer.current
    val prefs = LocalUiPrefs.current
    val scope = rememberCoroutineScope()
    val fraction = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    val swipeThreshold = with(LocalDensity.current) { 88.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val square = prefs.miniShape == MiniShape.SQUARE

    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically(tween(380)) { it / 2 } + fadeIn(tween(380)),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (abs(offsetX.value) / (swipeThreshold * 3f)).coerceIn(0f, 0.5f) }
                .then(
                    if (prefs.swipeMiniPlayer) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val dx = offsetX.value
                                    scope.launch {
                                        if (dx > swipeThreshold) player.previous()
                                        else if (dx < -swipeThreshold) player.next()
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.7f))
                                    }
                                },
                                onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                            ) { change, dragAmount ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                Row(
                    Modifier
                        .clickable(onClickLabel = "Open player", onClick = onOpen)
                        .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (square) {
                        Artwork(track, Modifier.size(48.dp), corner = 14.dp, sizePx = 128)
                    } else {
                        SpinningArtwork(
                            track = track,
                            isPlaying = state.isPlaying,
                            progress = fraction,
                            shape = prefs.miniShape,
                            spin = prefs.spinArtwork,
                            ring = prefs.progressRing,
                            diameter = 56.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
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
                // Without the ring (or with the plain square art) a thin bar shows the progress instead.
                if (square || !prefs.progressRing) {
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
    }
}
