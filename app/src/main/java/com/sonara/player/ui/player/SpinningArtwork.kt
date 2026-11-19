package com.sonara.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonara.player.data.MiniShape
import com.sonara.player.data.Track
import com.sonara.player.ui.components.Artwork
import com.sonara.player.ui.components.rememberMotionEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val SPIN_DEGREES_PER_SECOND = 36f

/** A round outline with [bumps] soft scallops (more depth = deeper bumps). Stays inside its box. */
private fun scallopShape(bumps: Int, depth: Float): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(cx, cy)
    val steps = 240
    for (i in 0..steps) {
        val a = i.toFloat() / steps * (2f * PI.toFloat())
        val wave = 0.5f + 0.5f * cos(bumps * a) // 0..1
        val radius = r * (1f - depth + depth * wave)
        val x = cx + radius * cos(a)
        val y = cy + radius * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The outline used for album art in the mini player (and previews in Theme Studio). */
fun miniShapeFor(shape: MiniShape): Shape = when (shape) {
    MiniShape.CIRCLE -> CircleShape
    MiniShape.COOKIE -> scallopShape(bumps = 9, depth = 0.09f)
    MiniShape.FLOWER -> scallopShape(bumps = 6, depth = 0.18f)
    MiniShape.SQUARE -> RoundedCornerShape(16.dp)
}

/**
 * Album art in a round, scalloped or flower outline that turns slowly while music plays,
 * with a ring around it that fills up as the song goes on.
 */
@Composable
fun SpinningArtwork(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    shape: MiniShape,
    spin: Boolean,
    ring: Boolean,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionEnabled()
    val spinning = isPlaying && spin && motion
    var angle by remember { mutableFloatStateOf(0f) }
    // Turn only while playing. Pausing keeps the angle, so it carries on from where it stopped.
    LaunchedEffect(spinning) {
        if (spinning) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1_000_000_000f * SPIN_DEGREES_PER_SECOND) % 360f
                last = now
            }
        }
    }

    val ringColor = MaterialTheme.colorScheme.primary
    val railColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val ringWidth = 3.dp
    val artShape = remember(shape) { miniShapeFor(shape) }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        if (ring) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = ringWidth.toPx(), cap = StrokeCap.Round)
                val inset = ringWidth.toPx() / 2f
                val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
                drawArc(
                    color = railColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        val gap = if (ring) ringWidth + 3.dp else 0.dp
        Artwork(
            track = track,
            modifier = Modifier
                .fillMaxSize()
                .padding(gap)
                .graphicsLayer {
                    rotationZ = angle
                    this.shape = artShape
                    clip = true
                },
            corner = 0.dp,
            sizePx = 192,
        )
    }
}
