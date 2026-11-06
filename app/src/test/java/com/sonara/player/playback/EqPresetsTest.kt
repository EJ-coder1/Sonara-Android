package com.sonara.player.playback

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EqPresetsTest {

    @Test
    fun flatIsAllZero() {
        assertEquals(listOf(0, 0, 0, 0, 0), EqPresets.levelsMb("Flat", 5, -1500, 1500).toList())
    }

    @Test
    fun bassBoostRaisesLowBandsMost() {
        val l = EqPresets.levelsMb("Bass boost", 5, -1500, 1500)
        assertTrue(l[0] > l[2] && l[2] >= l[4])
        assertEquals(600, l[0])
    }

    @Test
    fun clampsToDeviceRange() {
        val l = EqPresets.levelsMb("Electronic", 5, -300, 300)
        assertTrue(l.all { it in -300..300 })
        assertEquals(300, l[0])
    }

    @Test
    fun stretchesToAnyBandCount() {
        assertEquals(10, EqPresets.levelsMb("Rock", 10, -1500, 1500).size)
        assertEquals(1, EqPresets.levelsMb("Rock", 1, -1500, 1500).size)
        assertEquals(0, EqPresets.levelsMb("Rock", 0, -1500, 1500).size)
    }

    @Test
    fun unknownPresetIsFlat() {
        assertTrue(EqPresets.levelsMb("nope", 5, -1500, 1500).all { it == 0 })
    }

    @Test
    fun matchingFindsPresetOrCustom() {
        val rock = EqPresets.levelsMb("Rock", 5, -1500, 1500).toList()
        assertEquals("Rock", EqPresets.matching(rock, -1500, 1500))
        assertEquals(EqPresets.CUSTOM, EqPresets.matching(listOf(1, 2, 3, 4, 5), -1500, 1500))
        assertEquals(EqPresets.CUSTOM, EqPresets.matching(emptyList(), -1500, 1500))
    }
}
