package com.sonara.player.playback

import org.junit.Test
import kotlin.test.assertEquals

class FxSavedTest {

    @Test
    fun roundTrips() {
        val s = FxSaved(enabled = true, bassStrength = 400, loudnessMb = 600, levelsMb = listOf(300, 0, -150, 200, 500))
        assertEquals(s, FxSaved.decode(s.encode()))
    }

    @Test
    fun emptyLevelsRoundTrip() {
        val s = FxSaved(enabled = false, bassStrength = 0, loudnessMb = 0, levelsMb = emptyList())
        assertEquals(s, FxSaved.decode(s.encode()))
    }

    @Test
    fun badInputFallsBackToDefaults() {
        assertEquals(FxSaved(), FxSaved.decode(null))
        assertEquals(FxSaved(), FxSaved.decode(""))
        assertEquals(FxSaved(), FxSaved.decode("garbage"))
        assertEquals(FxSaved(), FxSaved.decode("1|1|x|0|1,2"))
        assertEquals(FxSaved(), FxSaved.decode("9|1|0|0|"))
    }

    @Test
    fun clampsOutOfRangeValues() {
        val s = FxSaved.decode("1|1|5000|9000|")
        assertEquals(1000, s.bassStrength)
        assertEquals(1500, s.loudnessMb)
    }
}
