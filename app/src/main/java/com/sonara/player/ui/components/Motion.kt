package com.sonara.player.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** True while a song is actually playing (used for the animated "now playing" bars in lists). */
val LocalIsPlaying = staticCompositionLocalOf { false }

/**
 * Whether decorative motion should run: the user's "Animations" setting AND Android's own
 * animation scale (Settings > Accessibility > Remove animations turns it off).
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    val system = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        } catch (e: Exception) {
            true
        }
    }
    return system && LocalUiPrefs.current.animations
}

/** Where one bar of [PlayingBars] bounces between, and how long one full up-and-down swing takes. */
private class BarSpec(val low: Float, val high: Float, val swingMs: Int)

private val PlayingBarSpecs = listOf(
    BarSpec(0.20f, 1.00f, 480),
    BarSpec(0.35f, 0.90f, 640),
    BarSpec(0.15f, 0.85f, 560),
)

/**
 * Three little bars (the equalizer "|||") that dance ONLY while [active] is true (music is
 * really playing) and hold still where they are when it is false (paused). Each bar keeps its own
 * height, so pausing freezes the bars mid-dance and playing again carries on from there.
 * They also stay still when animations are turned off (Settings, or Android's animation scale).
 */
@Composable
fun PlayingBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animate = active && rememberMotionEnabled()
    val bars = remember {
        listOf(Animatable(0.30f), Animatable(0.55f), Animatable(0.40f))
    }
    bars.forEachIndexed { i, bar ->
        val spec = PlayingBarSpecs[i]
        // The effect runs while `animate` is true and is cancelled the moment it turns false;
        // a cancelled Animatable simply stays at its current value, which is the "pause".
        LaunchedEffect(animate) {
            if (!animate) return@LaunchedEffect
            val range = spec.high - spec.low
            while (true) {
                for (target in floatArrayOf(spec.high, spec.low)) {
                    val distance = kotlin.math.abs(target - bar.value) / range
                    val duration = (distance * spec.swingMs / 2f).toInt().coerceAtLeast(16)
                    bar.animateTo(target, tween(duration, easing = LinearEasing))
                }
            }
        }
    }
    Canvas(modifier.size(16.dp)) {
        val barWidth = size.width / 5f
        bars.forEachIndexed { i, bar ->
            val h = size.height * bar.value
            drawRoundRect(
                color = color,
                topLeft = Offset(i * 2f * barWidth, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

/** Shrinks a little while pressed and springs back (give it the same source you pass to clickable). */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and slides an item up the first time it appears, one after another ([key] sets the order).
 * [seen] remembers what already played, so scrolling back doesn't replay it.
 */
@Composable
fun Modifier.fadeSlideIn(key: Int, seen: MutableSet<Int>, enabled: Boolean = true): Modifier {
    val play = remember { enabled && key !in seen }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (play) {
            seen.add(key)
            delay(key.coerceAtMost(10) * 60L)
            progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

/** A list item that fades and slides in the first time it shows (see [fadeSlideIn]). */
fun LazyListScope.animatedItem(
    key: String,
    index: Int,
    seen: MutableSet<Int>,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        Box(Modifier.fadeSlideIn(index, seen, enabled)) { content() }
    }
}
