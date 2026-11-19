package com.sonara.player.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/** Three little bars that dance while music plays and rest when it is paused. */
@Composable
fun PlayingBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val bars: List<State<Float>> = if (active) {
        val t = rememberInfiniteTransition(label = "bars")
        listOf(
            t.animateFloat(0.20f, 1.00f, infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse), label = "bar1"),
            t.animateFloat(0.35f, 0.90f, infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Reverse), label = "bar2"),
            t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "bar3"),
        )
    } else {
        remember { listOf(mutableFloatStateOf(0.30f), mutableFloatStateOf(0.55f), mutableFloatStateOf(0.40f)) }
    }
    Canvas(modifier.size(16.dp)) {
        val barWidth = size.width / 5f
        bars.forEachIndexed { i, state ->
            val h = size.height * state.value
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
