package com.sonara.player.ui.player

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonara.player.data.Track
import com.sonara.player.ui.components.BrandGradient
import com.sonara.player.ui.components.averageColorArgb
import com.sonara.player.ui.components.rememberArtworkBitmap

private fun animationsOn(context: Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
} catch (e: Exception) {
    true
}

/**
 * Slowly drifting, softly blurred artwork behind the player.
 *
 * How it works (no blur library needed): the cover is loaded as a tiny bitmap and drawn very large,
 * so smooth scaling itself produces the blur. Three copies rotate and drift at different speeds so
 * the colors flow into each other. A dark scrim on top keeps white text readable on any cover.
 *
 * Honors the system "Remove animations" setting and stops moving when [animate] is false (paused).
 *
 * @param dim 0..1, how much darker the scrim is (lyrics use a darker backdrop)
 */
@Composable
fun FluidBackdrop(
    track: Track?,
    animate: Boolean,
    modifier: Modifier = Modifier,
    dim: Float = 0.38f,
) {
    val context = LocalContext.current
    val bitmap = rememberArtworkBitmap(track, sizePx = 64)
    val tint = remember(bitmap) { runCatching { bitmap?.averageColorArgb() }.getOrNull()?.let { Color(it) } }
    val systemAnimations = remember { animationsOn(context) }
    val moving = animate && systemAnimations

    Box(modifier.fillMaxSize().clipToBounds()) {
        // Base color: the cover's average color, darkened. Also the fallback when there is no cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (tint != null) Brush.verticalGradient(listOf(tint.darken(0.55f), tint.darken(0.30f)))
                    else BrandGradient
                )
        )

        Crossfade(targetState = bitmap, animationSpec = tween(700), label = "backdropArt") { bmp ->
            if (bmp != null) {
                if (moving) MovingBlobs(bmp) else StaticBlobs(bmp)
            }
        }

        // Scrim: readable text everywhere, deeper at the bottom where the controls are.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = dim * 0.9f),
                        0.5f to Color.Black.copy(alpha = dim),
                        1f to Color.Black.copy(alpha = (dim + 0.35f).coerceAtMost(0.9f)),
                    )
                )
        )
    }
}

private fun Color.darken(factor: Float): Color =
    Color(red * factor, green * factor, blue * factor, 1f)

@Composable
private fun MovingBlobs(bmp: android.graphics.Bitmap) {
    val t = rememberInfiniteTransition(label = "fluid")
    val r1 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(46_000, easing = LinearEasing)), label = "r1")
    val r2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(62_000, easing = LinearEasing)), label = "r2")
    val r3 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(80_000, easing = LinearEasing)), label = "r3")
    val dx by t.animateFloat(-0.12f, 0.12f, infiniteRepeatable(tween(14_000), RepeatMode.Reverse), label = "dx")
    val dy by t.animateFloat(0.10f, -0.10f, infiniteRepeatable(tween(19_000), RepeatMode.Reverse), label = "dy")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = maxHeight * 1.45f
        val image = remember(bmp) { bmp.asImageBitmap() }
        Blob(image, big, alpha = 0.95f, scale = 1.00f) { rotationZ = r1; translationX = dx * size.width; translationY = dy * size.height }
        Blob(image, big, alpha = 0.60f, scale = 1.25f) { rotationZ = r2; translationX = -dx * size.width; translationY = dy * size.height * 0.6f }
        Blob(image, big * 0.8f, alpha = 0.45f, scale = 1.10f) { rotationZ = r3; translationX = dx * size.width * 0.5f; translationY = -dy * size.height }
    }
}

@Composable
private fun StaticBlobs(bmp: android.graphics.Bitmap) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val big = maxHeight * 1.45f
        val image = remember(bmp) { bmp.asImageBitmap() }
        Blob(image, big, alpha = 0.95f, scale = 1.00f) { rotationZ = 20f }
        Blob(image, big, alpha = 0.60f, scale = 1.25f) { rotationZ = 200f }
    }
}

@Composable
private fun Blob(
    image: androidx.compose.ui.graphics.ImageBitmap,
    size: androidx.compose.ui.unit.Dp,
    alpha: Float,
    scale: Float,
    transform: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = Modifier
                .requiredSize(size)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                    transform()
                },
        )
    }
}
