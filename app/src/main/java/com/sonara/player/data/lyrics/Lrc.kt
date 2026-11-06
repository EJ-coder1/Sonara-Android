package com.sonara.player.data.lyrics

import kotlin.math.max
import kotlin.math.min

/** One line of synced lyrics. [text] is empty for an instrumental gap. */
data class LyricLine(val timeMs: Long, val text: String)

/** What the lyrics screen can show. */
sealed interface LyricsState {
    data object Off : LyricsState
    data object Loading : LyricsState
    data class Synced(val lines: List<LyricLine>) : LyricsState
    data class Plain(val text: String) : LyricsState
    data object NotFound : LyricsState
    data object Failed : LyricsState
}

object Lrc {

    // [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx]  (also accepts ":" before the fraction)
    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    // Enhanced LRC word stamps such as <00:12.34>
    private val wordTag = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    /**
     * Turns LRC text into time-sorted lines. Lines may carry several time tags
     * ("[00:10.00][01:30.00]chorus"). Header tags like [ar:Artist] are ignored.
     */
    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<LyricLine>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line[0] != '[') continue
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            // Everything after the last leading time tag is the text.
            var textStart = 0
            val leading = ArrayList<MatchResult>()
            for (t in tags) {
                if (t.range.first == textStart) {
                    leading += t
                    textStart = t.range.last + 1
                } else break
            }
            if (leading.isEmpty()) continue
            val text = wordTag.replace(line.substring(textStart), "").trim()
            for (t in leading) {
                val mins = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val frac = t.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100L
                    2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(mins * 60_000L + sec * 1_000L + ms, text)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }
}

object LyricsTiming {

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines[0].timeMs) return -1
        var lo = 0
        var hi = lines.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lines[mid].timeMs <= positionMs) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** Splits a lyric line into words, keeping punctuation attached to the word. */
    fun words(text: String): List<String> = text.split(' ', '\t').filter { it.isNotEmpty() }

    /**
     * How much of each word has been "sung" (0f..1f) at [positionMs].
     * LRCLIB only has line timing, so word timing is estimated: the line's singing time is shared
     * between its words in proportion to their length. This gives a smooth karaoke sweep.
     *
     * [endMs] is when the next line starts (or a fallback). The sweep finishes slightly before it
     * so the last word is fully lit before the line changes.
     */
    fun wordFills(words: List<String>, startMs: Long, endMs: Long, positionMs: Long): FloatArray {
        val n = words.size
        if (n == 0) return FloatArray(0)
        val span = max(1L, endMs - startMs)
        val sungSpan = max(1L, (span * 0.88f).toLong())
        val progress = ((positionMs - startMs).toFloat() / sungSpan.toFloat()).coerceIn(0f, 1f)

        val weights = FloatArray(n) { (words[it].length + 1).toFloat() }
        val total = weights.sum()
        val fills = FloatArray(n)
        var acc = 0f
        for (i in 0 until n) {
            val a = acc / total
            acc += weights[i]
            val b = acc / total
            fills[i] = if (b <= a) 1f else ((progress - a) / (b - a)).coerceIn(0f, 1f)
        }
        return fills
    }

    /** When the sweep for line [index] should end: the next line's start, capped at [maxLineMs]. */
    fun lineEnd(lines: List<LyricLine>, index: Int, durationMs: Long, maxLineMs: Long = 8_000L): Long {
        val start = lines[index].timeMs
        val next = if (index + 1 <= lines.lastIndex) lines[index + 1].timeMs
        else if (durationMs > start) durationMs else start + maxLineMs
        return min(next, start + maxLineMs).coerceAtLeast(start + 1L)
    }
}
