package com.sonara.player.playback

import java.util.Locale

/** What the player reports about the audio it is playing right now. Any field can be unknown. */
data class AudioSpecs(
    val mimeType: String? = null,
    val bitrateBps: Int? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
) {
    /** "FLAC", "MP3", "AAC", "Opus"... or null when unknown. */
    val codec: String? get() = codecLabel(mimeType)

    /** Short badge text such as "FLAC · 987 kbps · 44.1 kHz". Null when nothing is known. */
    val badge: String?
        get() {
            val parts = listOfNotNull(codec, bitrateLabel(bitrateBps), sampleRateLabel(sampleRateHz))
            return if (parts.isEmpty()) null else parts.joinToString(" \u2022 ")
        }
}

fun codecLabel(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    return when (val m = mime.lowercase(Locale.ROOT)) {
        "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "MP3"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/flac" -> "FLAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/raw" -> "WAV"
        "audio/alac" -> "ALAC"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        "audio/amr-wb", "audio/3gpp" -> "AMR"
        else -> m.substringAfter('/').substringAfter("x-").ifBlank { null }?.uppercase(Locale.ROOT)
    }
}

fun bitrateLabel(bps: Int?): String? {
    if (bps == null || bps <= 0) return null
    return "${(bps + 500) / 1000} kbps"
}

fun sampleRateLabel(hz: Int?): String? {
    if (hz == null || hz <= 0) return null
    val khz = hz / 1000.0
    val text = if (hz % 1000 == 0) String.format(Locale.US, "%.0f", khz) else String.format(Locale.US, "%.1f", khz)
    return "$text kHz"
}

fun channelsLabel(channels: Int?): String? = when (channels) {
    null -> null
    1 -> "Mono"
    2 -> "Stereo"
    else -> if (channels > 2) "$channels channels" else null
}

/** "60 Hz", "910 Hz", "3.6 kHz", "14 kHz". */
fun hzLabel(hz: Int): String =
    if (hz < 1000) "$hz Hz"
    else if (hz % 1000 == 0) "${hz / 1000} kHz"
    else String.format(Locale.US, "%.1f kHz", hz / 1000.0)

/** Millibels as decibels with a sign: 600 -> "+6.0 dB". */
fun dbLabel(mb: Int): String = String.format(Locale.US, "%+.1f dB", mb / 100.0)
