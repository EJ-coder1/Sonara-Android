package com.sonara.player.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonara.player.BuildConfig
import com.sonara.player.data.AppIcon
import com.sonara.player.data.downloads.DownloadStatus
import com.sonara.player.playback.CrossfadeMath
import com.sonara.player.playback.PlayCache
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.components.LocalDownloads
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.SonaraLogo
import com.sonara.player.ui.components.fadeSlideIn
import com.sonara.player.ui.components.rememberMotionEnabled
import com.sonara.player.ui.player.AudioPlaybackSheet
import com.sonara.player.util.AppIconManager
import com.sonara.player.util.AppInfo
import com.sonara.player.util.BatteryOptimization
import com.sonara.player.util.formatBytes
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onOpenAppIcons: () -> Unit,
    onOpenThemeStudio: () -> Unit,
) {
    val context = LocalContext.current
    val message = LocalMessage.current
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val prefs by appVm.uiPrefs.collectAsStateWithLifecycle()
    val offline by appVm.offlinePrefs.collectAsStateWithLifecycle()
    val crossfade by appVm.crossfade.collectAsStateWithLifecycle()
    // The slider keeps its own value while dragging and saves when the finger lifts.
    var crossfadeSeconds by remember(crossfade.seconds) { mutableFloatStateOf(crossfade.seconds.toFloat()) }
    val downloads = LocalDownloads.current
    val downloadItems by downloads.items.collectAsStateWithLifecycle()
    val downloadBytes by downloads.diskBytes.collectAsStateWithLifecycle()
    val motion = rememberMotionEnabled()
    val seen = remember { mutableSetOf<Int>() }

    var confirmDelete by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var showCacheLimit by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    var appIcon by remember { mutableStateOf(AppIconManager.current(context)) }
    var cacheBytes by remember { mutableLongStateOf(PlayCache.usedBytes(context)) }
    // Coming back from a system page (battery settings, home screen): show the new status.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryOk = BatteryOptimization.isIgnoring(context)
        appIcon = AppIconManager.current(context)
        cacheBytes = PlayCache.usedBytes(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsHeroCard(
                    paletteLabel = prefs.accent.label,
                    onOpenStudio = onOpenThemeStudio,
                    modifier = Modifier.fadeSlideIn(0, seen, motion),
                )
            }

            item {
                SettingsGroup("Appearance", Icons.Rounded.Palette, Modifier.fadeSlideIn(1, seen, motion)) {
                    ThemeModePicker(
                        selected = themeMode,
                        onSelect = { selectedMode -> appVm.setThemeMode(selectedMode) },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Palette,
                        title = "Theme Studio",
                        subtitle = "${prefs.accent.label} colors, mini player shape, motion",
                        onClick = onOpenThemeStudio,
                    )
                    SettingsItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "App icon",
                        subtitle = "${appIcon.label}. Choose from ${AppIcon.entries.size} icons for your home screen.",
                        tone = 1,
                        onClick = onOpenAppIcons,
                    )
                }
            }

            item {
                SettingsGroup("Player", Icons.Rounded.GraphicEq, Modifier.fadeSlideIn(2, seen, motion)) {
                    SettingsItem(
                        icon = Icons.Rounded.Equalizer,
                        title = "Audio and sound effects",
                        subtitle = "Volume, speed, pitch, equalizer, bass boost",
                        onClick = { showAudio = true },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.Tune,
                        title = "Skip silence",
                        subtitle = "Jump over quiet gaps in songs and podcasts",
                        checked = prefs.skipSilence,
                        onChange = { appVm.setSkipSilence(it) },
                        tone = 1,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.Waves,
                        title = "Crossfade",
                        subtitle = "Fade the end of a song into the start of the next one",
                        checked = crossfade.enabled,
                        onChange = { appVm.setCrossfadeEnabled(it) },
                        tone = 2,
                    )
                    SettingsSliderItem(
                        icon = Icons.Rounded.Tune,
                        title = "Crossfade length",
                        valueLabel = if (crossfadeSeconds.roundToInt() == 0) "Off" else "${crossfadeSeconds.roundToInt()} s",
                        value = crossfadeSeconds,
                        onValueChange = { crossfadeSeconds = it },
                        onValueChangeFinished = { appVm.setCrossfadeSeconds(crossfadeSeconds.roundToInt()) },
                        valueRange = CrossfadeMath.MIN_SECONDS.toFloat()..CrossfadeMath.MAX_SECONDS.toFloat(),
                        steps = CrossfadeMath.MAX_SECONDS - CrossfadeMath.MIN_SECONDS - 1,
                        enabled = crossfade.enabled,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.FormatQuote,
                        title = "Lyrics",
                        subtitle = "Looks up synced lyrics on lrclib.net when you open the lyrics view. This sends the song's title, artist, album and length.",
                        checked = prefs.lyricsEnabled,
                        onChange = { appVm.setLyricsEnabled(it) },
                        tone = 2,
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Cached,
                        title = "Clear saved lyrics",
                        subtitle = "Lyrics are kept on this phone so songs open faster",
                        onClick = {
                            appVm.clearLyricsCache()
                            message("Saved lyrics cleared")
                        },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Background playback",
                        subtitle = if (batteryOk) "Allowed. Music keeps going with the screen off."
                        else "Restricted by your phone. Tap to allow Sonara to keep playing and keep the notification up to date with the screen off.",
                        tone = 1,
                        onClick = {
                            if (!BatteryOptimization.openSettings(context)) message("Couldn't open the battery settings")
                        },
                    )
                }
            }

            item {
                SettingsGroup("Downloads and storage", Icons.Rounded.Download, Modifier.fadeSlideIn(3, seen, motion)) {
                    SettingsSwitchItem(
                        icon = Icons.Rounded.Download,
                        title = "Download over Wi-Fi only",
                        subtitle = "Saves mobile data. Turn off to download on any connection.",
                        checked = offline.wifiOnlyDownloads,
                        onChange = { appVm.setWifiOnlyDownloads(it) },
                    )
                    val saved = downloadItems.count { it.status == DownloadStatus.DONE }
                    SettingsItem(
                        icon = Icons.Rounded.Storage,
                        title = "Downloaded songs",
                        subtitle = "$saved saved, ${formatBytes(downloadBytes)}. Manage them in Library, Downloads.",
                        tone = 1,
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Delete,
                        title = "Delete all downloads",
                        subtitle = "Removes the downloaded songs from this phone",
                        tone = 2,
                        onClick = { confirmDeleteDownloads = true },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Cached,
                        title = "Play cache",
                        subtitle = "${formatBytes(cacheBytes)} used of ${cacheLimitLabel(offline.cacheLimitMb)}. " +
                            "Songs you stream are kept, so playing them again uses no data.",
                        onClick = { showCacheLimit = true },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Refresh,
                        title = "Clear play cache",
                        subtitle = "Frees the space used by streamed songs",
                        tone = 1,
                        onClick = {
                            appVm.clearPlayCache {
                                cacheBytes = PlayCache.usedBytes(context)
                                message("Play cache cleared")
                            }
                        },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.History,
                        title = "Resume where I left off",
                        subtitle = "Open your last song and queue (paused) when Sonara starts",
                        checked = offline.resumeSession,
                        onChange = { appVm.setResumeSession(it) },
                        tone = 2,
                    )
                }
            }

            item {
                SettingsGroup("Your music", Icons.Rounded.MusicNote, Modifier.fadeSlideIn(4, seen, motion)) {
                    SettingsItem(
                        icon = Icons.Rounded.Refresh,
                        title = "Rescan music on this phone",
                        subtitle = "Find songs you added recently",
                        onClick = {
                            appVm.refreshSongs()
                            message("Scanning your music")
                        },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.History,
                        title = "Clear recently played",
                        tone = 1,
                        onClick = {
                            appVm.clearRecents()
                            message("Recently played cleared")
                        },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Delete all my data",
                        subtitle = "Removes liked songs, playlists, history and settings from this phone",
                        tone = 2,
                        onClick = { confirmDelete = true },
                    )
                }
            }

            item {
                SettingsGroup("Help and legal", Icons.Rounded.Info, Modifier.fadeSlideIn(5, seen, motion)) {
                    SettingsItem(Icons.Rounded.Lock, "Privacy Policy", onClick = { onOpenLegal("privacy") })
                    SettingsItem(Icons.Rounded.Description, "Terms of Use", tone = 1, onClick = { onOpenLegal("terms") })
                    SettingsItem(
                        icon = Icons.Rounded.Shield,
                        title = "Open-source licenses and credits",
                        subtitle = "Includes music credits for Jamendo",
                        tone = 2,
                        onClick = { onOpenLegal("licenses") },
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Email,
                        title = "Send feedback",
                        subtitle = AppInfo.SUPPORT_EMAIL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(AppInfo.SUPPORT_EMAIL))
                                putExtra(Intent.EXTRA_SUBJECT, "Sonara feedback (v${BuildConfig.VERSION_NAME})")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                message("No email app found")
                            }
                        },
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SonaraLogo(56.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Sonara", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAudio) AudioPlaybackSheet(onDismiss = { showAudio = false })

    if (showCacheLimit) {
        AlertDialog(
            onDismissRequest = { showCacheLimit = false },
            title = { Text("Play cache size") },
            text = {
                Column {
                    Text(
                        "When the cache is full, the songs you played longest ago are removed first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    for (mb in CACHE_LIMITS_MB) {
                        ThemeOption(cacheLimitLabel(mb), offline.cacheLimitMb == mb) {
                            appVm.setCacheLimitMb(mb)
                            showCacheLimit = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCacheLimit = false }) { Text("Close") } },
        )
    }

    if (confirmDeleteDownloads) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDownloads = false },
            title = { Text("Delete all downloads?") },
            text = { Text("The downloaded songs are removed from this phone. You can download them again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteDownloads = false
                    downloads.removeAll()
                    message("Downloads deleted")
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteDownloads = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all your data?") },
            text = { Text("This removes your liked songs, playlists, recently played list, downloads and settings from this phone. Your music files are not touched. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    appVm.deleteAllData { message("Your data was deleted") }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val CACHE_LIMITS_MB = listOf(128, 256, 512, 1024, 2048)

private fun cacheLimitLabel(mb: Int): String = if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"
