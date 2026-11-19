package com.sonara.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Jamendo v3 read-only catalogue client.
 *
 * All API calls run on Dispatchers.IO. Metadata is kept in a short-lived in-memory cache;
 * Media3's existing play cache continues to cache streamed audio separately.
 */
class JamendoRepository(private val clientId: String) {

    val isConfigured: Boolean get() = clientId.isNotBlank()

    private data class CacheEntry<T>(val savedAt: Long, val value: T)

    private val trackCache = ConcurrentHashMap<String, CacheEntry<List<Track>>>()
    private val albumCache = ConcurrentHashMap<String, CacheEntry<List<JamendoAlbum>>>()
    private val artistCache = ConcurrentHashMap<String, CacheEntry<List<JamendoArtist>>>()
    private val playlistCache = ConcurrentHashMap<String, CacheEntry<List<JamendoPlaylist>>>()

    private val trackTtlMs = 10 * 60 * 1000L
    private val catalogueTtlMs = 30 * 60 * 1000L

    suspend fun trending(limit: Int = 30, offset: Int = 0): Result<List<Track>> = tracks(
        "tracks",
        limit,
        offset,
        "order" to "popularity_week",
        "type" to "single albumtrack",
    )

    suspend fun newReleases(limit: Int = 30, offset: Int = 0): Result<List<Track>> = tracks(
        "tracks",
        limit,
        offset,
        "order" to "releasedate_desc",
        "type" to "single albumtrack",
    )

    suspend fun byMood(tag: String, limit: Int = 30, offset: Int = 0): Result<List<Track>> = tracks(
        "tracks",
        limit,
        offset,
        "fuzzytags" to tag,
        "boost" to "popularity_month",
        "type" to "single albumtrack",
    )

    suspend fun byGenre(tag: String, limit: Int = 30, offset: Int = 0): Result<List<Track>> =
        byMood(tag, limit, offset)

    suspend fun search(text: String, limit: Int = 40, offset: Int = 0): Result<List<Track>> =
        tracks("tracks", limit, offset, "search" to text, "type" to "single albumtrack")

    suspend fun searchAlbums(text: String, limit: Int = 20, offset: Int = 0): Result<List<JamendoAlbum>> = cached(
        albumCache, cacheKey("albumSearch:$text", limit, offset), catalogueTtlMs,
    ) {
        parseAlbums(
            requestJson(
                "albums",
                listOf(
                    "namesearch" to text,
                    "type" to "album",
                    "imagesize" to "600",
                    "limit" to limit.coerceIn(1, 200).toString(),
                    "offset" to offset.coerceAtLeast(0).toString(),
                ),
            ).optJSONArray("results") ?: JSONArray()
        )
    }

    suspend fun searchArtists(text: String, limit: Int = 20, offset: Int = 0): Result<List<JamendoArtist>> = cached(
        artistCache, cacheKey("artistSearch:$text", limit, offset), catalogueTtlMs,
    ) {
        parseArtists(
            requestJson(
                "artists",
                listOf(
                    "namesearch" to text,
                    "hasimage" to "true",
                    "limit" to limit.coerceIn(1, 200).toString(),
                    "offset" to offset.coerceAtLeast(0).toString(),
                ),
            ).optJSONArray("results") ?: JSONArray()
        )
    }

    suspend fun searchPlaylists(text: String, limit: Int = 20, offset: Int = 0): Result<List<JamendoPlaylist>> = cached(
        playlistCache, cacheKey("playlistSearch:$text", limit, offset), catalogueTtlMs,
    ) {
        parsePlaylists(
            requestJson(
                "playlists",
                listOf(
                    "namesearch" to text,
                    "limit" to limit.coerceIn(1, 200).toString(),
                    "offset" to offset.coerceAtLeast(0).toString(),
                ),
            ).optJSONArray("results") ?: JSONArray()
        )
    }

    suspend fun trackById(jamendoId: String): Result<Track?> =
        tracks("tracks", 1, 0, "id" to jamendoId).map { it.firstOrNull() }

    /** Jamendo's native similar-track endpoint; [trackId] is the numeric Jamendo ID. */
    suspend fun similar(trackId: String, limit: Int = 30, offset: Int = 0): Result<List<Track>> {
        val numericId = trackId.removePrefix("jam:").trim()
        if (numericId.isBlank() || numericId.any { !it.isDigit() }) {
            return Result.failure(IllegalArgumentException("Similar tracks require an online Jamendo track."))
        }
        return tracks("tracks/similar", limit, offset, "id" to numericId)
    }

