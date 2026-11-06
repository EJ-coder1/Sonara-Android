package com.sonara.player.playback

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Equalizer presets described as a gentle curve over five reference points (low to high pitch).
 * The curve is stretched over however many bands the phone's equalizer really has (usually 5,
 * sometimes 6 or 10), so one preset list works on every device.
 */
object EqPresets {
    const val CUSTOM = "Custom"

    /** Gain in dB at 5 reference points: bass, low-mid, mid, high-mid, treble. */
    private val curves: LinkedHashMap<String, FloatArray> = linkedMapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass boost" to floatArrayOf(6f, 4f, 0.5f, 0f, 0f),
        "Treble boost" to floatArrayOf(0f, 0f, 0.5f, 4f, 6f),
        "Vocal" to floatArrayOf(-2f, -0.5f, 3f, 3f, 0.5f),
        "Rock" to floatArrayOf(4.5f, 2f, -1f, 2.5f, 4.5f),
        "Electronic" to floatArrayOf(5f, 2.5f, 0f, 3f, 5f),
        "Acoustic" to floatArrayOf(3f, 2f, 1f, 2f, 2.5f),
    )

    val names: List<String> get() = curves.keys.toList()

    /**
     * Band levels in millibels for [preset] on a device with [bandCount] bands.
     * Values are clamped to the device range [minMb]..[maxMb]. Unknown names give a flat curve.
     */
    fun levelsMb(preset: String, bandCount: Int, minMb: Int, maxMb: Int): IntArray {
        if (bandCount <= 0) return IntArray(0)
        val curve = curves[preset] ?: curves.getValue("Flat")
        val lastRef = curve.size - 1
        return IntArray(bandCount) { band ->
            val pos = if (bandCount == 1) 0.5f else band.toFloat() / (bandCount - 1).toFloat()
            val scaled = pos * lastRef
            val lo = floor(scaled).toInt().coerceIn(0, lastRef)
            val hi = min(lo + 1, lastRef)
            val t = scaled - lo
            val db = curve[lo] + (curve[hi] - curve[lo]) * t
            (db * 100f).roundToInt().coerceIn(minMb, maxMb)
        }
    }

    /** Which preset the current levels match exactly, or [CUSTOM]. */
    fun matching(levels: List<Int>, minMb: Int, maxMb: Int): String {
        if (levels.isEmpty()) return CUSTOM
        for (name in curves.keys) {
            val expected = levelsMb(name, levels.size, minMb, maxMb)
            if (expected.toList() == levels) return name
        }
        return CUSTOM
    }
}
