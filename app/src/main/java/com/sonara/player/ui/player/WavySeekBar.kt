package com.sonara.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Seek bar whose played part is a gently moving wave. The wave flows while music plays and
 * relaxes into a straight line when paused. The not-yet-played part is a plain line.
 *
 * Original design, inspired by the "wavy progress" look of modern Material players.
 *
 * @param fraction current progress 0..1
 * @param onSeekPreview called with the new fraction while dragging, and with null when the drag ends or is cancelled
 * @param onSeekFinished called once with the final fraction (tap or end of drag)
 * @param wavy false draws a straight bar (used when "Wavy seek bar" is turned off)
 */
@Composable
fun WavySeekBar(
    fraction: Float,
    isPlaying: Boolean,
    onSeekPreview: (Float?) -> Unit,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.28f),
    wavy: Boolean = true,
    amplitude: Dp = 5.dp,
    wavelength: Dp = 30.dp,
    strokeWidth: Dp = 4.dp,
    enabled: Boolean = true,
) {
    val currentOnPreview by rememberUpdatedState(onSeekPreview)
    val currentOnFinished by rememberUpdatedState(onSeekFinished)

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)

    // Wave motion: runs only while playing; the amplitude eases in and out.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, wavy) {
        if (isPlaying && wavy) {
            while (true) {
                phase.snapTo(phase.value % WaveMath.TWO_PI)
                phase.animateTo(phase.value + WaveMath.TWO_PI, tween(2400, easing = LinearEasing))
            }
        }
    }
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && wavy) 1f else 0f,
        animationSpec = tween(450),
        label = "waveAmplitude",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragFraction != null) 1.35f else 1f,
        animationSpec = tween(140),
        label = "thumbScale",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                contentDescription = "Seek bar"
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target ->
                    if (enabled) currentOnFinished(target.coerceIn(0f, 1f))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    currentOnFinished(WaveMath.fractionFor(offset.x, size.width.toFloat()))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val f = WaveMath.fractionFor(offset.x, size.width.toFloat())
                        dragFraction = f
                        currentOnPreview(f)
                    },
                    onDragEnd = {
                        val last = dragFraction
                        dragFraction = null
                        currentOnPreview(null)
                        if (last != null) currentOnFinished(last)
                    },
                    onDragCancel = {
                        dragFraction = null
                        currentOnPreview(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val f = WaveMath.fractionFor(change.position.x, size.width.toFloat())
                        dragFraction = f
                        currentOnPreview(f)
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        val stroke = strokeWidth.toPx()
        val thumbW = 4.dp.toPx() * thumbScale
        val thumbH = 22.dp.toPx() * thumbScale
        val gap = 6.dp.toPx()
        // Keep the thumb fully inside the bar at both ends.
        val thumbX = (shown * w).coerceIn(thumbW / 2f, w - thumbW / 2f)

        // ----- played part (wave) -----
        val ampPx = amplitude.toPx() * amplitudeFactor
        val lenPx = wavelength.toPx()
        val taper = 14.dp.toPx()
        val endX = (thumbX - gap).coerceAtLeast(0f)
        val path = Path()
        var x = 0f
        val step = 2.dp.toPx().coerceAtLeast(1.5f)
        path.moveTo(0f, cy)
        while (x < endX) {
            path.lineTo(x, cy + WaveMath.offsetAt(x, endX, phase.value, ampPx, lenPx, taper))
            x += step
        }
        path.lineTo(endX, cy)
        drawPath(
            path = path,
            color = activeColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ----- remaining part (straight line) -----
        val startRest = thumbX + gap
        if (startRest < w) {
            drawLine(
                color = inactiveColor,
                start = Offset(startRest, cy),
                end = Offset(w, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        // ----- thumb -----
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(thumbX - thumbW / 2f, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
        )
    }
}
