package com.sonara.player.data.lyrics

import kotlin.math.max
import kotlin.math.min

/** One synced lyric line. wordTimesMs aligns with [text]'s words when enhanced LRC is available. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val wordTimesMs: List<Long> = emptyList(),
)

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
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    /**
     * Parses standard LRC and enhanced LRC. Enhanced word timestamps are retained instead of
     * discarded, so sources that provide karaoke timing can drive the word sweep accurately.
     */
    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<LyricLine>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line[0] != '[') continue
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            var textStart = 0
            val leading = ArrayList<MatchResult>()
            for (tag in tags) {
                if (tag.range.first == textStart) {
                    leading += tag
                    textStart = tag.range.last + 1
                } else break
            }
            if (leading.isEmpty()) continue

            val firstLineTime = parseTimestamp(leading.first())
            val (text, baseWordTimes) = parseEnhancedText(line.substring(textStart), firstLineTime)
            leading.forEach { tag ->
                val lineTime = parseTimestamp(tag)
                val shift = lineTime - firstLineTime
                val wordTimes = baseWordTimes.map { (it + shift).coerceAtLeast(0L) }
                out += LyricLine(lineTime, text, wordTimes)
            }
        }
        out.sortBy { it.timeMs }
        return out
    }

    private fun parseEnhancedText(raw: String, lineStartMs: Long): Pair<String, List<Long>> {
        val tags = wordTag.findAll(raw).toList()
        if (tags.isEmpty()) return wordTag.replace(raw, "").trim() to emptyList()

        val words = ArrayList<String>()
        val wordTimes = ArrayList<Long>()
        var cursor = 0
        var currentTime = lineStartMs

        fun appendSegment(segment: String, startMs: Long) {
            Regex("\\S+").findAll(segment).forEach { match ->
                words += match.value
                wordTimes += startMs
            }
        }

        for (tag in tags) {
            appendSegment(raw.substring(cursor, tag.range.first), currentTime)
            currentTime = parseTimestamp(tag)
            cursor = tag.range.last + 1
        }
        appendSegment(raw.substring(cursor), currentTime)
        return words.joinToString(" ") to wordTimes
    }

    private fun parseTimestamp(tag: MatchResult): Long {
        val minutes = tag.groupValues[1].toLong()
        val seconds = tag.groupValues[2].toLong()
        val fraction = tag.groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}

object LyricsTiming {
    /** Interpolates between player position updates, respecting playback speed and track duration. */
    fun interpolatePosition(positionMs: Long, elapsedMs: Long, speed: Float, durationMs: Long): Long {
        val advance = (elapsedMs.coerceAtLeast(0L).toDouble() * speed.coerceAtLeast(0f).toDouble()).toLong()
        val estimated = (positionMs.coerceAtLeast(0L) + advance).coerceAtLeast(0L)
        return if (durationMs > 0L) estimated.coerceAtMost(durationMs) else estimated
    }

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
     * Uses exact enhanced-LRC word stamps when available. For line-only LRC, it still estimates
     * a sweep within the line interval because the source does not provide word-level timing.
     */
    fun wordFills(
        words: List<String>,
        startMs: Long,
        endMs: Long,
        positionMs: Long,
        wordTimesMs: List<Long> = emptyList(),
    ): FloatArray {
        val count = words.size
        if (count == 0) return FloatArray(0)
        val fills = FloatArray(count)
        if (wordTimesMs.size == count) {
            for (i in 0 until count) {
                val start = wordTimesMs[i].coerceIn(startMs, endMs)
                val next = if (i + 1 < count) wordTimesMs[i + 1].coerceAtLeast(start + 1L) else endMs
                val span = max(1L, next - start)
                fills[i] = ((positionMs - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            }
            return fills
        }

        val span = max(1L, endMs - startMs)
        val sungSpan = max(1L, (span * 0.88f).toLong())
        val progress = ((positionMs - startMs).toFloat() / sungSpan.toFloat()).coerceIn(0f, 1f)
        val weights = FloatArray(count) { (words[it].length + 1).toFloat() }
        val total = weights.sum()
        var accumulated = 0f
        for (i in 0 until count) {
            val from = accumulated / total
            accumulated += weights[i]
            val to = accumulated / total
            fills[i] = if (to <= from) 1f else ((progress - from) / (to - from)).coerceIn(0f, 1f)
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
