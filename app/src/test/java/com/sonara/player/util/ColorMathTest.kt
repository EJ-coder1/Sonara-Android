package com.sonara.player.util

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorMathTest {

    @Test
    fun primaryColorsRoundTrip() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.hslToArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), ColorMath.hslToArgb(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hslToArgb(240f, 1f, 0.5f))
    }

    @Test
    fun grayHasNoSaturation() {
        val c = ColorMath.hslToArgb(200f, 0f, 0.5f)
        val r = (c shr 16) and 0xFF
        assertEquals(r, (c shr 8) and 0xFF)
        assertEquals(r, c and 0xFF)
    }

    @Test
    fun hueWrapsAndRecovers() {
        assertEquals(ColorMath.hslToArgb(10f, 0.7f, 0.5f), ColorMath.hslToArgb(370f, 0.7f, 0.5f))
        assertEquals(ColorMath.hslToArgb(350f, 0.7f, 0.5f), ColorMath.hslToArgb(-10f, 0.7f, 0.5f))
        val h = ColorMath.hueOf(ColorMath.hslToArgb(200f, 0.8f, 0.5f))
        assertTrue(kotlin.math.abs(h - 200f) < 1.5f)
    }

    @Test
    fun luminanceOrdersBlackBelowWhite() {
        assertTrue(ColorMath.luminance(0xFF000000.toInt()) < 0.01f)
        assertTrue(ColorMath.luminance(0xFFFFFFFF.toInt()) > 0.99f)
    }
}
