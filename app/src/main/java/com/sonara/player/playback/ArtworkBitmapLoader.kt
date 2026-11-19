package com.sonara.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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
 *
 * The notification and the lock-screen session wait for this loader every time the song changes,
 * so it is built to never hold them up:
 *  - covers are cached, so a song that was loaded before (or prefetched, see [prefetch]) is answered
 *    instantly and the new title AND cover show up together;
 *  - the same cover is never loaded twice at once;
 *  - a cover that can't be loaded gives a plain placeholder instead of a failure, so the
 *    notification and lock screen always finish updating to the new song.
 */
@OptIn(UnstableApi::class)
class ArtworkBitmapLoader(context: Context) : BitmapLoader {

    private val appContext = context.applicationContext
    private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(3))

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val inFlight = HashMap<String, ListenableFuture<Bitmap>>()

    private val placeholder: Bitmap by lazy {
        Bitmap.createBitmap(PLACEHOLDER_PX, PLACEHOLDER_PX, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.rgb(0x1F, 0x24, 0x30))
        }
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> {
            decodeSampled(data)?.let { shrink(it) } ?: throw IOException("Could not decode artwork bytes")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        cache.get(key)?.let { return Futures.immediateFuture(it) }
        var started = false
        val future = synchronized(inFlight) {
            inFlight[key] ?: startLoad(key, uri).also {
                inFlight[key] = it
                started = true
            }
        }
        // Registered after the future is in the map, so the clean-up can never run before the entry exists.
        if (started) {
            future.addListener({ synchronized(inFlight) { inFlight.remove(key) } }, MoreExecutors.directExecutor())
        }
        return future
    }

    private fun startLoad(key: String, uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable<Bitmap> {
            val bitmap = try {
                load(uri)
            } catch (e: Throwable) {
                null
            }
            if (bitmap != null) {
                cache.put(key, bitmap)
                bitmap
            } else {
                placeholder // not cached, so a later try can still succeed
            }
        })

    /**
     * Starts loading the cover of [item] in the background, so it is ready the moment the
     * notification needs it. Safe to call as often as you like.
     */
    fun prefetch(item: MediaItem?) {
        val uri = item?.mediaMetadata?.artworkUri ?: return
        if (cache.get(uri.toString()) != null) return
        loadBitmap(uri)
    }

    private fun load(uri: Uri): Bitmap? {
        val raw = when (uri.scheme) {
            "content", "file" -> loadLocal(uri)
            "http", "https" -> loadRemote(uri)
            else -> null
        } ?: return null
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
            appContext.contentResolver.openInputStream(uri)?.use { it.readCapped() }?.let { decodeSampled(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadRemote(uri: Uri): Bitmap? {
        val conn = URL(uri.toString()).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 6_000
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { it.readCapped() }?.let { decodeSampled(it) }
        } finally {
            conn.disconnect()
        }
    }

    /** Decodes at no more than about [MAX_PX], so a huge cover never costs a huge bitmap. */
    private fun decodeSampled(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Reads at most [MAX_BYTES]; null if the data is bigger (not a normal cover). */
    private fun InputStream.readCapped(): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
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
        const val PLACEHOLDER_PX = 256
        const val MAX_BYTES = 8 * 1024 * 1024
        const val CACHE_BYTES = 12 * 1024 * 1024
    }
}
