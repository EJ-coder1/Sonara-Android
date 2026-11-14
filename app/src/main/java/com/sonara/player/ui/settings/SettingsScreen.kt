package com.sonara.player.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonara.player.BuildConfig
import com.sonara.player.data.Accent
import com.sonara.player.data.AppIcon
import com.sonara.player.data.ThemeMode
import com.sonara.player.ui.player.AudioPlaybackSheet
import com.sonara.player.ui.theme.accentColorScheme
import androidx.compose.runtime.mutableLongStateOf
import com.sonara.player.playback.PlayCache
import com.sonara.player.ui.components.LocalDownloads
import com.sonara.player.util.formatBytes
import com.sonara.player.util.AppIconManager
import com.sonara.player.util.BatteryOptimization
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.ui.components.SonaraLogo
import com.sonara.player.util.AppInfo

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onBack: () -> Unit,
    onOpenLegal: (String) -> Unit,
    onOpenAppIcons: () -> Unit,
) {
    val context = LocalContext.current
    val message = LocalMessage.current
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val dynamic by appVm.dynamicColor.collectAsStateWithLifecycle()
    val prefs by appVm.uiPrefs.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    // Coming back from the system battery page: show the new status.
    var appIcon by remember { mutableStateOf(AppIconManager.current(context)) }
    val offline by appVm.offlinePrefs.collectAsStateWithLifecycle()
    val downloads = LocalDownloads.current
    val downloadItems by downloads.items.collectAsStateWithLifecycle()
    val downloadBytes by downloads.diskBytes.collectAsStateWithLifecycle()
    var cacheBytes by remember { mutableLongStateOf(PlayCache.usedBytes(context)) }
    var showCacheLimit by remember { mutableStateOf(false) }
    var confirmDeleteDownloads by remember { mutableStateOf(false) }
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

        LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { GroupTitle("Appearance") }
            item {
                Column {
                    ThemeOption("Match system", themeMode == ThemeMode.SYSTEM) { appVm.setThemeMode(ThemeMode.SYSTEM) }
                    ThemeOption("Light", themeMode == ThemeMode.LIGHT) { appVm.setThemeMode(ThemeMode.LIGHT) }
                    ThemeOption("Dark", themeMode == ThemeMode.DARK) { appVm.setThemeMode(ThemeMode.DARK) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        headlineContent = { Text("Use wallpaper colors") },
                        supportingContent = { Text("Match the app to your phone's color theme") },
                        trailingContent = { Switch(checked = dynamic, onCheckedChange = { appVm.setDynamicColor(it) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { appVm.setDynamicColor(!dynamic) },
                    )
                }
            }

            item {
                SwitchRow(
                    title = "AMOLED black",
                    subtitle = "Pure black backgrounds in dark mode (saves battery on OLED screens)",
                    checked = prefs.amoled,
                    onChange = { appVm.setAmoled(it) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Accent color", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Turn off \"Use wallpaper colors\" to pick your own."
                        else "Used for buttons and highlights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AccentPicker(
                        selected = prefs.accent,
                        enabled = !(dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
                        onSelect = { appVm.setAccent(it) },
                    )
                }
            }

            item {
                ListItem(
                    headlineContent = { Text("App icon") },
                    supportingContent = { Text("${appIcon.label}. Choose from ${AppIcon.entries.size} icons for your home screen.") },
                    leadingContent = { AppIconPreview(appIcon, 44.dp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenAppIcons),
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { GroupTitle("Player") }
            item {
                SwitchRow(
                    title = "Animated backdrop",
                    subtitle = "Soft, slowly moving colors from the album cover behind the player",
                    checked = prefs.fluidBackdrop,
                    onChange = { appVm.setFluidBackdrop(it) },
                )
            }
            item {
                SwitchRow(
                    title = "Wavy seek bar",
                    subtitle = "The played part of the bar flows like a wave while music plays",
                    checked = prefs.wavySeekBar,
                    onChange = { appVm.setWavySeekBar(it) },
                )
            }
            item {
                SwitchRow(
                    title = "Lyrics",
                    subtitle = "Looks up synced lyrics on lrclib.net when you open the lyrics view. This sends the song's title, artist, album and length.",
                    checked = prefs.lyricsEnabled,
                    onChange = { appVm.setLyricsEnabled(it) },
                )
            }
            item {
                SettingRow("Clear saved lyrics", "Lyrics are kept on this phone so songs open faster") {
                    appVm.clearLyricsCache()
                    message("Saved lyrics cleared")
                }
            }
            item {
                SettingRow("Audio & sound effects", "Volume, speed, pitch, equalizer, bass boost") { showAudio = true }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { GroupTitle("Playback") }
            item {
                SettingRow(
                    "Background playback",
                    if (batteryOk) "Allowed. Music keeps going with the screen off."
                    else "Restricted by your phone. Tap to allow Sonara to keep playing and keep the notification up to date with the screen off.",
                ) {
                    if (!BatteryOptimization.openSettings(context)) message("Couldn't open the battery settings")
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { GroupTitle("Downloads and storage") }
            item {
                SwitchRow(
                    "Download over Wi-Fi only",
                    "Saves mobile data. Turn off to download on any connection.",
                    offline.wifiOnlyDownloads,
                ) { appVm.setWifiOnlyDownloads(it) }
            }
            item {
                val saved = downloadItems.count { it.status == com.sonara.player.data.downloads.DownloadStatus.DONE }
                SettingRow(
                    "Downloaded songs",
                    "$saved saved, ${formatBytes(downloadBytes)}. Manage them in Library, Downloads.",
                ) { }
            }
            item {
                SettingRow("Delete all downloads", "Removes the downloaded songs from this phone") {
                    confirmDeleteDownloads = true
                }
            }
            item {
                SettingRow(
                    "Play cache",
                    "${formatBytes(cacheBytes)} used of ${cacheLimitLabel(offline.cacheLimitMb)}. " +
                        "Songs you stream are kept, so playing them again uses no data.",
                ) { showCacheLimit = true }
            }
            item {
                SettingRow("Clear play cache", "Frees the space used by streamed songs") {
                    appVm.clearPlayCache {
                        cacheBytes = PlayCache.usedBytes(context)
                        message("Play cache cleared")
                    }
                }
            }
            item {
                SwitchRow(
                    "Resume where I left off",
                    "Open your last song and queue (paused) when Sonara starts",
                    offline.resumeSession,
                ) { appVm.setResumeSession(it) }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { GroupTitle("Your music") }
            item {
                SettingRow("Rescan music on this phone", "Find songs you added recently") {
                    appVm.refreshSongs()
                    message("Scanning your music")
                }
            }
            item {
                SettingRow("Clear recently played", null) {
                    appVm.clearRecents()
                    message("Recently played cleared")
                }
            }
            item {
                SettingRow("Delete all my data", "Removes liked songs, playlists, history and settings from this phone") {
                    confirmDelete = true
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { GroupTitle("Help and legal") }
            item { SettingRow("Privacy Policy", null) { onOpenLegal("privacy") } }
            item { SettingRow("Terms of Use", null) { onOpenLegal("terms") } }
            item { SettingRow("Open-source licenses and credits", "Includes music credits for Jamendo") { onOpenLegal("licenses") } }
            item {
                SettingRow("Send feedback", AppInfo.SUPPORT_EMAIL) {
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
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SonaraLogo(64.dp)
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
            text = { Text("This removes your liked songs, playlists, recently played list and settings from this phone. Your music files are not touched. This can't be undone.") },
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
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun AccentPicker(selected: Accent, enabled: Boolean, onSelect: (Accent) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Accent.entries.forEach { accent ->
            val color = accentColorScheme(accent, dark = true).primary
            val isSelected = accent == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (enabled) 1f else 0.35f))
                    .clickable(enabled = enabled, role = Role.RadioButton, onClickLabel = "Use ${accent.label}") { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = "${accent.label} selected", tint = Color.Black.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
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

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    if (subtitle != null) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
        )
    } else {
        ListItem(
            headlineContent = { Text(title) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

private val CACHE_LIMITS_MB = listOf(128, 256, 512, 1024, 2048)

private fun cacheLimitLabel(mb: Int): String = if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"
