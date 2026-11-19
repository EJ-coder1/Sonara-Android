package com.sonara.player.playback

/**
 * The numbers behind crossfade. Pure Kotlin (no Android), so it is covered by unit tests.
 *
 * Terms: the OUTGOING song is the one that is ending, the INCOMING song is the next one.
 * Fade lengths are wall-clock milliseconds; positions are song milliseconds.
 */
object CrossfadeMath {
    const val MIN_SECONDS = 0
    const val MAX_SECONDS = 12
    const val DEFAULT_SECONDS = 5

    fun clampSeconds(seconds: Int): Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /**
     * How long the overlap really lasts for a song of [durationMs] played at [speed].
     * It is the length chosen in Settings, but never more than half of the song, so a short song
     * is not faded out before it has been heard. 0 means "no crossfade for this song".
     */
    fun fadeMs(seconds: Int, durationMs: Long, speed: Float): Long {
        val wanted = clampSeconds(seconds) * 1000L
        if (wanted == 0L || durationMs <= 0L || speed <= 0f) return 0L
        val longestAllowed = (durationMs / (2f * speed)).toLong()
        return minOf(wanted, longestAllowed).coerceAtLeast(0L)
    }

    /**
     * Where in the outgoing song the overlap starts, so that it ends exactly when the song ends.
     * At 2x speed the last [fadeMs] of real time are the last 2 * [fadeMs] of song.
     */
    fun overlapStartMs(durationMs: Long, fadeMs: Long, speed: Float): Long =
        (durationMs - (fadeMs * speed).toLong()).coerceAtLeast(0L)

    /** 0.0 at the start of the overlap, 1.0 at the end. */
    fun progress(elapsedMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L) return 1f
        return (elapsedMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    /** The incoming song fades in 0.0 -> 1.0. */
    fun fadeInVolume(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** The outgoing song fades out 1.0 -> 0.0. */
    fun fadeOutVolume(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)
}
