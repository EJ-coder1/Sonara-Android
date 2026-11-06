package com.sonara.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sonara.player.data.Track
import com.sonara.player.data.toMediaItem
import com.sonara.player.data.toTrackOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    /** true when the user wants playback running (even while buffering). */
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val sleepRemainingMs: Long = 0L,
    val error: String? = null,
    /** Playback speed and pitch, 1.0 = normal. */
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** In-app volume, 0..1 (separate from the phone's volume keys). */
    val volume: Float = 1f,
    /** What the decoder reports about the song that is playing; null until known. */
    val audioSpecs: AudioSpecs? = null,
)

/**
 * The UI's single door to playback. It talks to [PlaybackService] through a MediaController
 * and exposes everything the screens need as one [StateFlow].
 */
class PlayerController(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var queue: List<Track> = emptyList()
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var lastRecordedId: String? = null

    /** Called once each time a new track begins (used for "Recently played"). */
    var onTrackStarted: ((Track) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    /** Safe to call many times. */
    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                sync()
                while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            } catch (e: Exception) {
                future = null
                pending.clear()
                _state.update { it.copy(error = "Couldn't start the music player. Please restart the app.") }
            }
        }, MoreExecutors.directExecutor())
        startTicker()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            action(c)
        } else {
            pending.addLast(action)
            connect()
        }
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null && c.isPlaying) {
                    val pos = c.currentPosition.coerceAtLeast(0L)
                    _state.update { it.copy(positionMs = pos) }
                }
                delay(250)
            }
        }
    }

    private fun sync() {
        val c = controller ?: return
        // The playback service may already hold a queue this controller never saw (for example
        // after the screen was recreated). Rebuild our copy from it so the UI never goes blank
        // while the notification is still playing.
        if (queue.isEmpty() && c.mediaItemCount > 0) {
            val rebuilt = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toTrackOrNull() }
            if (rebuilt.size == c.mediaItemCount) queue = rebuilt
        }
        val mediaId = c.currentMediaItem?.mediaId
        val cur = if (mediaId != null) {
            queue.firstOrNull { it.id == mediaId } ?: c.currentMediaItem?.toTrackOrNull()
        } else null
        if (cur != null && cur.id != lastRecordedId) {
            lastRecordedId = cur.id
            onTrackStarted?.invoke(cur)
        }
        val dur = c.duration
        val params = c.playbackParameters
        val specs = readSpecs(c)
        _state.update {
            it.copy(
                current = cur,
                isPlaying = c.isPlaying,
                playWhenReady = c.playWhenReady,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = if (dur != C.TIME_UNSET && dur > 0L) dur else (cur?.durationMs ?: 0L),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                queueIndex = if (cur != null) c.currentMediaItemIndex else -1,
                speed = params.speed,
                pitch = params.pitch,
                volume = c.volume,
                audioSpecs = specs,
            )
        }
    }

    private fun readSpecs(c: MediaController): AudioSpecs? {
        val group = c.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected } ?: return null
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val f = group.getTrackFormat(i)
            val bitrate = listOf(f.bitrate, f.averageBitrate, f.peakBitrate).firstOrNull { it > 0 }
            return AudioSpecs(
                mimeType = f.sampleMimeType ?: f.containerMimeType,
                bitrateBps = bitrate,
                sampleRateHz = f.sampleRate.takeIf { it > 0 },
                channels = f.channelCount.takeIf { it > 0 },
            )
        }
        return null
    }

    /** Only shows a message. Skipping to the next song is done by [PlaybackService] so it also works with the screen off. */
    private fun handleError(error: PlaybackException) {
        val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        _state.update {
            it.copy(
                error = if (network) "Can't reach the internet. Check your connection."
                else "This track can't be played. Skipping."
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------- queue ----------

    /**
     * Replaces the queue and starts playing [tracks] at [startIndex].
     * [shuffle]: null keeps the user's current shuffle setting.
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val start = if (shuffle == true) Random.nextInt(tracks.size) else startIndex.coerceIn(0, tracks.lastIndex)
        val (list, idx) = window(tracks, start)
        withController { c ->
            queue = list
            if (shuffle != null) c.shuffleModeEnabled = shuffle
            c.setMediaItems(list.map { it.toMediaItem() }, idx, 0L)
            c.prepare()
            c.play()
            sync()
        }
    }

    fun playTrack(track: Track) = playQueue(listOf(track), 0)

    fun playNext(track: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) {
                playQueue(listOf(track))
                return@withController
            }
            val at = (c.currentMediaItemIndex + 1).coerceIn(0, queue.size)
            queue = queue.toMutableList().apply { add(at, track) }
            c.addMediaItem(at, track.toMediaItem())
            sync()
        }
    }

    fun addToQueue(track: Track) {
        withController { c ->
            if (c.mediaItemCount == 0) {
                playQueue(listOf(track))
                return@withController
            }
            queue = queue + track
            c.addMediaItem(track.toMediaItem())
            sync()
        }
    }

    fun removeFromQueue(index: Int) {
        withController { c ->
            if (index !in queue.indices || index == c.currentMediaItemIndex) return@withController
            queue = queue.toMutableList().apply { removeAt(index) }
            c.removeMediaItem(index)
            sync()
        }
    }

    fun skipToQueueItem(index: Int) {
        withController { c ->
            if (index in queue.indices) {
                c.seekTo(index, 0L)
                c.play()
            }
        }
    }

    // ---------- transport ----------

    fun togglePlayPause() {
        withController { c ->
            val state = c.playbackState
            if (c.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
                c.pause()
            } else {
                if (state == Player.STATE_IDLE) c.prepare()
                else if (state == Player.STATE_ENDED) c.seekToDefaultPosition()
                c.play()
            }
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        withController { c ->
            c.seekTo(positionMs.coerceAtLeast(0L))
            _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        }
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() {
        withController { c ->
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---------- audio controls ----------

    /** Speed and pitch, each 0.5x..2.0x. Pass the same value for both to get the "vinyl" effect. */
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        val sp = speed.coerceIn(0.5f, 2f)
        val pi = pitch.coerceIn(0.5f, 2f)
        withController { c ->
            c.setPlaybackParameters(PlaybackParameters(sp, pi))
            sync()
        }
    }

    /** In-app volume, 0..1. */
    fun setVolume(volume: Float) {
        withController { c ->
            c.volume = volume.coerceIn(0f, 1f)
            sync()
        }
    }

    // ---------- sleep timer ----------

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepJob = scope.launch {
            while (isActive) {
                val remaining = endAt - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    controller?.pause()
                    _state.update { it.copy(sleepRemainingMs = 0L) }
                    break
                }
                _state.update { it.copy(sleepRemainingMs = remaining) }
                delay(1000)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _state.update { it.copy(sleepRemainingMs = 0L) }
    }

    // ---------- helpers ----------

    /** Keeps very large libraries manageable: at most [MAX_QUEUE] items around the tapped song. */
    private fun window(tracks: List<Track>, start: Int): Pair<List<Track>, Int> {
        if (tracks.size <= MAX_QUEUE) return tracks to start
        val from = (start - 50).coerceAtLeast(0)
        val to = (from + MAX_QUEUE).coerceAtMost(tracks.size)
        return tracks.subList(from, to) to (start - from)
    }

    private companion object {
        const val MAX_QUEUE = 500
    }
}
