package com.sonara.player.playback

import org.junit.Test
import kotlin.test.assertEquals

class CacheTrimPlannerTest {
    private val a = CacheTrimPlanner.Entry("a", 100, lastTouch = 1)
    private val b = CacheTrimPlanner.Entry("b", 100, lastTouch = 2)
    private val c = CacheTrimPlanner.Entry("c", 100, lastTouch = 3)

    @Test
    fun nothingIsRemovedWhenUnderTheLimit() {
        assertEquals(emptyList(), CacheTrimPlanner.keysToEvict(listOf(a, b, c), 300, 300))
    }

    @Test
    fun oldestSongsGoFirstUntilItFits() {
        assertEquals(listOf("a", "b"), CacheTrimPlanner.keysToEvict(listOf(c, a, b), 300, 150))
    }

    @Test
    fun theSongThatIsPlayingIsNeverRemoved() {
        assertEquals(listOf("b", "c"), CacheTrimPlanner.keysToEvict(listOf(a, b, c), 300, 150, setOf("a")))
    }
}
