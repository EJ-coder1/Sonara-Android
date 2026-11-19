package com.sonara.player.playback

import android.content.Context
import android.content.SharedPreferences

/** What Settings > Crossfade holds. */
data class CrossfadeSettings(
    val enabled: Boolean = CrossfadePrefs.DEFAULT_ENABLED,
    val seconds: Int = CrossfadeMath.DEFAULT_SECONDS,
) {
    /** True when songs should really overlap. */
    val active: Boolean get() = enabled && seconds > 0
}

/**
 * Crossfade settings in SharedPreferences. The settings screen writes them and the playback
 * service reads them (same process, same file), so a change applies from the next song on.
 */
class CrossfadePrefs private constructor(private val prefs: SharedPreferences) {

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var seconds: Int
        get() = CrossfadeMath.clampSeconds(prefs.getInt(KEY_SECONDS, CrossfadeMath.DEFAULT_SECONDS))
        set(value) { prefs.edit().putInt(KEY_SECONDS, CrossfadeMath.clampSeconds(value)).apply() }

    fun read(): CrossfadeSettings = CrossfadeSettings(enabled, seconds)

    companion object {
        const val DEFAULT_ENABLED = false
        private const val FILE = "sonara_crossfade"
        private const val KEY_ENABLED = "crossfade_enabled"
        private const val KEY_SECONDS = "crossfade_seconds"

        fun get(context: Context): CrossfadePrefs =
            CrossfadePrefs(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
