package com.sonara.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Loads the artwork shown in the notification, on the lock screen and on Bluetooth displays.
 *
 * Why this exists: the default loader cannot read phone-library art on Android 10+, so the
 * notification showed no cover (or a stale one) for songs from the phone. Here:
 *  - content:// / file://  -> the cover is read from the audio file itself (loadThumbnail)
 *  - https://              -> downloaded with short timeouts
 * A failure never breaks playback; the notification just shows text for that song.
 */
@OptIn(UnstableApi::class)
class ArtworkBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IOException("Could not decode artwork bytes")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> { load(uri) })

    private fun load(uri: Uri): Bitmap {
        val raw = when (uri.scheme) {
            "content", "file" -> loadLocal(uri)
            "http", "https" -> loadRemote(uri)
            else -> null
        } ?: throw IOException("No artwork for $uri")
        return shrink(raw)
    }

    private fun loadLocal(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return appContext.contentResolver.loadThumbnail(uri, Size(MAX_PX, MAX_PX), null)
            } catch (e: Exception) {
                // fall through to the stream reader below
            }
        }
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadRemote(uri: Uri): Bitmap? {
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** Notifications don't need huge bitmaps; big ones can fail to cross the system boundary. */
    private fun shrink(src: Bitmap): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= MAX_PX) return src
        val scale = MAX_PX.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val MAX_PX = 512
    }
}
