package com.sonara.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sonara.player.playback.FxSaved
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.sonaraStore: DataStore<Preferences> by preferencesDataStore(name = "sonara_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent colors the user can pick (used when "Use wallpaper colors" is off). */
enum class Accent(val label: String) {
    VIOLET("Violet"), CRIMSON("Crimson"), OCEAN("Ocean"), SAGE("Sage"), AMBER("Amber"), ROSE("Rose"), MONO("Mono"),
}

/** Look-and-feel and lyrics options, saved on the phone. */
data class UiPrefs(
    val amoled: Boolean = false,
    val accent: Accent = Accent.VIOLET,
    /** Animated, blurred artwork behind the player. */
    val fluidBackdrop: Boolean = true,
    /** Wavy seek bar (off = a plain straight bar). */
    val wavySeekBar: Boolean = true,
    /** Fetch lyrics from lrclib.net (sends song title, artist, album and length). Off until the user opts in. */
    val lyricsEnabled: Boolean = false,
    /** 85, 100 or 120 (percent). */
    val lyricsScalePercent: Int = 100,
    /** Positive = lyrics appear later. */
    val lyricsOffsetMs: Int = 0,
)

/**
 * Everything the user creates lives only on their device:
 * favorites, recently played, playlists and settings. No account, no cloud.
 */
class LibraryStore(context: Context) {
    private val ds = context.applicationContext.sonaraStore

    private val keyFavorites = stringPreferencesKey("favorites")
    private val keyRecents = stringPreferencesKey("recents")
    private val keyPlaylists = stringPreferencesKey("playlists")
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyDynamic = booleanPreferencesKey("dynamic_color")
    private val keyOnboarding = booleanPreferencesKey("onboarding_done")
    private val keyAmoled = booleanPreferencesKey("amoled")
    private val keyAccent = stringPreferencesKey("accent")
    private val keyBackdrop = booleanPreferencesKey("fluid_backdrop")
    private val keyWavy = booleanPreferencesKey("wavy_seekbar")
    private val keyLyrics = booleanPreferencesKey("lyrics_enabled")
    private val keyLyricsScale = intPreferencesKey("lyrics_scale")
    private val keyLyricsOffset = intPreferencesKey("lyrics_offset_ms")
    private val keyFx = stringPreferencesKey("audio_fx")
    private val keyBatteryHint = booleanPreferencesKey("battery_hint_shown")

    val favorites: Flow<List<Track>> = ds.data.map { parseTracks(it[keyFavorites]) }
    val recents: Flow<List<Track>> = ds.data.map { parseTracks(it[keyRecents]) }
    val playlists: Flow<List<Playlist>> = ds.data.map { parsePlaylists(it[keyPlaylists]) }

    val themeMode: Flow<ThemeMode> = ds.data.map {
        runCatching { ThemeMode.valueOf(it[keyTheme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    val dynamicColor: Flow<Boolean> = ds.data.map { it[keyDynamic] ?: false }
    val onboardingDone: Flow<Boolean> = ds.data.map { it[keyOnboarding] ?: false }

    val uiPrefs: Flow<UiPrefs> = ds.data.map { p ->
        UiPrefs(
            amoled = p[keyAmoled] ?: false,
            accent = runCatching { Accent.valueOf(p[keyAccent] ?: Accent.VIOLET.name) }.getOrDefault(Accent.VIOLET),
            fluidBackdrop = p[keyBackdrop] ?: true,
            wavySeekBar = p[keyWavy] ?: true,
            lyricsEnabled = p[keyLyrics] ?: false,
            lyricsScalePercent = (p[keyLyricsScale] ?: 100).coerceIn(70, 140),
            lyricsOffsetMs = (p[keyLyricsOffset] ?: 0).coerceIn(-5000, 5000),
        )
    }
    val fx: Flow<FxSaved> = ds.data.map { FxSaved.decode(it[keyFx]) }
    val batteryHintShown: Flow<Boolean> = ds.data.map { it[keyBatteryHint] ?: false }

    suspend fun toggleFavorite(track: Track) {
        ds.edit { p ->
            val list = parseTracks(p[keyFavorites]).toMutableList()
            val i = list.indexOfFirst { it.id == track.id }
            if (i >= 0) list.removeAt(i) else list.add(0, track)
            p[keyFavorites] = list.toJsonArray().toString()
        }
    }

    suspend fun addRecent(track: Track) {
        ds.edit { p ->
            val list = parseTracks(p[keyRecents]).filterNot { it.id == track.id }.toMutableList()
            list.add(0, track)
            p[keyRecents] = list.take(MAX_RECENTS).toJsonArray().toString()
        }
    }

    suspend fun clearRecents() {
        ds.edit { it.remove(keyRecents) }
    }

    // ----- playlists -----

    suspend fun createPlaylist(name: String, first: Track? = null): String {
        val id = UUID.randomUUID().toString()
        ds.edit { p ->
            val list = parsePlaylists(p[keyPlaylists]).toMutableList()
            list += Playlist(id, name.trim().take(60), listOfNotNull(first))
            p[keyPlaylists] = list.toJsonString()
        }
        return id
    }

    suspend fun renamePlaylist(id: String, name: String) = updatePlaylists { list ->
        list.map { if (it.id == id) it.copy(name = name.trim().take(60)) else it }
    }

    suspend fun deletePlaylist(id: String) = updatePlaylists { list -> list.filterNot { it.id == id } }

    /** Returns false when the track was already in the playlist. */
    suspend fun addToPlaylist(playlistId: String, track: Track): Boolean {
        var added = false
        updatePlaylists { list ->
            list.map { pl ->
                if (pl.id == playlistId && pl.tracks.none { it.id == track.id }) {
                    added = true
                    pl.copy(tracks = pl.tracks + track)
                } else pl
            }
        }
        return added
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) = updatePlaylists { list ->
        list.map { pl -> if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl }
    }

    private suspend fun updatePlaylists(transform: (List<Playlist>) -> List<Playlist>) {
        ds.edit { p -> p[keyPlaylists] = transform(parsePlaylists(p[keyPlaylists])).toJsonString() }
    }

    // ----- settings -----

    suspend fun setThemeMode(mode: ThemeMode) { ds.edit { it[keyTheme] = mode.name } }
    suspend fun setDynamicColor(enabled: Boolean) { ds.edit { it[keyDynamic] = enabled } }
    suspend fun setOnboardingDone() { ds.edit { it[keyOnboarding] = true } }
    suspend fun setAmoled(on: Boolean) { ds.edit { it[keyAmoled] = on } }
    suspend fun setAccent(accent: Accent) { ds.edit { it[keyAccent] = accent.name } }
    suspend fun setFluidBackdrop(on: Boolean) { ds.edit { it[keyBackdrop] = on } }
    suspend fun setWavySeekBar(on: Boolean) { ds.edit { it[keyWavy] = on } }
    suspend fun setLyricsEnabled(on: Boolean) { ds.edit { it[keyLyrics] = on } }
    suspend fun setLyricsScale(percent: Int) { ds.edit { it[keyLyricsScale] = percent.coerceIn(70, 140) } }
    suspend fun setLyricsOffset(ms: Int) { ds.edit { it[keyLyricsOffset] = ms.coerceIn(-5000, 5000) } }
    suspend fun setFx(fx: FxSaved) { ds.edit { it[keyFx] = fx.encode() } }
    suspend fun setBatteryHintShown() { ds.edit { it[keyBatteryHint] = true } }

    /** Wipes everything the app saved (used by Settings > Delete my data). */
    suspend fun clearAll() { ds.edit { it.clear() } }

    private companion object {
        const val MAX_RECENTS = 30
    }
}
