package com.sonara.player.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonara.player.data.Accent
import com.sonara.player.data.MiniShape
import com.sonara.player.data.Track
import com.sonara.player.data.TrackSource
import com.sonara.player.data.UiPrefs
import com.sonara.player.ui.AppViewModel
import com.sonara.player.ui.components.BrandGradient
import com.sonara.player.ui.components.fadeSlideIn
import com.sonara.player.ui.components.pressScale
import com.sonara.player.ui.components.rememberMotionEnabled
import com.sonara.player.ui.player.SpinningArtwork
import com.sonara.player.ui.player.miniShapeFor
import com.sonara.player.ui.theme.BrandColors
import com.sonara.player.ui.theme.LocalBrandColors
import com.sonara.player.ui.theme.accentColorScheme
import com.sonara.player.ui.theme.asAmoled
import com.sonara.player.ui.theme.brandColorsFor
import com.sonara.player.util.ColorMath

private val PreviewTrack = Track(
    id = "preview",
    title = "Your song",
    artist = "Your artist",
    album = "",
    durationMs = 180_000L,
    uri = "",
    artworkUri = null,
    source = TrackSource.LOCAL,
)

/** Settings > Theme Studio: pick or build a color palette, shape the mini player, tune motion. Everything previews live. */
@Composable
fun ThemeStudioScreen(
    padding: PaddingValues,
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val prefs by appVm.uiPrefs.collectAsStateWithLifecycle()
    val themeMode by appVm.themeMode.collectAsStateWithLifecycle()
    val dynamic by appVm.dynamicColor.collectAsStateWithLifecycle()
    val wallpaperOn = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val motion = rememberMotionEnabled()
    val seen = remember { mutableSetOf<Int>() }

    // The colors being edited. They update instantly while dragging; saved when the finger lifts.
    var mainColor by remember { mutableIntStateOf(prefs.customPrimary) }
    var secondColor by remember { mutableIntStateOf(prefs.customSecondary) }
    var editSecond by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Theme Studio", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LiveThemePreview(
                    accent = prefs.accent,
                    customMain = mainColor,
                    customSecond = secondColor,
                    wallpaperOn = wallpaperOn,
                    prefs = prefs,
                    modifier = Modifier.fadeSlideIn(0, seen, motion),
                )
            }

            item {
                SettingsGroup("Colors", Icons.Rounded.Palette, Modifier.fadeSlideIn(1, seen, motion)) {
                    if (wallpaperOn) {
                        Text(
                            "Wallpaper colors are on, so the app uses your phone's colors. Turn them off below to pick your own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Accent.entries.chunked(4).forEach { rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                rowItems.forEach { accent ->
                                    val isCustom = accent == Accent.CUSTOM
                                    PaletteChip(
                                        accent = accent,
                                        selected = prefs.accent == accent,
                                        enabled = !wallpaperOn,
                                        main = if (isCustom) mainColor else accent.primary,
                                        second = if (isCustom) secondColor else accent.secondary,
                                        onClick = {
                                            if (isCustom) appVm.setCustomColors(mainColor, secondColor)
                                            else appVm.setAccent(accent)
                                        },
                                    )
                                }
                                repeat(4 - rowItems.size) { Spacer(Modifier.width(72.dp)) }
                            }
                        }
                    }
                    AnimatedVisibility(visible = prefs.accent == Accent.CUSTOM && !wallpaperOn) {
                        CustomColorEditor(
                            main = mainColor,
                            second = secondColor,
                            editSecond = editSecond,
                            onEditSecond = { editSecond = it },
                            onColorsChange = { m, s ->
                                mainColor = m
                                secondColor = s
                            },
                            onCommit = { appVm.setCustomColors(mainColor, secondColor) },
                        )
                    }
                }
            }

            item {
                SettingsGroup("Style", Icons.Rounded.AutoAwesome, Modifier.fadeSlideIn(2, seen, motion)) {
                    ThemeModePicker(themeMode) { appVm.setThemeMode(it) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitchItem(
                            icon = Icons.Rounded.Palette,
                            title = "Use wallpaper colors",
                            subtitle = "Match the app to your phone's color theme",
                            checked = dynamic,
                            onChange = { appVm.setDynamicColor(it) },
                            tone = 1,
                        )
                    }
                    SettingsSwitchItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AMOLED black",
                        subtitle = "Pure black backgrounds in dark mode (saves battery on OLED screens)",
                        checked = prefs.amoled,
                        onChange = { appVm.setAmoled(it) },
                        tone = 2,
                    )
                }
            }

            item {
                SettingsGroup("Mini player", Icons.Rounded.MusicNote, Modifier.fadeSlideIn(3, seen, motion)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MiniShape.entries.forEach { shape ->
                            ShapeChoice(shape, selected = prefs.miniShape == shape, onClick = { appVm.setMiniShape(shape) })
                        }
                    }
                    SettingsSwitchItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Spinning artwork",
                        subtitle = "The cover turns slowly while music plays",
                        checked = prefs.spinArtwork,
                        onChange = { appVm.setSpinArtwork(it) },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.MusicNote,
                        title = "Progress ring",
                        subtitle = "A ring around the cover shows how far the song is",
                        checked = prefs.progressRing,
                        onChange = { appVm.setProgressRing(it) },
                        tone = 1,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.SwapHoriz,
                        title = "Swipe to change song",
                        subtitle = "Swipe the mini player left for next, right for previous",
                        checked = prefs.swipeMiniPlayer,
                        onChange = { appVm.setSwipeMiniPlayer(it) },
                        tone = 2,
                    )
                }
            }

            item {
                SettingsGroup("Player and motion", Icons.Rounded.GraphicEq, Modifier.fadeSlideIn(4, seen, motion)) {
                    SettingsSwitchItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Animated backdrop",
                        subtitle = "Soft, slowly moving colors from the album cover behind the player",
                        checked = prefs.fluidBackdrop,
                        onChange = { appVm.setFluidBackdrop(it) },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Wavy seek bar",
                        subtitle = "The played part of the bar flows like a wave while music plays",
                        checked = prefs.wavySeekBar,
                        onChange = { appVm.setWavySeekBar(it) },
                        tone = 1,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Animations",
                        subtitle = "Entrance effects and moving decorations. Turn off for a calmer app that also saves battery.",
                        checked = prefs.animations,
                        onChange = { appVm.setAnimations(it) },
                        tone = 2,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

/** A small, real copy of the app drawn with the colors being edited (a nested theme, so nothing else changes). */
@Composable
private fun LiveThemePreview(
    accent: Accent,
    customMain: Int,
    customSecond: Int,
    wallpaperOn: Boolean,
    prefs: UiPrefs,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val current = MaterialTheme.colorScheme
    val scheme = if (wallpaperOn) {
        current
    } else {
        val generated = accentColorScheme(accent, dark, customMain, customSecond)
        if (dark && prefs.amoled) generated.asAmoled() else generated
    }
    val brand: BrandColors = if (wallpaperOn) LocalBrandColors.current else brandColorsFor(accent, customMain, customSecond)

    CompositionLocalProvider(LocalBrandColors provides brand) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .background(BrandGradient)
                    ) {
                        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text("Good evening", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text("Live preview", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpinningArtwork(
                            track = PreviewTrack,
                            isPlaying = true,
                            progress = 0.42f,
                            shape = prefs.miniShape,
                            spin = prefs.spinArtwork,
                            ring = prefs.progressRing,
                            diameter = 56.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your song", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("Your artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledIconButton(onClick = {}) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview play button")
                        }
                    }
                    Row(
                        Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(selected = true, onClick = {}, label = { Text("Chill") })
                        FilterChip(selected = false, onClick = {}, label = { Text("Focus") })
                        Spacer(Modifier.weight(1f))
                        Switch(checked = true, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteChip(
    accent: Accent,
    selected: Boolean,
    enabled: Boolean,
    main: Int,
    second: Int,
    onClick: () -> Unit,
) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "paletteRing")
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(72.dp)
            .pressScale(source)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClickLabel = "Use the ${accent.label} colors",
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(main), Color(second))))
                .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "${accent.label} selected", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            if (accent == Accent.CUSTOM && !selected) {
                Icon(Icons.Rounded.Colorize, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
            }
        }
        Text(
            accent.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ShapeChoice(shape: MiniShape, selected: Boolean, onClick: () -> Unit) {
    val outline = miniShapeFor(shape)
    val source = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressScale(source)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClickLabel = "Use the ${shape.label} shape",
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(outline)
                .background(BrandGradient)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, outline) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
        }
        Text(
            shape.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------

private fun hslColor(h: Float, s: Float, l: Float) = Color(ColorMath.hslToArgb(h, s, l))

private val Rainbow = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hslColor(it % 360f, 1f, 0.5f) }

/** Hue, saturation and brightness sliders plus a hex box, for the main color or the second color. */
@Composable
private fun CustomColorEditor(
    main: Int,
    second: Int,
    editSecond: Boolean,
    onEditSecond: (Boolean) -> Unit,
    onColorsChange: (Int, Int) -> Unit,
    onCommit: () -> Unit,
) {
    val current = if (editSecond) second else main
    val start = remember(editSecond) { ColorMath.toHsl(current) }
    var hue by remember(editSecond) { mutableFloatStateOf(start.first) }
    var sat by remember(editSecond) { mutableFloatStateOf(start.second) }
    var light by remember(editSecond) { mutableFloatStateOf(start.third) }
    var hex by remember(editSecond, current) { mutableStateOf(ColorMath.toHex(current)) }

    fun put(argb: Int) {
        if (editSecond) onColorsChange(main, argb) else onColorsChange(argb, second)
    }

    fun push() = put(ColorMath.hslToArgb(hue, sat, light))

    fun setAll(argb: Int) {
        val (h, s, l) = ColorMath.toHsl(argb)
        if (s > 0f) hue = h
        sat = s
        light = l
        put(argb)
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModePill("Main color", Icons.Rounded.Palette, !editSecond, { onEditSecond(false) }, Modifier.weight(1f))
            ModePill("Second color", Icons.Rounded.Colorize, editSecond, { onEditSecond(true) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        SliderLabel("Hue")
        ColorSlider(
            value = hue / 360f,
            colors = Rainbow,
            description = "Hue",
            onChange = { hue = it * 360f; push() },
            onFinished = onCommit,
        )
        SliderLabel("Saturation")
        ColorSlider(
            value = sat,
            colors = listOf(hslColor(hue, 0f, light), hslColor(hue, 1f, light)),
            description = "Saturation",
            onChange = { sat = it; push() },
            onFinished = onCommit,
        )
        SliderLabel("Brightness")
        ColorSlider(
            value = light,
            colors = listOf(Color.Black, hslColor(hue, sat, 0.5f), Color.White),
            description = "Brightness",
            onChange = { light = it; push() },
            onFinished = onCommit,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(current))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            OutlinedTextField(
                value = hex,
                onValueChange = { text ->
                    hex = text.take(7)
                    val digits = text.trim().removePrefix("#")
                    if (digits.length == 6) {
                        ColorMath.parseHex(text)?.let { argb ->
                            setAll(argb)
                            onCommit()
                        }
                    }
                },
                singleLine = true,
                label = { Text("Hex color") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                setAll(if (editSecond) Accent.CUSTOM.secondary else Accent.CUSTOM.primary)
                onCommit()
            }) { Text("Reset") }
        }
    }
}

@Composable
private fun SliderLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** A slider whose track is the color range it controls. */
@Composable
private fun ColorSlider(
    value: Float,
    colors: List<Color>,
    description: String,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val outline = MaterialTheme.colorScheme.outline
    val v = value.coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f)
                setProgress { target ->
                    change(target.coerceIn(0f, 1f))
                    finished()
                    true
                }
            }
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                fun fraction(x: Float) = ((x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f)
                detectTapGestures { offset ->
                    change(fraction(offset.x))
                    finished()
                }
            }
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                fun fraction(x: Float) = ((x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { change(fraction(it.x)) },
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { pointer, _ ->
                    pointer.consume()
                    change(fraction(pointer.position.x))
                }
            }
    ) {
        val pad = 14.dp.toPx()
        val barHeight = 14.dp.toPx()
        val top = (this.size.height - barHeight) / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(colors, startX = pad, endX = this.size.width - pad),
            topLeft = Offset(pad, top),
            size = Size(this.size.width - 2f * pad, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
        )
        val cx = pad + v * (this.size.width - 2f * pad)
        val cy = this.size.height / 2f
        drawCircle(Color.White, radius = 13.dp.toPx(), center = Offset(cx, cy))
        drawCircle(outline, radius = 13.dp.toPx(), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        drawCircle(lerpColor(colors, v), radius = 8.dp.toPx(), center = Offset(cx, cy))
    }
}

/** The color at position [t] along a list of gradient colors. */
private fun lerpColor(colors: List<Color>, t: Float): Color {
    if (colors.size == 1) return colors[0]
    val scaled = t.coerceIn(0f, 1f) * (colors.size - 1)
    val i = scaled.toInt().coerceAtMost(colors.size - 2)
    return androidx.compose.ui.graphics.lerp(colors[i], colors[i + 1], scaled - i)
}
