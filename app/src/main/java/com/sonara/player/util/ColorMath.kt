package com.sonara.player.util

import kotlin.math.abs
import kotlin.math.roundToInt

/** Small HSL helpers so accent colors can be generated without any extra library. */
object ColorMath {

    /** [h] in degrees (any value), [s] and [l] in 0..1. Returns opaque ARGB. */
    fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val sat = s.coerceIn(0f, 1f)
        val light = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * light - 1f)) * sat
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = light - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Hue (0..360) of an ARGB color. Gray colors return 0. */
    fun hueOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return (h + 360f) % 360f
    }

    /** Relative luminance 0..1 (WCAG). */
    fun luminance(argb: Int): Float {
        fun ch(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * ch((argb shr 16) and 0xFF) + 0.7152f * ch((argb shr 8) and 0xFF) + 0.0722f * ch(argb and 0xFF)
    }
}
