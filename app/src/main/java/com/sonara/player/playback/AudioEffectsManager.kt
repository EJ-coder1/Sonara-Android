package com.sonara.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.sonara.player.data.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Audio sheet shows. Unsupported effects are simply hidden by the UI. */
data class FxState(
    val equalizerSupported: Boolean = false,
    val enabled: Boolean = false,
    val bandHz: List<Int> = emptyList(),
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val levelsMb: List<Int> = emptyList(),
    val preset: String = EqPresets.CUSTOM,
    val bassSupported: Boolean = false,
    val bassStrength: Int = 0,
    val loudnessSupported: Boolean = false,
    val loudnessMb: Int = 0,
)

/**
 * Equalizer, bass boost and loudness boost, attached to the player's audio session.
 * Lives in [com.sonara.player.AppContainer]; the playback service attaches it, the UI controls it.
 * Every call into the system effects is guarded: some phones don't support some effects.
 */
class AudioEffectsManager(private val store: LibraryStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(FxState())
    val state: StateFlow<FxState> = _state.asStateFlow()

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var attachGeneration = 0

    /** Safe to call repeatedly (the service calls it on creation and when the session id changes). */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        val generation = ++attachGeneration
        scope.launch {
            val saved = store.fx.first()
            if (generation != attachGeneration) return@launch // a newer attach superseded this one
            releaseEffects()
            build(audioSessionId, saved)
        }
    }

    fun release() {
        attachGeneration++
        releaseEffects()
        _state.value = FxState()
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { loud?.release() }
        eq = null
        bass = null
        loud = null
    }

    private fun build(sessionId: Int, saved: FxSaved) {
        var next = FxState(enabled = saved.enabled)

        try {
            val e = Equalizer(0, sessionId)
            val bands = e.getNumberOfBands().toInt()
            val range = e.getBandLevelRange()
            val min = range[0].toInt()
            val max = range[1].toInt()
            val hz = List(bands) { e.getCenterFreq(it.toShort()) / 1000 }
            val levels = if (saved.levelsMb.size == bands) saved.levelsMb.map { it.coerceIn(min, max) } else List(bands) { 0 }
            levels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.toShort()) }
            e.setEnabled(saved.enabled)
            eq = e
            next = next.copy(
                equalizerSupported = bands > 0,
                bandHz = hz,
                minMb = min,
                maxMb = max,
                levelsMb = levels,
                preset = EqPresets.matching(levels, min, max),
            )
        } catch (e: Exception) {
            eq = null
        }

        try {
            val b = BassBoost(0, sessionId)
            if (b.getStrengthSupported()) {
                b.setStrength(saved.bassStrength.toShort())
                b.setEnabled(saved.enabled && saved.bassStrength > 0)
                bass = b
                next = next.copy(bassSupported = true, bassStrength = saved.bassStrength)
            } else {
                b.release()
            }
        } catch (e: Exception) {
            bass = null
        }

        try {
            val l = LoudnessEnhancer(sessionId)
            l.setTargetGain(saved.loudnessMb)
            l.setEnabled(saved.enabled && saved.loudnessMb > 0)
            loud = l
            next = next.copy(loudnessSupported = true, loudnessMb = saved.loudnessMb)
        } catch (e: Exception) {
            loud = null
        }

        _state.value = next
    }

    // ---------- controls ----------

    fun setEnabled(on: Boolean) {
        val s = _state.value
        runCatching { eq?.setEnabled(on) }
        runCatching { bass?.setEnabled(on && s.bassStrength > 0) }
        runCatching { loud?.setEnabled(on && s.loudnessMb > 0) }
        _state.update { it.copy(enabled = on) }
        commit()
    }

    /** Live change while a slider moves. Call [commit] when the finger lifts. */
    fun setBandLevel(index: Int, levelMb: Int) {
        val s = _state.value
        if (index !in s.levelsMb.indices) return
        val clamped = levelMb.coerceIn(s.minMb, s.maxMb)
        runCatching { eq?.setBandLevel(index.toShort(), clamped.toShort()) }
        val levels = s.levelsMb.toMutableList().also { it[index] = clamped }
        _state.update { it.copy(levelsMb = levels, preset = EqPresets.matching(levels, s.minMb, s.maxMb)) }
    }

    fun applyPreset(name: String) {
        val s = _state.value
        if (s.levelsMb.isEmpty()) return
        val levels = EqPresets.levelsMb(name, s.levelsMb.size, s.minMb, s.maxMb).toList()
        levels.forEachIndexed { i, l -> runCatching { eq?.setBandLevel(i.toShort(), l.toShort()) } }
        _state.update { it.copy(levelsMb = levels, preset = EqPresets.matching(levels, s.minMb, s.maxMb)) }
        commit()
    }

    fun setBass(strength: Int) {
        val v = strength.coerceIn(0, 1000)
        runCatching { bass?.setStrength(v.toShort()) }
        runCatching { bass?.setEnabled(_state.value.enabled && v > 0) }
        _state.update { it.copy(bassStrength = v) }
    }

    fun setLoudness(levelMb: Int) {
        val v = levelMb.coerceIn(0, 1500)
        runCatching { loud?.setTargetGain(v) }
        runCatching { loud?.setEnabled(_state.value.enabled && v > 0) }
        _state.update { it.copy(loudnessMb = v) }
    }

    /** Saves the current settings. */
    fun commit() {
        val s = _state.value
        val saved = FxSaved(s.enabled, s.bassStrength, s.loudnessMb, s.levelsMb)
        scope.launch { store.setFx(saved) }
    }
}
