package com.sonara.player.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Spotify-style crossfade with TWO players.
 *
 *  - [main] is the session player. It owns the queue, the notification, the lock screen and the UI,
 *    and it always plays the song the user should see as "now playing".
 *  - a second player ("standby") is only alive for a few seconds around each song change.
 *
 * How one overlap goes (the song that is ending = OUTGOING, the next one = INCOMING):
 *  1. A few seconds before the overlap, the standby is loaded with the OUTGOING song, parked
 *     (paused) at the exact position where the overlap begins, so it is ready to start instantly.
 *  2. When [main] reaches that position the standby starts playing the rest of the outgoing song,
 *     and [main] jumps to the next queue item with its volume at 0. From this moment the
 *     notification, lock screen and app already show the new song.
 *  3. For the next X seconds: standby volume 1.0 -> 0.0 (fade out), main volume 0.0 -> 1.0 (fade in).
 *  4. The standby is released and main is back at the user's normal volume.
 *
 * If the user skips, seeks past the overlap, pauses, or the queue changes, everything snaps back to
 * normal playback (see [cancel]). Pausing also pauses the standby and the fade clock.
 *
 * Must be created and used on the main thread (same as the ExoPlayer instances).
 */
class CrossfadeController(
    private val main: ExoPlayer,
    private val prefs: CrossfadePrefs,
    /** Builds the second player. It must NOT handle audio focus (the main player owns it). */
    private val newStandbyPlayer: () -> ExoPlayer,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var released = false

    // ---- the second player ----
    private var standby: ExoPlayer? = null
    private var standbyItemId: String? = null
    private var standbyReady = false
    private var overlapStartMs = 0L
    private var fadeMs = 0L
    /** A song whose standby failed: don't keep retrying for the same song. */
    private var giveUpOnId: String? = null

    // ---- the running crossfade ----
    private var fading = false
    private var incomingId: String? = null
    private var fadeElapsedMs = 0L
    private var lastTickAt = 0L

    /** The volume the user chose (the in-app volume slider); the fade scales around it. */
    private var baseVolume = main.volume
    private var lastSetVolume = Float.NaN

    private val mainListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            lastTickAt = SystemClock.uptimeMillis()
            if (fading) {
                standby?.playWhenReady = playWhenReady // pause / resume the old song together with the new one
            } else if (!playWhenReady) {
                releaseStandby() // paused: don't keep a second decoder around; it is prepared again on resume
            }
            reschedule()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) cancel()
            reschedule()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The jump we make ourselves at the start of the overlap is expected; anything else
            // (Next, Previous, a tap in the queue, a new queue) ends the crossfade.
            if (fading && mediaItem?.mediaId == incomingId) return
            cancel()
        }

        override fun onVolumeChanged(volume: Float) {
            // Ignore the volumes we set ourselves; remember the ones the user sets.
            if (fading && volume == lastSetVolume) return
            baseVolume = volume
        }
    }

    private val standbyListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && !fading && standby != null) {
                standbyReady = true
                reschedule()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Crossfade is a bonus. If the second player can't play this song, just play normally.
            giveUpOnId = standbyItemId
            releaseStandby()
        }
    }

    private val tick = Runnable {
        if (!released) {
            if (fading) advanceFade() else watchForOverlap()
            reschedule()
        }
    }

    init {
        main.addListener(mainListener)
        reschedule()
    }

    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        main.removeListener(mainListener)
        val wasFading = fading
        fading = false
        incomingId = null
        releaseStandby()
        if (wasFading) main.volume = baseVolume
    }

    // ---------- scheduling ----------

    /** Ticks only while something is playing, so an idle app does no work. */
    private fun reschedule() {
        handler.removeCallbacks(tick)
        if (released || !main.playWhenReady) return
        val state = main.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return
        handler.postDelayed(tick, nextDelayMs())
    }

    private fun nextDelayMs(): Long = when {
        fading -> FADE_TICK_MS
        // Close to the start of the overlap: check often so the second song starts on time.
        standbyReady && overlapStartMs - main.currentPosition <= NEAR_MS -> TRIGGER_TICK_MS
        else -> IDLE_TICK_MS
    }

    // ---------- before the overlap ----------

    private fun watchForOverlap() {
        val settings = prefs.read()
        if (!settings.active) {
            releaseStandby()
            return
        }
        val item = main.currentMediaItem
        if (item == null ||
            main.repeatMode == Player.REPEAT_MODE_ONE ||
            main.nextMediaItemIndex == C.INDEX_UNSET
        ) {
            releaseStandby() // nothing to fade into (last song, or the same song on repeat)
            return
        }
        if (item.mediaId == giveUpOnId) return

        if (standby != null && standbyItemId != item.mediaId) releaseStandby()

        val position = main.currentPosition
        if (standby == null) {
            val duration = main.duration
            if (duration == C.TIME_UNSET || duration <= 0L) return
            val speed = main.playbackParameters.speed
            val fade = CrossfadeMath.fadeMs(settings.seconds, duration, speed)
            if (fade <= 0L) return
            val start = CrossfadeMath.overlapStartMs(duration, fade, speed)
            // Prepare shortly before the overlap; too late (or too early) means no crossfade for now.
            if (position < start - PREPARE_MIN_LEAD_MS && start - position <= PREPARE_AHEAD_MS) {
                prepareStandby(item, start, fade)
            }
            return
        }

        if (!standbyReady) return
        val late = position - overlapStartMs
        if (late < 0L) return // not there yet
        if (late > MAX_LATE_MS) {
            releaseStandby() // the user jumped well past the start of the overlap: skip it
            return
        }
        beginCrossfade()
    }

    private fun prepareStandby(item: MediaItem, startMs: Long, fadeMs: Long) {
        val player = try {
            newStandbyPlayer()
        } catch (e: Exception) {
            giveUpOnId = item.mediaId
            return
        }
        standby = player
        standbyItemId = item.mediaId
        standbyReady = false
        overlapStartMs = startMs
        this.fadeMs = fadeMs
        player.volume = baseVolume
        player.playbackParameters = main.playbackParameters
        player.addListener(standbyListener)
        player.setMediaItem(item, startMs) // parked at the start of the overlap
        player.playWhenReady = false
        player.prepare()
    }

    // ---------- the overlap ----------

    private fun beginCrossfade() {
        val outgoing = standby ?: return
        val nextIndex = main.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) {
            releaseStandby()
            return
        }
        // Set the flags BEFORE moving, so main's own transition callback is recognised as ours.
        incomingId = main.getMediaItemAt(nextIndex).mediaId
        fading = true
        fadeElapsedMs = 0L
        lastTickAt = SystemClock.uptimeMillis()

        outgoing.volume = baseVolume
        outgoing.playWhenReady = true // the old song carries on...
        setMainVolume(0f)             // ...while the new song starts silent
        main.seekToNextMediaItem()    // the session (notification, lock screen, UI) moves on right now
    }

    private fun advanceFade() {
        val now = SystemClock.uptimeMillis()
        if (main.playWhenReady) fadeElapsedMs += now - lastTickAt // the clock stands still while paused
        lastTickAt = now
        val progress = CrossfadeMath.progress(fadeElapsedMs, fadeMs)
        setMainVolume(baseVolume * CrossfadeMath.fadeInVolume(progress))
        standby?.volume = baseVolume * CrossfadeMath.fadeOutVolume(progress)
        if (progress >= 1f) finishFade()
    }

    private fun finishFade() {
        fading = false
        incomingId = null
        giveUpOnId = null
        releaseStandby()
        setMainVolume(baseVolume)
    }

    /** Back to plain playback: no second player, the user's volume. */
    private fun cancel() {
        val wasFading = fading
        fading = false
        incomingId = null
        giveUpOnId = null
        releaseStandby()
        if (wasFading) setMainVolume(baseVolume)
    }

    // ---------- helpers ----------

    private fun setMainVolume(volume: Float) {
        lastSetVolume = volume
        main.volume = volume
    }

    private fun releaseStandby() {
        val player = standby ?: return
        standby = null
        standbyItemId = null
        standbyReady = false
        player.removeListener(standbyListener)
        player.release()
    }

    private companion object {
        const val IDLE_TICK_MS = 250L
        const val TRIGGER_TICK_MS = 15L
        const val FADE_TICK_MS = 40L

        /** Start preparing the second player this long (in song time) before the overlap begins. */
        const val PREPARE_AHEAD_MS = 8_000L

        /** Don't bother preparing if the overlap is closer than this: it would not be ready in time. */
        const val PREPARE_MIN_LEAD_MS = 300L

        /** Switch to fast ticking when the overlap is this close (song time). */
        const val NEAR_MS = 1_500L

        /** If main is further than this past the overlap start, the user seeked: skip this crossfade. */
        const val MAX_LATE_MS = 600L
    }
}
