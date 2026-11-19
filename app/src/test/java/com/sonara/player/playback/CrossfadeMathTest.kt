package com.sonara.player.playback

import org.junit.Test
import kotlin.test.assertEquals

class CrossfadeMathTest {

    @Test
    fun secondsAreKeptBetweenZeroAndTwelve() {
        assertEquals(0, CrossfadeMath.clampSeconds(-3))
        assertEquals(0, CrossfadeMath.clampSeconds(0))
        assertEquals(5, CrossfadeMath.clampSeconds(5))
        assertEquals(12, CrossfadeMath.clampSeconds(12))
        assertEquals(12, CrossfadeMath.clampSeconds(99))
    }

    @Test
    fun defaultIsFiveSeconds() {
        assertEquals(5, CrossfadeMath.DEFAULT_SECONDS)
        assertEquals(5, CrossfadeSettings().seconds)
    }

    @Test
    fun normalSongsUseTheChosenLength() {
        assertEquals(5_000L, CrossfadeMath.fadeMs(5, 200_000L, 1f))
        assertEquals(12_000L, CrossfadeMath.fadeMs(12, 200_000L, 1f))
        assertEquals(1_000L, CrossfadeMath.fadeMs(1, 200_000L, 1f))
    }

    @Test
    fun zeroSecondsOrUnknownLengthMeansNoFade() {
        assertEquals(0L, CrossfadeMath.fadeMs(0, 200_000L, 1f))
        assertEquals(0L, CrossfadeMath.fadeMs(5, 0L, 1f))
        assertEquals(0L, CrossfadeMath.fadeMs(5, -1L, 1f))
        assertEquals(0L, CrossfadeMath.fadeMs(5, 200_000L, 0f))
    }

    @Test
    fun theFadeIsNeverLongerThanHalfTheSong() {
        assertEquals(5_000L, CrossfadeMath.fadeMs(12, 10_000L, 1f))
        assertEquals(3_000L, CrossfadeMath.fadeMs(5, 6_000L, 1f))
    }

    @Test
    fun fasterPlaybackShortensTheLimit() {
        // At 2x, 5 seconds of real time are 10 seconds of song: half of a 20 second song.
        assertEquals(5_000L, CrossfadeMath.fadeMs(12, 20_000L, 2f))
    }

    @Test
    fun theOverlapEndsExactlyWhenTheSongEnds() {
        assertEquals(195_000L, CrossfadeMath.overlapStartMs(200_000L, 5_000L, 1f))
        assertEquals(190_000L, CrossfadeMath.overlapStartMs(200_000L, 5_000L, 2f))
        assertEquals(0L, CrossfadeMath.overlapStartMs(1_000L, 5_000L, 1f))
    }

    @Test
    fun progressRunsFromZeroToOneAndStaysInside() {
        assertEquals(0f, CrossfadeMath.progress(0L, 5_000L), 1e-6f)
        assertEquals(0.5f, CrossfadeMath.progress(2_500L, 5_000L), 1e-6f)
        assertEquals(1f, CrossfadeMath.progress(5_000L, 5_000L), 1e-6f)
        assertEquals(1f, CrossfadeMath.progress(9_999L, 5_000L), 1e-6f)
        assertEquals(0f, CrossfadeMath.progress(-5L, 5_000L), 1e-6f)
        assertEquals(1f, CrossfadeMath.progress(10L, 0L), 1e-6f)
    }

    @Test
    fun fadeInGoesZeroToOneAndFadeOutGoesOneToZero() {
        assertEquals(0f, CrossfadeMath.fadeInVolume(0f), 1e-6f)
        assertEquals(1f, CrossfadeMath.fadeInVolume(1f), 1e-6f)
        assertEquals(1f, CrossfadeMath.fadeOutVolume(0f), 1e-6f)
        assertEquals(0f, CrossfadeMath.fadeOutVolume(1f), 1e-6f)
    }

    @Test
    fun bothVolumesAlwaysAddUpToOne() {
        for (i in 0..20) {
            val p = i / 20f
            assertEquals(1f, CrossfadeMath.fadeInVolume(p) + CrossfadeMath.fadeOutVolume(p), 1e-6f)
        }
    }

    @Test
    fun settingsAreActiveOnlyWhenOnAndLongerThanZero() {
        assertEquals(false, CrossfadeSettings(enabled = false, seconds = 5).active)
        assertEquals(false, CrossfadeSettings(enabled = true, seconds = 0).active)
        assertEquals(true, CrossfadeSettings(enabled = true, seconds = 1).active)
    }
}
