package com.sonara.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonara.player.playback.EqPresets
import com.sonara.player.playback.dbLabel
import com.sonara.player.playback.hzLabel
import com.sonara.player.ui.components.LocalFx
import com.sonara.player.ui.components.LocalPlayer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Audio & playback": in-app volume, speed and pitch, equalizer, bass and loudness boost.
 * Opened from the player and from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlaybackSheet(
    onDismiss: () -> Unit,
    onOpenSleepTimer: (() -> Unit)? = null,
) {
    val player = LocalPlayer.current
    val fxManager = LocalFx.current
    val state by player.state.collectAsStateWithLifecycle()
    val fx by fxManager.state.collectAsStateWithLifecycle()

    // Sliders keep their own value while dragging so they never fight with the player's updates.
    var speed by remember { mutableFloatStateOf(state.speed) }
    var pitch by remember { mutableFloatStateOf(state.pitch) }
    var volume by remember { mutableFloatStateOf(state.volume) }
    var linked by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Audio & playback", style = MaterialTheme.typography.titleLarge)
            Text(
                "Volume, speed, pitch and sound shaping",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // ---------- volume ----------
            SectionTitle("In-app volume", "${(volume * 100).roundToInt()}%")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        player.setVolume(it)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // ---------- speed & pitch ----------
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speed & pitch", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (linked) "Linked: pitch follows speed" else "Speed changes without changing the pitch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = linked,
                    onClick = {
                        linked = !linked
                        if (linked) {
                            pitch = speed
                            player.setSpeedAndPitch(speed, speed)
                        }
                    },
                    label = { Text("Linked") },
                )
            }
            SectionTitle("Speed", multiplier(speed))
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    if (linked) pitch = it
                    player.setSpeedAndPitch(speed, pitch)
                },
                valueRange = 0.5f..2f,
                steps = 29,
            )
            SectionTitle("Pitch", multiplier(pitch))
            Slider(
                value = pitch,
                onValueChange = {
                    pitch = it
                    player.setSpeedAndPitch(speed, pitch)
                },
                valueRange = 0.5f..2f,
                steps = 29,
                enabled = !linked,
            )
            TextButton(
                onClick = {
                    speed = 1f
                    pitch = 1f
                    player.setSpeedAndPitch(1f, 1f)
                },
                enabled = abs(speed - 1f) > 0.001f || abs(pitch - 1f) > 0.001f,
            ) { Text("Reset speed and pitch") }

            // ---------- equalizer ----------
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sound effects", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Equalizer, bass boost and loudness",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = fx.enabled,
                    onCheckedChange = { fxManager.setEnabled(it) },
                    enabled = fx.equalizerSupported || fx.bassSupported || fx.loudnessSupported,
                )
            }

            if (!fx.equalizerSupported && !fx.bassSupported && !fx.loudnessSupported) {
                Text(
                    "Sound effects aren't available on this phone, or no song has played yet. Play a song and open this again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (fx.equalizerSupported) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EqPresets.names + EqPresets.CUSTOM, key = { it }) { name ->
                        FilterChip(
                            selected = fx.preset == name,
                            onClick = { if (name != EqPresets.CUSTOM) fxManager.applyPreset(name) },
                            enabled = fx.enabled && name != EqPresets.CUSTOM,
                            label = { Text(name) },
                        )
                    }
                }
                fx.levelsMb.forEachIndexed { index, level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hzLabel(fx.bandHz.getOrElse(index) { 0 }),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp),
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { fxManager.setBandLevel(index, it.roundToInt()) },
                            onValueChangeFinished = { fxManager.commit() },
                            valueRange = fx.minMb.toFloat()..fx.maxMb.toFloat(),
                            enabled = fx.enabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            dbLabel(level),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }
            }

            if (fx.bassSupported) {
                SectionTitle("Bass boost", "${fx.bassStrength / 10}%")
                Slider(
                    value = fx.bassStrength.toFloat(),
                    onValueChange = { fxManager.setBass(it.roundToInt()) },
                    onValueChangeFinished = { fxManager.commit() },
                    valueRange = 0f..1000f,
                    enabled = fx.enabled,
                )
            }
            if (fx.loudnessSupported) {
                SectionTitle("Loudness boost", String.format(Locale.US, "+%.1f dB", fx.loudnessMb / 100f))
                Slider(
                    value = fx.loudnessMb.toFloat(),
                    onValueChange = { fxManager.setLoudness(it.roundToInt()) },
                    onValueChangeFinished = { fxManager.commit() },
                    valueRange = 0f..1500f,
                    enabled = fx.enabled,
                )
                Text(
                    "Loudness boost makes quiet songs louder. Keep it low if the sound starts to crackle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onOpenSleepTimer != null) {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Sleep timer") },
                    leadingContent = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenSleepTimer()
                        },
                )
            }
        }
    }
}

private fun multiplier(v: Float): String = String.format(Locale.US, "%.2fx", v)

@Composable
private fun SectionTitle(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
