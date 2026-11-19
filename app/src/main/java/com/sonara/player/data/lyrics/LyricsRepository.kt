package com.sonara.player.data.lyrics

import android.content.Context
import android.util.LruCache
import com.sonara.player.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Looks up time-synced lyrics on lrclib.net (a free, open lyrics database, no account needed).
 *
 * Privacy: a lookup sends the song's title, artist, album and length to lrclib.net, nothing else.
 * It only happens when the user has lyrics turned on in Settings. Results (and misses) are cached
 * on the phone so the same song is never requested twice in a row.
 */
class LyricsRepository(context: Context) {

    private val cacheDir = File(context.applicationContext.cacheDir, "lyrics").apply { mkdirs() }
    private val memory = LruCache<String, LyricsState>(40)

    suspend fun load(track: Track): LyricsState = withContext(Dispatchers.IO) {
        val key = hash("${track.id}|${track.title}|${track.artist}")
        memory.get(key)?.let { return@withContext it }
        readDisk(key)?.let {
            memory.put(key, it)
            return@withContext it
        }
        try {
            val found = lookup(track)
            val state = found ?: LyricsState.NotFound
            memory.put(key, state)
            writeDisk(key, state)
            state
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsState.Failed // not cached: try again next time
        }
    }

    // ---------- lookup ----------

    private fun lookup(track: Track): LyricsState? {
        val rawTitle = track.title.trim()
        val title = LyricsMatching.cleanTitle(rawTitle)
        val artist = track.artist.takeUnless { it.isBlank() || it.equals("Unknown artist", true) }
        val seconds = (track.durationMs / 1000L).toInt()
        val album = track.album.takeUnless { it.isBlank() || it.equals("Unknown album", true) }

        // One precise lookup avoids a chain of slow near-duplicate requests. If it misses,
        // the search endpoint below provides alternatives that are validated just as strictly.
        if (artist != null && title.isNotBlank()) {
            val exactParams = linkedMapOf("track_name" to rawTitle, "artist_name" to artist).apply {
                if (album != null) put("album_name", album)
                if (seconds > 0) put("duration", seconds.toString())
            }
            val exact = getObject("https://lrclib.net/api/get?${query(exactParams)}")
            if (exact != null && matchesTrack(exact, rawTitle, artist, seconds)) {
                toState(exact)?.let { return it }
            }
        }

        // Structured search first. A title-only fallback is allowed only after the same strict
        // candidate checks, preventing lyrics for similarly named tracks by another artist.
        val structured = if (artist != null) {
            getArray("https://lrclib.net/api/search?${query(mapOf("track_name" to title, "artist_name" to LyricsMatching.cleanArtist(artist)))}")
        } else null
        pick(structured, rawTitle, artist, seconds)?.let { return it }

        val queryText = listOfNotNull(artist, title).joinToString(" ")
        val fallback = getArray("https://lrclib.net/api/search?${query(mapOf("q" to queryText))}")
        return pick(fallback, rawTitle, artist, seconds)
    }

    /** Only accept candidates whose title/artist/version match. Duration is a ranking signal,
     * not the sole identity check: 8 seconds is preferred; up to 30 seconds is a fallback. */
    private fun pick(
        results: JSONArray?,
        requestedTitle: String,
        requestedArtist: String?,
        seconds: Int,
    ): LyricsState? {
        if (results == null) return null
        data class Candidate(val deltaSeconds: Double, val state: LyricsState)
        val candidates = ArrayList<Candidate>()
        for (index in 0 until results.length()) {
            val record = results.optJSONObject(index) ?: continue
            if (!matchesTrack(record, requestedTitle, requestedArtist, seconds)) continue
            val state = toState(record) ?: continue
            val recordDuration = record.optDouble("duration", 0.0)
            val delta = if (seconds > 0 && recordDuration > 0.0) abs(recordDuration - seconds) else 0.0
            candidates += Candidate(delta, state)
        }
        if (candidates.isEmpty()) return null

        // Prefer synced lyrics at close durations, then synced lyrics at a looser duration,
        // and use plain lyrics only when no well-matched synced record exists.
        val close = candidates.filter { it.deltaSeconds <= STRICT_DURATION_DELTA }.sortedBy { it.deltaSeconds }
        close.firstOrNull { it.state is LyricsState.Synced }?.let { return it.state }
        val relaxed = candidates.filter { it.deltaSeconds <= RELAXED_DURATION_DELTA }.sortedBy { it.deltaSeconds }
        relaxed.firstOrNull { it.state is LyricsState.Synced }?.let { return it.state }
        close.firstOrNull { it.state is LyricsState.Plain }?.let { return it.state }
        relaxed.firstOrNull { it.state is LyricsState.Plain }?.let { return it.state }
        return null
    }

