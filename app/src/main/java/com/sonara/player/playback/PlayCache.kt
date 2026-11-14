package com.sonara.player.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The "play cache": online songs are saved on the phone while they stream, so playing them again
 * (or after a connection drop) doesn't use data. One shared instance for the whole process,
 * because two caches on the same folder are not allowed.
 *
 * Eviction is done by [trim] (using the limit from Settings) instead of by the cache itself,
 * so a changed limit takes effect straight away.
 */
@OptIn(UnstableApi::class)
object PlayCache {

    @Volatile private var cache: SimpleCache? = null
    private var failed = false

    @Synchronized
    fun getOrNull(context: Context): SimpleCache? {
        cache?.let { return it }
        if (failed) return null
        return try {
            val app = context.applicationContext
            SimpleCache(File(app.cacheDir, "play-cache"), NoOpCacheEvictor(), StandaloneDatabaseProvider(app))
                .also { cache = it }
        } catch (e: Exception) {
            failed = true
            null
        }
    }

    fun usedBytes(context: Context): Long = getOrNull(context)?.cacheSpace ?: 0L

    /** Deletes the least recently played songs until the cache fits [limitBytes]. */
    @Synchronized
    fun trim(context: Context, limitBytes: Long, protectedKeys: Set<String> = emptySet()) {
        val c = getOrNull(context) ?: return
        val total = c.cacheSpace
        if (total <= limitBytes) return
        val entries = c.keys.map { key ->
            val spans = c.getCachedSpans(key)
            CacheTrimPlanner.Entry(
                key = key,
                bytes = spans.sumOf { it.length },
                lastTouch = spans.maxOfOrNull { it.lastTouchTimestamp } ?: 0L,
            )
        }
        for (key in CacheTrimPlanner.keysToEvict(entries, total, limitBytes, protectedKeys)) {
            try {
                c.removeResource(key)
            } catch (e: Exception) {
                // Still in use; it will go on a later pass.
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        val c = getOrNull(context) ?: return
        for (key in c.keys.toList()) {
            try {
                c.removeResource(key)
            } catch (e: Exception) {
                // Still in use; ignore.
            }
        }
    }
}
