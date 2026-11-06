package com.sonara.player.playback

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioSpecsTest {

    @Test
    fun codecLabels() {
        assertEquals("MP3", codecLabel("audio/mpeg"))
        assertEquals("AAC", codecLabel("audio/mp4a-latm"))
        assertEquals("FLAC", codecLabel("audio/flac"))
        assertEquals("Opus", codecLabel("audio/opus"))
        assertEquals("XYZ", codecLabel("audio/x-xyz"))
        assertNull(codecLabel(null))
        assertNull(codecLabel(""))
    }

    @Test
    fun badgeJoinsKnownParts() {
        val s = AudioSpecs("audio/flac", 987_000, 44_100, 2)
        assertEquals("FLAC \u2022 987 kbps \u2022 44.1 kHz", s.badge)
        assertEquals("MP3 \u2022 320 kbps \u2022 48 kHz", AudioSpecs("audio/mpeg", 320_000, 48_000, 2).badge)
        assertEquals("Opus", AudioSpecs(mimeType = "audio/opus").badge)
        assertNull(AudioSpecs().badge)
    }

    @Test
    fun labelsRoundSensibly() {
        assertEquals("133 kbps", bitrateLabel(132_800))
        assertNull(bitrateLabel(-1))
        assertNull(bitrateLabel(0))
        assertEquals("Stereo", channelsLabel(2))
        assertEquals("Mono", channelsLabel(1))
        assertEquals("6 channels", channelsLabel(6))
        assertNull(channelsLabel(null))
    }

    @Test
    fun frequencyAndGainLabels() {
        assertEquals("60 Hz", hzLabel(60))
        assertEquals("910 Hz", hzLabel(910))
        assertEquals("3.6 kHz", hzLabel(3600))
        assertEquals("14 kHz", hzLabel(14000))
        assertEquals("+6.0 dB", dbLabel(600))
        assertEquals("-1.5 dB", dbLabel(-150))
        assertEquals("+0.0 dB", dbLabel(0))
    }
}
