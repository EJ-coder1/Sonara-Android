package com.sonara.player.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonara.player.data.Track
import com.sonara.player.playback.AudioSpecs
import com.sonara.player.playback.bitrateLabel
import com.sonara.player.playback.channelsLabel
import com.sonara.player.playback.sampleRateLabel
import com.sonara.player.util.formatTime

/**
 * "Details & audio specs". The audio format is only known for the song that is playing now
 * (it comes from the decoder), so [specs] is null for any other song.
 */
@Composable
fun TrackDetailsDialog(track: Track, specs: AudioSpecs?, onDismiss: () -> Unit) {
    val rows = buildList {
        add("Title" to track.title)
        add("Artist" to track.artist)
        if (track.album.isNotBlank()) add("Album" to track.album)
        if (track.durationMs > 0L) add("Length" to formatTime(track.durationMs))
        add("Source" to if (track.isOnline) "Jamendo (free, Creative Commons)" else "This phone")
        specs?.codec?.let { add("Format" to it) }
        bitrateLabel(specs?.bitrateBps)?.let { add("Bitrate" to it) }
        sampleRateLabel(specs?.sampleRateHz)?.let { add("Sample rate" to it) }
        channelsLabel(specs?.channels)?.let { add("Channels" to it) }
        if (track.isOnline && track.licenseUrl != null) add("License" to track.licenseUrl)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details & audio specs") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
                if (specs == null) {
                    Text(
                        "Play this song to see its format, bitrate and sample rate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
