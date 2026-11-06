package com.sonara.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free, artist-licensed (Creative Commons) music from Jamendo.
 * Needs a free client ID from https://devportal.jamendo.com  (see README).
 */
class JamendoRepository(private val clientId: String) {

    val isConfigured: Boolean get() = clientId.isNotBlank()

    suspend fun trending(limit: Int = 20) =
        query(limit, "order" to "popularity_week")

    suspend fun newReleases(limit: Int = 20) =
        query(limit, "order" to "releasedate_desc")

    suspend fun byMood(tag: String, limit: Int = 20) =
        query(limit, "fuzzytags" to tag, "order" to "popularity_month")

    suspend fun search(text: String, limit: Int = 30) =
        query(limit, "search" to text) // default order = relevance

    private suspend fun query(
        limit: Int,
        vararg extra: Pair<String, String>,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetch(limit, extra))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetch(limit: Int, extra: Array<out Pair<String, String>>): List<Track> {
        if (!isConfigured) throw IOException("Online music is not configured")
        val params = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
            "limit" to limit.toString(),
            "imagesize" to "600",
            "audioformat" to "mp31",
        )
        extra.forEach { params[it.first] = it.second }
        val qs = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        val conn = URL("https://api.jamendo.com/v3.0/tracks/?$qs").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): List<Track> {
        val root = JSONObject(body)
        val headers = root.optJSONObject("headers")
        if (headers != null && headers.optString("status") != "success") {
            throw IOException(headers.optString("error_message", "Music service error"))
        }
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val audio = o.optString("audio", "")
            val id = o.optString("id", "")
            if (audio.isBlank() || id.isBlank()) continue
            val image = o.optString("image", "").ifBlank { o.optString("album_image", "") }
            out += Track(
                id = "jam:$id",
                title = o.optString("name", "Untitled"),
                artist = o.optString("artist_name", "Unknown artist"),
                album = o.optString("album_name", ""),
                durationMs = o.optLong("duration", 0L) * 1000L,
                uri = audio.replace("http://", "https://"),
                artworkUri = image.ifBlank { null },
                source = TrackSource.ONLINE,
                licenseUrl = o.optString("license_ccurl", "").ifBlank { null },
                shareUrl = o.optString("shareurl", "").ifBlank { null },
            )
        }
        return out
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
