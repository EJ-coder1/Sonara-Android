package com.sonara.player.data

import com.sonara.player.data.lyrics.LyricLine
import com.sonara.player.data.lyrics.Lrc
import com.sonara.player.data.lyrics.LyricsTiming
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LrcTest {

    @Test
    fun parsesBasicLines() {
        val lines = Lrc.parse("[00:12.50]Hello there\n[01:02.05]Second line")
        assertEquals(listOf(LyricLine(12_500, "Hello there"), LyricLine(62_050, "Second line")), lines)
    }

    @Test
    fun ignoresHeaderTagsAndBlankInput() {
        assertTrue(Lrc.parse(null).isEmpty())
        assertTrue(Lrc.parse("").isEmpty())
        val lines = Lrc.parse("[ar:Someone]\n[ti:Song]\n[00:01.00]Go")
        assertEquals(listOf(LyricLine(1_000, "Go")), lines)
    }

    @Test
    fun handlesMultipleTimeTagsAndSorts() {
        val lines = Lrc.parse("[01:00.00][00:10.00]Chorus\n[00:30.00]Verse")
        assertEquals(listOf(10_000L, 30_000L, 60_000L), lines.map { it.timeMs })
        assertEquals("Chorus", lines[0].text)
        assertEquals("Chorus", lines[2].text)
    }

    @Test
    fun handlesFractionLengths() {
        assertEquals(500L, Lrc.parse("[00:00.5]a")[0].timeMs)
        assertEquals(550L, Lrc.parse("[00:00.55]a")[0].timeMs)
        assertEquals(555L, Lrc.parse("[00:00.555]a")[0].timeMs)
        assertEquals(5_000L, Lrc.parse("[00:05]a")[0].timeMs)
    }

    @Test
    fun keepsEmptyInstrumentalLinesAndStripsWordStamps() {
        val lines = Lrc.parse("[00:05.00]\n[00:06.00]<00:06.00>Hi <00:06.40>you")
        assertEquals("", lines[0].text)
        assertEquals("Hi you", lines[1].text)
    }

    @Test
    fun activeIndexFindsCurrentLine() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, LyricsTiming.activeIndex(lines, 500))
        assertEquals(0, LyricsTiming.activeIndex(lines, 1_000))
        assertEquals(0, LyricsTiming.activeIndex(lines, 4_999))
        assertEquals(1, LyricsTiming.activeIndex(lines, 5_000))
        assertEquals(2, LyricsTiming.activeIndex(lines, 99_000))
        assertEquals(-1, LyricsTiming.activeIndex(emptyList(), 1_000))
    }

    @Test
    fun wordFillsSweepFromLeftToRight() {
        val words = LyricsTiming.words("one two three")
        val start = LyricsTiming.wordFills(words, 0, 4_000, 0)
        assertTrue(start.all { it == 0f })
        val mid = LyricsTiming.wordFills(words, 0, 4_000, 1_800)
        assertTrue(mid[0] > mid[1] && mid[1] >= mid[2])
        val end = LyricsTiming.wordFills(words, 0, 4_000, 4_000)
        assertTrue(end.all { it == 1f })
    }

    @Test
    fun wordFillsHandlesEmptyAndOddInput() {
        assertEquals(0, LyricsTiming.wordFills(emptyList(), 0, 1_000, 500).size)
        val f = LyricsTiming.wordFills(listOf("x"), 1_000, 1_000, 500)
        assertEquals(1, f.size)
        assertTrue(f[0] in 0f..1f)
    }

    @Test
    fun lineEndUsesNextLineAndCaps() {
        val lines = listOf(LyricLine(0, "a"), LyricLine(3_000, "b"), LyricLine(30_000, "c"))
        assertEquals(3_000L, LyricsTiming.lineEnd(lines, 0, 100_000))
        assertEquals(3_000L + 8_000L, LyricsTiming.lineEnd(lines, 1, 100_000))
        assertEquals(30_000L + 8_000L, LyricsTiming.lineEnd(lines, 2, 100_000))
        assertEquals(31_000L, LyricsTiming.lineEnd(lines, 2, 31_000))
    }
}
