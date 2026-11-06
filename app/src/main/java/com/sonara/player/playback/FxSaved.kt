package com.sonara.player.playback

/** The audio-effect settings that are saved on the phone (equalizer, bass, loudness). */
data class FxSaved(
    val enabled: Boolean = false,
    /** 0..1000 */
    val bassStrength: Int = 0,
    /** 0..1500 millibels */
    val loudnessMb: Int = 0,
    /** One level per equalizer band, in millibels. Empty = flat. */
    val levelsMb: List<Int> = emptyList(),
) {
    fun encode(): String =
        "1|${if (enabled) 1 else 0}|$bassStrength|$loudnessMb|${levelsMb.joinToString(",")}"

    companion object {
        fun decode(raw: String?): FxSaved {
            if (raw.isNullOrBlank()) return FxSaved()
            return try {
                val p = raw.split('|')
                if (p.size < 5 || p[0] != "1") return FxSaved()
                FxSaved(
                    enabled = p[1] == "1",
                    bassStrength = p[2].toInt().coerceIn(0, 1000),
                    loudnessMb = p[3].toInt().coerceIn(0, 1500),
                    levelsMb = if (p[4].isBlank()) emptyList() else p[4].split(',').map { it.trim().toInt() },
                )
            } catch (e: NumberFormatException) {
                FxSaved()
            }
        }
    }
}
