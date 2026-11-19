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
/**
 * A color palette: a main color and a second color (the "pink" next to the "violet" in the default look).
 * [CUSTOM] uses the two colors the user picked in Theme Studio.
 */
enum class Accent(val label: String, val primary: Int, val secondary: Int) {
    VIOLET("Violet", 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()),
    ROSE("Rose", 0xFFEC4899.toInt(), 0xFFA855F7.toInt()),
    CRIMSON("Crimson", 0xFFE11D48.toInt(), 0xFFFB923C.toInt()),
    SUNSET("Sunset", 0xFFF97316.toInt(), 0xFFE11D48.toInt()),
    AMBER("Amber", 0xFFF59E0B.toInt(), 0xFFEF4444.toInt()),
    LIME("Lime", 0xFF84CC16.toInt(), 0xFF10B981.toInt()),
    SAGE("Sage", 0xFF6B9B7A.toInt(), 0xFFB5C99A.toInt()),
    TEAL("Teal", 0xFF14B8A6.toInt(), 0xFF3B82F6.toInt()),
    OCEAN("Ocean", 0xFF0EA5E9.toInt(), 0xFF6366F1.toInt()),
    INDIGO("Indigo", 0xFF6366F1.toInt(), 0xFFEC4899.toInt()),
    MONO("Mono", 0xFF9CA3AF.toInt(), 0xFF6B7280.toInt()),
    CUSTOM("Custom", 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()),
}

/** The shape of the album art in the mini player. */
enum class MiniShape(val label: String) {
    CIRCLE("Circle"), COOKIE("Cookie"), FLOWER("Flower"), SQUARE("Rounded"),
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
    /** The user's own main and second color (used when [accent] is [Accent.CUSTOM]). */
    val customPrimary: Int = Accent.CUSTOM.primary,
    val customSecondary: Int = Accent.CUSTOM.secondary,
    val miniShape: MiniShape = MiniShape.COOKIE,
    /** The mini player artwork turns while a song plays. */
    val spinArtwork: Boolean = true,
    /** A ring around the mini player artwork shows how far the song is. */
    val progressRing: Boolean = true,
    /** Swipe the mini player left or right to change song. */
    val swipeMiniPlayer: Boolean = true,
    /** Entrance animations and moving decorations (off = calmer, also saves battery). */
    val animations: Boolean = true,
    /** Skip quiet gaps in songs and podcasts. */
    val skipSilence: Boolean = false,
)

