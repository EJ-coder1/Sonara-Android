package com.sonara.player.ui.player

import kotlin.math.PI
import kotlin.math.sin

/** Math for the wavy seek bar, kept free of Android so it can be unit tested. */
object WaveMath {
    const val TWO_PI: Float = (2.0 * PI).toFloat()

    /**
     * Vertical offset of the wave at [x] pixels from the left.
     * [thumbX] is where the played part ends; the wave fades out over [taperPx] before it so the
     * line meets the thumb smoothly, and fades in over [taperPx] at the very start.
     */
    fun offsetAt(x: Float, thumbX: Float, phase: Float, amplitudePx: Float, wavelengthPx: Float, taperPx: Float): Float {
        if (amplitudePx <= 0f || wavelengthPx <= 0f) return 0f
        val toEnd = ((thumbX - x) / taperPx).coerceIn(0f, 1f)
        val fromStart = (x / taperPx).coerceIn(0f, 1f)
        val envelope = smooth(toEnd) * smooth(fromStart)
        return sin(x / wavelengthPx * TWO_PI - phase) * amplitudePx * envelope
    }

    /** Smoothstep. */
    fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    /** Converts a touch x position into a 0..1 fraction of the bar. */
    fun fractionFor(x: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)
}
