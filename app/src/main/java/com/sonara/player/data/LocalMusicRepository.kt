package com.sonara.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the songs stored on the phone through MediaStore. Nothing leaves the device. */
class LocalMusicRepository(private val context: Context) {

    suspend fun loadAll(): List<Track> = withContext(Dispatchers.IO) {
        val collection: Uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // Only real music (skips ringtones, alarms, notification sounds) and ignores clips under 30s.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("30000")
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val albumArtBase = Uri.parse("content://media/external/audio/albumart")

        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val artist = c.getString(artistCol).cleanOrNull() ?: "Unknown artist"
                val album = c.getString(albumCol).cleanOrNull() ?: "Unknown album"
                out += Track(
                    id = "local:$id",
                    title = c.getString(titleCol).cleanOrNull() ?: "Unknown title",
                    artist = artist,
                    album = album,
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    artworkUri = ContentUris.withAppendedId(albumArtBase, albumId).toString(),
                    source = TrackSource.LOCAL,
                    addedAt = c.getLong(addedCol),
                )
            }
        }
        out
    }

    private fun String?.cleanOrNull(): String? {
        val s = this?.trim().orEmpty()
        return if (s.isEmpty() || s == "<unknown>") null else s
    }
}