/** Downloads, play cache and resume options, saved on the phone. */
data class OfflinePrefs(
    /** How much room the play cache may use, in megabytes. */
    val cacheLimitMb: Int = 512,
    /** Only start downloads on Wi-Fi (or another unmetered connection). */
    val wifiOnlyDownloads: Boolean = true,
    /** Reopen the last queue and song (paused) when Sonara starts. */
    val resumeSession: Boolean = true,
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
    private val keyCacheLimit = intPreferencesKey("cache_limit_mb")
    private val keyWifiOnly = booleanPreferencesKey("downloads_wifi_only")
    private val keyResume = booleanPreferencesKey("resume_session")
    private val keyDownloads = stringPreferencesKey("downloads")
    private val keySession = stringPreferencesKey("session")
    private val keyCustomPrimary = intPreferencesKey("custom_primary")
    private val keyCustomSecondary = intPreferencesKey("custom_secondary")
    private val keyMiniShape = stringPreferencesKey("mini_shape")
    private val keySpin = booleanPreferencesKey("spin_artwork")
    private val keyRing = booleanPreferencesKey("progress_ring")
    private val keySwipeMini = booleanPreferencesKey("swipe_mini_player")
    private val keyAnimations = booleanPreferencesKey("animations")
    private val keySkipSilence = booleanPreferencesKey("skip_silence")
    private val keySearchHistory = stringPreferencesKey("search_history")

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
            customPrimary = p[keyCustomPrimary] ?: Accent.CUSTOM.primary,
            customSecondary = p[keyCustomSecondary] ?: Accent.CUSTOM.secondary,
            miniShape = runCatching { MiniShape.valueOf(p[keyMiniShape] ?: MiniShape.COOKIE.name) }.getOrDefault(MiniShape.COOKIE),
            spinArtwork = p[keySpin] ?: true,
            progressRing = p[keyRing] ?: true,
            swipeMiniPlayer = p[keySwipeMini] ?: true,
            animations = p[keyAnimations] ?: true,
            skipSilence = p[keySkipSilence] ?: false,
        )
    }

    /** The last few searches (newest first). */
    val searchHistory: Flow<List<String>> = ds.data.map { p ->
        (p[keySearchHistory] ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    val fx: Flow<FxSaved> = ds.data.map { FxSaved.decode(it[keyFx]) }
    val batteryHintShown: Flow<Boolean> = ds.data.map { it[keyBatteryHint] ?: false }
    val offlinePrefs: Flow<OfflinePrefs> = ds.data.map { p ->
        OfflinePrefs(
            cacheLimitMb = (p[keyCacheLimit] ?: 512).coerceIn(64, 8192),
            wifiOnlyDownloads = p[keyWifiOnly] ?: true,
            resumeSession = p[keyResume] ?: true,
        )
    }
    val downloadRecords: Flow<List<DownloadRecord>> = ds.data.map { DownloadRecordCodec.decode(it[keyDownloads]) }
    val session: Flow<SessionSnapshot?> = ds.data.map { SessionCodec.decode(it[keySession]) }

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
    suspend fun setCustomColors(primary: Int, secondary: Int) {
        ds.edit {
            it[keyCustomPrimary] = primary
            it[keyCustomSecondary] = secondary
        }
    }
    suspend fun setMiniShape(shape: MiniShape) { ds.edit { it[keyMiniShape] = shape.name } }
    suspend fun setSpinArtwork(on: Boolean) { ds.edit { it[keySpin] = on } }
    suspend fun setProgressRing(on: Boolean) { ds.edit { it[keyRing] = on } }
    suspend fun setSwipeMiniPlayer(on: Boolean) { ds.edit { it[keySwipeMini] = on } }
    suspend fun setAnimations(on: Boolean) { ds.edit { it[keyAnimations] = on } }
    suspend fun setSkipSilence(on: Boolean) { ds.edit { it[keySkipSilence] = on } }

    suspend fun addSearch(query: String) {
        val q = query.trim().take(60)
        if (q.length < 2) return
        ds.edit { p ->
            val list = (p[keySearchHistory] ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val updated = (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(8)
            p[keySearchHistory] = updated.joinToString("\n")
        }
    }
    suspend fun clearSearchHistory() { ds.edit { it.remove(keySearchHistory) } }
    suspend fun setLyricsEnabled(on: Boolean) { ds.edit { it[keyLyrics] = on } }
    suspend fun setLyricsScale(percent: Int) { ds.edit { it[keyLyricsScale] = percent.coerceIn(70, 140) } }
    suspend fun setLyricsOffset(ms: Int) { ds.edit { it[keyLyricsOffset] = ms.coerceIn(-5000, 5000) } }
    suspend fun setFx(fx: FxSaved) { ds.edit { it[keyFx] = fx.encode() } }
    suspend fun setBatteryHintShown() { ds.edit { it[keyBatteryHint] = true } }
    suspend fun setCacheLimitMb(mb: Int) { ds.edit { it[keyCacheLimit] = mb.coerceIn(64, 8192) } }
    suspend fun setWifiOnlyDownloads(on: Boolean) { ds.edit { it[keyWifiOnly] = on } }
    suspend fun setResumeSession(on: Boolean) { ds.edit { it[keyResume] = on } }

    // ----- downloads and resume -----

    suspend fun updateDownloads(transform: (List<DownloadRecord>) -> List<DownloadRecord>) {
        ds.edit { p -> p[keyDownloads] = DownloadRecordCodec.encode(transform(DownloadRecordCodec.decode(p[keyDownloads]))) }
    }

    suspend fun saveSession(snapshot: SessionSnapshot) { ds.edit { it[keySession] = SessionCodec.encode(snapshot) } }
    suspend fun clearSession() { ds.edit { it.remove(keySession) } }

    /** Wipes everything the app saved (used by Settings > Delete my data). */
    suspend fun clearAll() { ds.edit { it.clear() } }

    private companion object {
        const val MAX_RECENTS = 30
    }
}