    private fun matchesTrack(record: JSONObject, requestedTitle: String, requestedArtist: String?, seconds: Int): Boolean {
        val candidateTitle = record.optString("trackName", "").ifBlank { record.optString("name", "") }
        val candidateArtist = record.optString("artistName", "")
        if (!LyricsMatching.matches(candidateTitle, candidateArtist, requestedTitle, requestedArtist)) return false
        val candidateDuration = record.optDouble("duration", 0.0)
        if (seconds > 0 && candidateDuration > 0.0 && abs(candidateDuration - seconds) > RELAXED_DURATION_DELTA) return false
        return true
    }

    private fun toState(o: JSONObject): LyricsState? {
        if (o.optBoolean("instrumental", false)) return null
        val synced = Lrc.parse(o.optString("syncedLyrics", ""))
        if (synced.isNotEmpty()) return LyricsState.Synced(synced)
        val plain = o.optString("plainLyrics", "").trim()
        return if (plain.isNotEmpty()) LyricsState.Plain(plain) else null
    }

    // ---------- http ----------

    private fun query(params: Map<String, String>): String =
        params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** null on 404 (not found). Throws on network problems so the caller can retry later. */
    private fun getObject(url: String): JSONObject? = request(url)?.let { JSONObject(it) }

    private fun getArray(url: String): JSONArray? = request(url)?.let { JSONArray(it) }

    private fun request(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            // LRCLIB asks apps to identify themselves.
            conn.setRequestProperty("User-Agent", "Sonara/1.0 (Android music player)")
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8 * 1024)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (sb.length > MAX_BODY_CHARS) throw IOException("Response too large")
                }
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    // ---------- disk cache (hits kept 30 days, misses 1 day) ----------

    private fun readDisk(key: String): LyricsState? {
        val f = File(cacheDir, "$key.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val saved = o.optLong("t", 0L)
            val age = System.currentTimeMillis() - saved
            when (o.optString("k")) {
                "synced" -> if (age < 30 * DAY_MS) LyricsState.Synced(Lrc.parse(o.optString("v"))) else null
                "plain" -> if (age < 30 * DAY_MS) LyricsState.Plain(o.optString("v")) else null
                "none" -> if (age < DAY_MS) LyricsState.NotFound else null
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeDisk(key: String, state: LyricsState) {
        try {
            val o = JSONObject().put("t", System.currentTimeMillis())
            when (state) {
                is LyricsState.Synced -> o.put("k", "synced").put(
                    "v",
                    state.lines.joinToString("\n") { line ->
                        val words = LyricsTiming.words(line.text)
                        val enhancedText = if (words.isNotEmpty() && line.wordTimesMs.size == words.size) {
                            words.indices.joinToString(" ") { index ->
                                "<${stamp(line.wordTimesMs[index])}>${words[index]}"
                            }
                        } else {
                            line.text
                        }
                        "[${stamp(line.timeMs)}]$enhancedText"
                    },
                )
                is LyricsState.Plain -> o.put("k", "plain").put("v", state.text)
                LyricsState.NotFound -> o.put("k", "none")
                else -> return
            }
            File(cacheDir, "$key.json").writeText(o.toString())
            trimCache()
        } catch (e: Exception) {
            // The cache is only an optimisation.
        }
    }

    private fun stamp(ms: Long): String {
        val m = ms / 60_000
        val s = (ms % 60_000) / 1000
        val millis = ms % 1000
        return "%02d:%02d.%03d".format(m, s, millis)
    }

    private fun trimCache() {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    fun clearCache() {
        memory.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val STRICT_DURATION_DELTA = 8.0
        const val RELAXED_DURATION_DELTA = 30.0
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_FILES = 300
        const val MAX_BODY_CHARS = 600_000
    }
}