    /** Trending chart filtered by the artist's declared country (PHL = Philippines). */
    suspend fun filipinoTracks(limit: Int = 30, offset: Int = 0): Result<List<Track>> = cached(
        trackCache,
        cacheKey("filipinoTracks", limit, offset),
        trackTtlMs,
    ) {
        val chart = requestJson(
            "charts/track",
            listOf("country" to "PHL", "limit" to limit.toString(), "offset" to offset.toString()),
        ).optJSONArray("results") ?: JSONArray()
        val ids = (0 until chart.length()).mapNotNull { i ->
            chart.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        if (ids.isEmpty()) emptyList()
        else {
            val byId = parseTracks(
                requestJson(
                    "tracks",
                    listOf(
                        "id" to ids.joinToString(" "),
                        "type" to "single albumtrack",
                        "limit" to ids.size.toString(),
                        "imagesize" to "600",
                    ),
                )
                    .optJSONArray("results") ?: JSONArray()
            ).associateBy { it.id.removePrefix("jam:") }
            ids.mapNotNull(byId::get)
        }
    }

    /** Artists who declared the Philippines as their reference location. */
    suspend fun filipinoArtists(limit: Int = 24, offset: Int = 0): Result<List<JamendoArtist>> = cached(
        artistCache,
        cacheKey("filipinoArtists", limit, offset),
        catalogueTtlMs,
    ) {
        parseArtists(
            requestJson(
                "artists/locations",
                listOf(
                    "location_country" to "PHL",
                    "haslocation" to "true",
                    "hasimage" to "true",
                    "order" to "popularity_week",
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
            ).optJSONArray("results") ?: JSONArray(),
            includeLocations = true,
        )
    }

    suspend fun popularAlbums(limit: Int = 24, offset: Int = 0): Result<List<JamendoAlbum>> = albums(
        limit, offset, "popularity_week"
    )

    suspend fun latestAlbums(limit: Int = 24, offset: Int = 0): Result<List<JamendoAlbum>> = albums(
        limit, offset, "releasedate_desc"
    )

    private suspend fun albums(limit: Int, offset: Int, order: String): Result<List<JamendoAlbum>> = cached(
        albumCache,
        cacheKey("albums:$order", limit, offset),
        catalogueTtlMs,
    ) {
        parseAlbums(
            requestJson(
                "albums",
                listOf(
                    "order" to order,
                    "type" to "album",
                    "imagesize" to "600",
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
            ).optJSONArray("results") ?: JSONArray()
        )
    }

    suspend fun popularArtists(limit: Int = 24, offset: Int = 0): Result<List<JamendoArtist>> = cached(
        artistCache,
        cacheKey("popularArtists", limit, offset),
        catalogueTtlMs,
    ) {
        parseArtists(
            requestJson(
                "artists",
                listOf(
                    "order" to "popularity_week",
                    "hasimage" to "true",
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
            ).optJSONArray("results") ?: JSONArray()
        )
    }

    /** Jamendo's hot selection is an ID list; resolve IDs through /playlists/ for display metadata. */
    suspend fun featuredPlaylists(limit: Int = 16): Result<List<JamendoPlaylist>> = cached(
        playlistCache,
        cacheKey("featuredPlaylists", limit, 0),
        catalogueTtlMs,
    ) {
        val hot = requestJson("playlists/hots", listOf("limit" to limit.toString()))
            .optJSONArray("results") ?: JSONArray()
        val ids = (0 until hot.length()).mapNotNull { i ->
            hot.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        if (ids.isEmpty()) emptyList()
        else parsePlaylists(
            requestJson("playlists", listOf("id" to ids.joinToString(" "), "limit" to ids.size.toString()))
                .optJSONArray("results") ?: JSONArray()
        ).sortedBy { item -> ids.indexOf(item.id).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    suspend fun playlistTracks(playlistId: String, limit: Int = 100, offset: Int = 0): Result<List<Track>> =
        nestedTracks("playlists/tracks", playlistId, "playlist", limit, offset)

    suspend fun albumTracks(albumId: String, limit: Int = 100, offset: Int = 0): Result<List<Track>> =
        nestedTracks("albums/tracks", albumId, "album", limit, offset)

    suspend fun artistTracks(artistId: String, limit: Int = 100, offset: Int = 0): Result<List<Track>> =
        nestedTracks("artists/tracks", artistId, "artist", limit, offset)

    private suspend fun nestedTracks(
        endpoint: String,
        id: String,
        type: String,
        limit: Int,
        offset: Int,
    ): Result<List<Track>> = cached(trackCache, cacheKey("$type:$id", limit, offset), trackTtlMs) {
        val result = requestJson(
            endpoint,
            listOf(
                "id" to id,
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                if (type == "album") "type" to "album" else "track_type" to "single albumtrack",
                "imagesize" to "600",
            ),
        ).optJSONArray("results") ?: JSONArray()
        val tracks = ArrayList<Track>()
        for (i in 0 until result.length()) {
            val parent = result.optJSONObject(i) ?: continue
            val nested = parent.optJSONArray("tracks")
            if (nested != null) {
                val parentArtist = parent.optString("artist_name", "Unknown artist")
                when (type) {
                    "artist" -> tracks += parseTracks(nested, parent.optString("name", parentArtist), null)
                    "album" -> tracks += parseTracks(nested, parentArtist, parent.optString("name", ""))
                    else -> tracks += parseTracks(nested)
                }
            } else if (parent.has("audio")) {
                tracks += parseTracks(JSONArray().put(parent))
            }
        }
        tracks.distinctBy { it.id }
    }

    private suspend fun tracks(
        endpoint: String,
        limit: Int,
        offset: Int,
        vararg extra: Pair<String, String>,
    ): Result<List<Track>> {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val extras = extra.toList() + listOf(
            "limit" to safeLimit.toString(),
            "offset" to safeOffset.toString(),
        )
        val key = cacheKey(endpoint + extras.joinToString("|") { "${it.first}=${it.second}" }, safeLimit, safeOffset)
        return cached(trackCache, key, trackTtlMs) {
            parseTracks(requestJson(endpoint, extras).optJSONArray("results") ?: JSONArray())
        }
    }

    private suspend fun <T : Any> cached(
        cache: ConcurrentHashMap<String, CacheEntry<T>>,
        key: String,
        ttlMs: Long,
        load: () -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured) throw IOException("Online music is not configured")
            val now = System.currentTimeMillis()
            val existing = cache[key]
            val value = if (existing != null && now - existing.savedAt < ttlMs) {
                existing.value
            } else {
                load().also { cache[key] = CacheEntry(now, it) }
            }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun requestJson(endpoint: String, extra: List<Pair<String, String>>): JSONObject {
        if (!isConfigured) throw IOException("Online music is not configured")
        val params = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
        )
        if (endpoint.startsWith("tracks") || endpoint == "albums/tracks" || endpoint == "artists/tracks" || endpoint == "playlists/tracks") {
            params["audioformat"] = "mp31"
            params["audiodlformat"] = "mp32"
            params["imagesize"] = "600"
        }
        extra.forEach { params[it.first] = it.second }
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val url = URL("https://api.jamendo.com/v3.0/$endpoint/?$query")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Sonara-Android/1.0")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Jamendo returned HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val headers = root.optJSONObject("headers")
            if (headers != null && headers.optString("status") != "success") {
                throw IOException(headers.optString("error_message").ifBlank { "Jamendo request failed" })
            }
            return root
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTracks(array: JSONArray, artistOverride: String? = null, albumOverride: String? = null): List<Track> {
        val out = ArrayList<Track>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val audio = o.optString("audio", "")
            val jamendoId = o.optString("id", "")
            if (audio.isBlank() || jamendoId.isBlank()) continue
            val image = o.optString("image", "").ifBlank { o.optString("album_image", "") }
            val allowed = o.optBoolean("audiodownload_allowed", false)
            val downloadUrl = if (allowed) o.optString("audiodownload", "").ifBlank { null }?.https() else null
            out += Track(
                id = "jam:$jamendoId",
                title = o.optString("name", "Untitled"),
                artist = artistOverride ?: o.optString("artist_name", "Unknown artist"),
                album = albumOverride ?: o.optString("album_name", ""),
                durationMs = o.optLong("duration", 0L) * 1000L,
                uri = audio.https(),
                artworkUri = image.ifBlank { null },
                source = TrackSource.ONLINE,
                licenseUrl = o.optString("license_ccurl", "").ifBlank { null }?.https(),
                shareUrl = o.optString("shareurl", "").ifBlank { null }?.https(),
                downloadUrl = downloadUrl,
            )
        }
        return out
    }

    private fun parseAlbums(array: JSONArray): List<JamendoAlbum> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            add(
                JamendoAlbum(
                    id = id,
                    name = o.optString("name", "Untitled album"),
                    artistId = o.optString("artist_id", ""),
                    artistName = o.optString("artist_name", "Unknown artist"),
                    imageUrl = o.optString("image", "").ifBlank { null },
                    releaseDate = o.optString("releasedate", ""),
                    shareUrl = o.optString("shareurl", "").ifBlank { null },
                )
            )
        }
    }

    private fun parseArtists(array: JSONArray, includeLocations: Boolean = false): List<JamendoArtist> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            val location = if (includeLocations) o.optJSONArray("locations")?.optJSONObject(0) else null
            add(
                JamendoArtist(
                    id = id,
                    name = o.optString("name", "Unknown artist"),
                    imageUrl = o.optString("image", "").ifBlank { null },
                    countryCode = location?.optString("country")?.ifBlank { null },
                    city = location?.optString("city")?.ifBlank { null },
                    shareUrl = o.optString("shareurl", "").ifBlank { null },
                )
            )
        }
    }

    private fun parsePlaylists(array: JSONArray): List<JamendoPlaylist> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            add(
                JamendoPlaylist(
                    id = id,
                    name = o.optString("name", "Untitled playlist"),
                    creatorName = o.optString("user_name", "Jamendo artist"),
                    shareUrl = o.optString("shareurl", "").ifBlank { null },
                )
            )
        }
    }

    private fun cacheKey(name: String, limit: Int, offset: Int): String = "$name:$limit:$offset"
    private fun String.https(): String = replace("http://", "https://")
    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
