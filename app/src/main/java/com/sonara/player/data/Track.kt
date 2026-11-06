package com.sonara.player.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

enum class TrackSource { LOCAL, ONLINE }

data class Track(
    /** Unique and stable: "local:<mediaStoreId>" or "jam:<jamendoId>". */
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val artworkUri: String?,
    val source: TrackSource,
    val licenseUrl: String? = null,
    val shareUrl: String? = null,
    val addedAt: Long = 0L,
) {
    val isOnline: Boolean get() = source == TrackSource.ONLINE
}

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track>,
)

private const val X_SOURCE = "sonara.source"
private const val X_URI = "sonara.uri"
private const val X_ART = "sonara.art"
private const val X_LICENSE = "sonara.license"
private const val X_SHARE = "sonara.share"
private const val X_ADDED = "sonara.added"

/**
 * Everything the player and the notification need travels inside the MediaItem, so the UI can
 * rebuild the current [Track] from the playback service even if its own queue copy is empty.
 */
fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(X_SOURCE, source.name)
        putString(X_URI, uri)
        artworkUri?.let { putString(X_ART, it) }
        licenseUrl?.let { putString(X_LICENSE, it) }
        shareUrl?.let { putString(X_SHARE, it) }
        putLong(X_ADDED, addedAt)
    }
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
    if (durationMs > 0L) meta.setDurationMs(durationMs)
    // Local songs: the artwork is read from the audio file itself (see ArtworkBitmapLoader),
    // because the old "albumart" address stopped working on Android 10+.
    val art = if (source == TrackSource.LOCAL) uri else artworkUri
    art?.let { meta.setArtworkUri(Uri.parse(it)) }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(meta.build())
        .build()
}

/** Rebuilds a [Track] from a MediaItem made by [toMediaItem]. Null for items from elsewhere. */
fun MediaItem.toTrackOrNull(): Track? {
    val x = mediaMetadata.extras ?: return null
    val uri = x.getString(X_URI) ?: return null
    return Track(
        id = mediaId,
        title = mediaMetadata.title?.toString() ?: "Unknown title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
        album = mediaMetadata.albumTitle?.toString() ?: "",
        durationMs = mediaMetadata.durationMs ?: 0L,
        uri = uri,
        artworkUri = x.getString(X_ART),
        source = runCatching { TrackSource.valueOf(x.getString(X_SOURCE) ?: "") }.getOrDefault(TrackSource.LOCAL),
        licenseUrl = x.getString(X_LICENSE),
        shareUrl = x.getString(X_SHARE),
        addedAt = x.getLong(X_ADDED, 0L),
    )
}

// ---------- JSON (stored in DataStore) ----------

fun Track.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("dur", durationMs)
    put("uri", uri)
    if (artworkUri != null) put("art", artworkUri)
    put("src", source.name)
    if (licenseUrl != null) put("lic", licenseUrl)
    if (shareUrl != null) put("share", shareUrl)
    put("added", addedAt)
}

fun JSONObject.toTrack(): Track? = try {
    Track(
        id = getString("id"),
        title = getString("title"),
        artist = optString("artist", ""),
        album = optString("album", ""),
        durationMs = optLong("dur", 0L),
        uri = getString("uri"),
        artworkUri = optString("art", "").ifBlank { null },
        source = TrackSource.valueOf(optString("src", TrackSource.LOCAL.name)),
        licenseUrl = optString("lic", "").ifBlank { null },
        shareUrl = optString("share", "").ifBlank { null },
        addedAt = optLong("added", 0L),
    )
} catch (e: Exception) {
    null
}

fun List<Track>.toJsonArray(): JSONArray = JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

fun parseTracks(json: String?): List<Track> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toTrack() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun parsePlaylists(json: String?): List<Playlist> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val tracksArr = o.optJSONArray("tracks") ?: JSONArray()
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                tracks = (0 until tracksArr.length()).mapNotNull { tracksArr.optJSONObject(it)?.toTrack() },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun List<Playlist>.toJsonString(): String {
    val arr = JSONArray()
    forEach { p ->
        arr.put(
            JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("tracks", p.tracks.toJsonArray())
            }
        )
    }
    return arr.toString()
}
