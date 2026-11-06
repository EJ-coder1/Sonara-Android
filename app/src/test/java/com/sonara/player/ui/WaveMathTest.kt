package com.sonara.player.ui

import com.sonara.player.ui.player.WaveMath
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveMathTest {

    @Test
    fun flatWhenNoAmplitude() {
        assertEquals(0f, WaveMath.offsetAt(50f, 200f, 1f, 0f, 30f, 20f))
    }

    @Test
    fun fadesOutAtTheThumbAndInAtTheStart() {
        // Right at the thumb and at x = 0 the wave must be exactly flat.
        assertEquals(0f, WaveMath.offsetAt(200f, 200f, 0.7f, 8f, 30f, 20f), 1e-6f)
        assertEquals(0f, WaveMath.offsetAt(0f, 200f, 0.7f, 8f, 30f, 20f), 1e-6f)
    }

    @Test
    fun neverExceedsAmplitude() {
        var x = 0f
        while (x < 400f) {
            assertTrue(abs(WaveMath.offsetAt(x, 400f, 2.1f, 8f, 30f, 20f)) <= 8f + 1e-4f)
            x += 1.5f
        }
    }

    @Test
    fun fractionClamps() {
        assertEquals(0f, WaveMath.fractionFor(-20f, 100f))
        assertEquals(1f, WaveMath.fractionFor(250f, 100f))
        assertEquals(0.5f, WaveMath.fractionFor(50f, 100f))
        assertEquals(0f, WaveMath.fractionFor(10f, 0f))
    }
}
