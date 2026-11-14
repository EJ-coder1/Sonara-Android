package com.sonara.player.data

import org.json.JSONArray
import org.json.JSONObject

/** What was playing when Sonara was last used, so the next start can pick up where it left off. */
data class SessionSnapshot(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Picks which part of a long queue is saved. Pure logic, no Android needed. */
object SessionWindow {
    /** A saved queue never holds more than this many songs (keeps the saved data small). */
    const val MAX_TRACKS = 100

    /**
     * The songs around the one that is playing. Returns (from, toExclusive);
     * the playing song is always inside the range.
     */
    fun window(total: Int, index: Int, max: Int = MAX_TRACKS): Pair<Int, Int> {
        if (total <= max) return 0 to total
        val from = (index - max / 4).coerceIn(0, total - max)
        return from to (from + max)
    }
}

object SessionCodec {
    fun encode(s: SessionSnapshot): String = JSONObject().apply {
        put("v", 1)
        put("i", s.index)
        put("p", s.positionMs)
        put("s", s.shuffle)
        put("r", s.repeatMode)
        put("t", s.tracks.toJsonArray())
    }.toString()

    fun decode(json: String?): SessionSnapshot? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val arr = o.optJSONArray("t") ?: JSONArray()
            val tracks = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toTrack() }
            val index = o.optInt("i", 0)
            if (tracks.isEmpty() || index !in tracks.indices) return null
            SessionSnapshot(
                tracks = tracks,
                index = index,
                positionMs = o.optLong("p", 0L).coerceAtLeast(0L),
                shuffle = o.optBoolean("s", false),
                repeatMode = o.optInt("r", 0),
            )
        } catch (e: Exception) {
            null
        }
    }
}
