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
        val title = cleanTitle(track.title)
        val artist = track.artist.takeUnless { it.isBlank() || it.equals("Unknown artist", true) }
        val seconds = (track.durationMs / 1000L).toInt()

        // 1) exact match on title + artist (+ album + length)
        if (artist != null) {
            val q = linkedMapOf("track_name" to title, "artist_name" to artist)
            track.album.takeUnless { it.isBlank() || it.equals("Unknown album", true) }?.let { q["album_name"] = it }
            if (seconds > 0) q["duration"] = seconds.toString()
            val exact = getObject("https://lrclib.net/api/get?${query(q)}")
            exact?.let { toState(it) }?.let { return it }

            // 2) title + artist search
            val list = getArray("https://lrclib.net/api/search?${query(mapOf("track_name" to title, "artist_name" to artist))}")
            pick(list, seconds)?.let { return it }
        }

        // 3) free-text search on the title alone (file names like "Artist - Song")
        val list = getArray("https://lrclib.net/api/search?${query(mapOf("q" to title))}")
        return pick(list, seconds)
    }

    /** Prefers results with synced lyrics and a length within 3 seconds of the song. */
    private fun pick(results: JSONArray?, seconds: Int): LyricsState? {
        if (results == null) return null
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val hasSynced = o.optString("syncedLyrics", "").isNotBlank()
            val hasPlain = o.optString("plainLyrics", "").isNotBlank()
            if (!hasSynced && !hasPlain) continue
            val diff = if (seconds > 0) abs(o.optDouble("duration", 0.0) - seconds) else 0.0
            if (seconds > 0 && diff > 3.0) continue
            val score = (if (hasSynced) 100 else 0) - diff.toInt()
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        return best?.let { toState(it) }
    }

    private fun toState(o: JSONObject): LyricsState? {
        if (o.optBoolean("instrumental", false)) return null
        val synced = Lrc.parse(o.optString("syncedLyrics", ""))
        if (synced.isNotEmpty()) return LyricsState.Synced(synced)
        val plain = o.optString("plainLyrics", "").trim()
        return if (plain.isNotEmpty()) LyricsState.Plain(plain) else null
    }

    /** Removes "(feat. ...)", "[Official Video]" and file-name noise that confuses searches. */
    private fun cleanTitle(raw: String): String {
        var t = raw.replace('_', ' ')
        t = t.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), " ")
        t = t.replace(Regex("""(?i)\b(official|lyric|lyrics|video|audio|hd|hq|4k)\b"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim().trim('-').trim()
        return t.ifBlank { raw.trim() }
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
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
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
                is LyricsState.Synced -> o.put("k", "synced").put("v", state.lines.joinToString("\n") { "[${stamp(it.timeMs)}]${it.text}" })
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
        val cs = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
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
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val MAX_FILES = 300
        const val MAX_BODY_CHARS = 600_000
    }
}
