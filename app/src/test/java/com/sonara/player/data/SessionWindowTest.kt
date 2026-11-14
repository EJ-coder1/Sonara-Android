package com.sonara.player.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionWindowTest {
    @Test
    fun shortQueuesAreSavedWhole() {
        assertEquals(0 to 50, SessionWindow.window(50, 10))
        assertEquals(0 to 100, SessionWindow.window(100, 99))
    }

    @Test
    fun longQueuesKeepTheSongsAroundTheCurrentOne() {
        assertEquals(225 to 325, SessionWindow.window(500, 250))
    }

    @Test
    fun theCurrentSongIsAlwaysInsideTheWindow() {
        for (index in listOf(0, 1, 24, 25, 250, 474, 475, 499)) {
            val (from, to) = SessionWindow.window(500, index)
            assertTrue(index in from until to, "index $index not in $from..$to")
            assertEquals(SessionWindow.MAX_TRACKS, to - from)
        }
    }

    @Test
    fun theWindowNeverLeavesTheQueue() {
        assertEquals(0 to 100, SessionWindow.window(500, 0))
        assertEquals(400 to 500, SessionWindow.window(500, 499))
    }
}
