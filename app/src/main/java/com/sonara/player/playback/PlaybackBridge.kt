package com.sonara.player.playback

import androidx.media3.exoplayer.ExoPlayer

/**
 * Lets the app change ExoPlayer options the media controller can't reach (the player lives in
 * PlaybackService, in the same process). Call from the main thread.
 */
object PlaybackBridge {
    private var player: ExoPlayer? = null
    private var skipSilence = false

    fun attach(player: ExoPlayer) {
        this.player = player
        player.setSkipSilenceEnabled(skipSilence)
    }

    fun detach() {
        player = null
    }

    /** Skip quiet gaps in songs and podcasts. Remembered for when the player is (re)created. */
    fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        player?.setSkipSilenceEnabled(enabled)
    }
}
