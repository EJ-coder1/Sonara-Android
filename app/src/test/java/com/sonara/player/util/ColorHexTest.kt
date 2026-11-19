package com.sonara.player.util

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorHexTest {
    @Test
    fun parsesLongAndShortHexWithOrWithoutHash() {
        assertEquals(0xFF7C3AED.toInt(), ColorMath.parseHex("#7C3AED"))
        assertEquals(0xFF7C3AED.toInt(), ColorMath.parseHex("7c3aed"))
        assertEquals(0xFF7733EE.toInt(), ColorMath.parseHex("#73E"))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.parseHex("  #FFF "))
    }

    @Test
    fun rejectsAnythingThatIsNotAColor() {
        assertNull(ColorMath.parseHex(""))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex("-7C3AE"))
        assertNull(ColorMath.parseHex("#7C3AEDFF"))
    }

    @Test
    fun hexRoundTrips() {
        assertEquals("#7C3AED", ColorMath.toHex(0xFF7C3AED.toInt()))
        assertEquals("#000000", ColorMath.toHex(0xFF000000.toInt()))
        assertEquals(0xFFEC4899.toInt(), ColorMath.parseHex(ColorMath.toHex(0xFFEC4899.toInt())))
    }

    @Test
    fun hslOfPrimaryColors() {
        val (h, s, l) = ColorMath.toHsl(0xFFFF0000.toInt())
        assertEquals(0f, h)
        assertEquals(1f, s)
        assertEquals(0.5f, l)
        val gray = ColorMath.toHsl(0xFF808080.toInt())
        assertEquals(0f, gray.second)
    }

    @Test
    fun hslToArgbAndBackKeepsTheColorClose() {
        for (argb in listOf(0xFF7C3AED.toInt(), 0xFF0EA5E9.toInt(), 0xFFF59E0B.toInt(), 0xFF14B8A6.toInt())) {
            val (h, s, l) = ColorMath.toHsl(argb)
            val back = ColorMath.hslToArgb(h, s, l)
            for (shift in listOf(16, 8, 0)) {
                val a = (argb shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue(abs(a - b) <= 2, "channel at $shift differs: $a vs $b")
            }
        }
    }
}
