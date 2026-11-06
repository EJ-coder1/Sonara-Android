package com.sonara.player.ui.player

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.player.data.lyrics.LyricLine
import com.sonara.player.data.lyrics.LyricsState
import com.sonara.player.data.lyrics.LyricsTiming

private val Bright = Color.White
private val Dim = Color.White.copy(alpha = 0.38f)

/**
 * Lyrics that follow the song. The line being sung is large and fills word by word (a karaoke
 * sweep); other lines are dim. Tap a line to jump to it.
 *
 * LRCLIB supplies line timing, so the word sweep inside a line is an estimate.
 *
 * @param positionMs playback position from the player (updates a few times a second)
 * @param offsetMs user sync offset: positive = lyrics later
 * @param scalePercent 70..140
 */
@Composable
fun LyricsView(
    state: LyricsState,
    positionMs: Long,
    isPlaying: Boolean,
    speed: Float,
    durationMs: Long,
    offsetMs: Int,
    scalePercent: Int,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (state) {
            LyricsState.Off -> Message(
                "Lyrics are off.\nTurning them on looks up this song on lrclib.net and sends its title, artist, album and length.",
                "Turn on lyrics",
                onEnable,
            )
            LyricsState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
            LyricsState.NotFound -> Message("No lyrics found for this song.")
            LyricsState.Failed -> Message("Couldn't load lyrics.\nCheck your internet connection.", "Try again", onRetry)
            is LyricsState.Plain -> PlainLyrics(state.text, scalePercent)
            is LyricsState.Synced -> SyncedLyrics(
                lines = state.lines,
                positionMs = positionMs,
                isPlaying = isPlaying,
                speed = speed,
                durationMs = durationMs,
                offsetMs = offsetMs,
                scalePercent = scalePercent,
                onSeek = onSeek,
            )
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = Color.White) }
            }
        }
    }
}

@Composable
private fun PlainLyrics(text: String, scalePercent: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (18f * scalePercent / 100f).sp, lineHeight = (28f * scalePercent / 100f).sp),
            color = Color.White.copy(alpha = 0.9f),
        )
        Text(
            "Not time-synced for this song.",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * Position that keeps moving smoothly between the player's updates, so the word sweep is fluid.
 * It re-anchors to the real position every time the player reports one, so it never drifts.
 */
@Composable
private fun rememberSmoothPosition(positionMs: Long, isPlaying: Boolean, speed: Float): State<Long> =
    produceState(initialValue = positionMs, positionMs, isPlaying, speed) {
        value = positionMs
        if (!isPlaying) return@produceState
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            value = positionMs + ((SystemClock.elapsedRealtime() - startedAt) * speed).toLong()
        }
    }

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    speed: Float,
    durationMs: Long,
    offsetMs: Int,
    scalePercent: Int,
    onSeek: (Long) -> Unit,
) {
    val smooth = rememberSmoothPosition(positionMs, isPlaying, speed)
    // Which line is active only needs the coarse position; the fine position drives the sweep.
    val activeIndex = LyricsTiming.activeIndex(lines, positionMs - offsetMs)
    val listState = rememberLazyListState()
    val scale = scalePercent / 100f

    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0) return@LaunchedEffect
        if (placed) {
            listState.animateScrollToItem(activeIndex, scrollOffset = 0)
        } else {
            // First time the lyrics open: jump straight to the current line instead of scrolling from the top.
            listState.scrollToItem(activeIndex, scrollOffset = 0)
            placed = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Top padding puts the active line a little below the top; bottom padding lets the last line reach it.
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 320.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex
            LyricRow(
                line = line,
                isActive = isActive,
                isPast = index < activeIndex,
                endMs = LyricsTiming.lineEnd(lines, index, durationMs),
                smoothPosition = smooth,
                offsetMs = offsetMs,
                scale = scale,
                onClick = { onSeek((line.timeMs + offsetMs).coerceAtLeast(0L)) },
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    endMs: Long,
    smoothPosition: State<Long>,
    offsetMs: Int,
    scale: Float,
    onClick: () -> Unit,
) {
    val textAlpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.55f
            else -> 0.75f
        },
        animationSpec = tween(250),
        label = "lyricAlpha",
    )
    val size = (if (isActive) 30f else 24f) * scale
    val sizeAnimated by animateFloatAsState(targetValue = size, animationSpec = tween(250), label = "lyricSize")
    val restColor by animateColorAsState(
        targetValue = if (isActive) Dim else Color.White.copy(alpha = 0.42f),
        animationSpec = tween(250),
        label = "lyricRest",
    )

    val text = line.text.ifBlank { "\u266A" }
    val words = remember(line.text) { LyricsTiming.words(line.text) }

    Text(
        text = if (isActive && line.text.isNotBlank()) {
            // Reading the smooth position HERE (not in the parent) means only this row redraws every frame.
            val pos = smoothPosition.value - offsetMs
            val fills = LyricsTiming.wordFills(words, line.timeMs, endMs, pos)
            buildAnnotatedString {
                words.forEachIndexed { i, word ->
                    withStyle(SpanStyle(color = lerp(Dim, Bright, fills[i]))) { append(word) }
                    if (i < words.lastIndex) append(' ')
                }
            }
        } else {
            buildAnnotatedString { withStyle(SpanStyle(color = restColor)) { append(text) } }
        },
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = sizeAnimated.sp,
            lineHeight = (sizeAnimated * 1.25f).sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(textAlpha)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
