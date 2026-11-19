package com.sonara.player.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata for a worldwide-storefront search result. These entries are not playable Sonara
 * streams: tapping one opens its official Apple/iTunes store page.
 */
data class GlobalCatalogTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val storefront: String,
    val storeUrl: String,
    val durationMs: Long,
)

/** Search-provider contract. Additional official catalog providers can implement this interface. */
interface MusicCatalogSearchProvider {
    val providerId: String
    suspend fun search(query: String, storefront: String): Result<List<GlobalCatalogTrack>>
}

/**
 * Apple/iTunes public Search API adapter. It is used for catalog metadata and official store links
 * only. Sonara deliberately does not use previewUrl or extract audio from Apple/YouTube/Spotify.
 */
class ItunesCatalogSearchProvider : MusicCatalogSearchProvider {
    override val providerId: String = "apple-itunes"

    private data class CacheEntry(val savedAt: Long, val value: List<GlobalCatalogTrack>)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun search(query: String, storefront: String): Result<List<GlobalCatalogTrack>> {
        val cleanQuery = query.trim()
        val country = storefront.trim().uppercase(Locale.ROOT)
        if (cleanQuery.length < 2) return Result.success(emptyList())
        if (!country.matches(Regex("[A-Z]{2}"))) {
            return Result.failure(IllegalArgumentException("A two-letter storefront code is required."))
        }

        val key = "$country|${cleanQuery.lowercase(Locale.ROOT)}"
        cache[key]?.takeIf { System.currentTimeMillis() - it.savedAt < CACHE_TTL_MS }?.let {
            return Result.success(it.value)
        }

        return try {
            val found = runInterruptible(Dispatchers.IO) {
                val encoded = URLEncoder.encode(cleanQuery, Charsets.UTF_8.name())
                val endpoint = "$SEARCH_URL?term=$encoded&country=$country&media=music&entity=song&limit=$PAGE_SIZE"
                val connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Sonara-Android/1.1 (music catalog search)")
                }
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) throw IOException("Apple catalog returned HTTP $status.")
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseSearchResponse(body, country)
                } finally {
                    connection.disconnect()
                }
            }
            if (cache.size >= MAX_CACHE_ENTRIES) {
                val oldestCutoff = System.currentTimeMillis() - CACHE_TTL_MS
                cache.entries.removeIf { it.value.savedAt < oldestCutoff }
                if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
            }
            cache[key] = CacheEntry(System.currentTimeMillis(), found)
            Result.success(found)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseSearchResponse(body: String, storefront: String): List<GlobalCatalogTrack> {
        val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val title = item.optString("trackName").trim()
                val artist = item.optString("artistName").trim()
                val storeUrl = item.optString("trackViewUrl").trim()
                if (title.isBlank() || artist.isBlank() || !isTrustedStoreUrl(storeUrl)) continue
                val trackId = item.optLong("trackId", -1L)
                if (trackId <= 0L) continue
                add(
                    GlobalCatalogTrack(
                        id = "apple:$storefront:$trackId",
                        title = title,
                        artist = artist,
                        album = item.optString("collectionName").trim(),
                        storefront = storefront,
                        storeUrl = storeUrl,
                        durationMs = item.optLong("trackTimeMillis", 0L).coerceAtLeast(0L),
                    ),
                )
            }
        }
    }

    private fun isTrustedStoreUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host.equals("music.apple.com", ignoreCase = true) ||
                uri.host.equals("itunes.apple.com", ignoreCase = true))
    }.getOrDefault(false)

    private companion object {
        const val SEARCH_URL = "https://itunes.apple.com/search"
        const val PAGE_SIZE = 16
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
        const val CACHE_TTL_MS = 15 * 60 * 1000L
        const val MAX_CACHE_ENTRIES = 80
    }
}

/**
 * Small provider registry for global catalog metadata. It queries the user's storefront first,
 * then a few additional major storefronts to broaden discovery beyond the local chart.
 */
class WorldwideMusicCatalog(
    private val providers: List<MusicCatalogSearchProvider> = listOf(ItunesCatalogSearchProvider()),
) {
    suspend fun search(query: String, primaryStorefront: String): Result<List<GlobalCatalogTrack>> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return Result.success(emptyList())
        if (providers.isEmpty()) return Result.success(emptyList())

        val storefronts = worldwideStorefronts(primaryStorefront)
        return try {
            val pages = coroutineScope {
                providers.flatMap { provider ->
                    storefronts.map { storefront ->
                        async { provider.search(cleanQuery, storefront) }
                    }
                }.awaitAll()
            }
            val successfulPages = pages.mapNotNull { it.getOrNull() }
            if (successfulPages.isEmpty()) {
                Result.failure(pages.firstNotNullOfOrNull { it.exceptionOrNull() }
                    ?: IOException("No music catalog provider returned results."))
            } else {
                Result.success(mergeWorldwideCatalogPages(successfulPages))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

/** Keeps the user's storefront first and adds PH/US/GB/KR discovery coverage without duplicates. */
internal fun worldwideStorefronts(primaryStorefront: String): List<String> {
    val primary = primaryStorefront.trim().uppercase(Locale.ROOT)
        .takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "PH"
    return listOf(primary, "PH", "US", "GB", "KR").distinct()
}

/** Earlier storefronts win; the same title/artist is shown once even if stores return different IDs. */
internal fun mergeWorldwideCatalogPages(pages: List<List<GlobalCatalogTrack>>): List<GlobalCatalogTrack> {
    val seen = HashSet<String>()
    return pages.flatten().filter { track ->
        val key = "${track.title.trim().lowercase(Locale.ROOT)}|${track.artist.trim().lowercase(Locale.ROOT)}"
        key.isNotBlank() && seen.add(key)
    }.take(60)
}

sealed interface GlobalCatalogState {
    data object Idle : GlobalCatalogState
    data object Loading : GlobalCatalogState
    data class Ready(val tracks: List<GlobalCatalogTrack>) : GlobalCatalogState
    data class Error(val message: String) : GlobalCatalogState
}
