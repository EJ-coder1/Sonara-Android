package com.sonara.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sonara.player.data.Accent
import com.sonara.player.data.ThemeMode
import com.sonara.player.util.ColorMath

// Brand palette (matches the logo gradient)
val BrandDeep = Color(0xFF4C1D95)
val BrandViolet = Color(0xFF7C3AED)
val BrandPink = Color(0xFFEC4899)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF2A0B6B),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFFF58FC3),
    onSecondary = Color(0xFF4A0A2C),
    secondaryContainer = Color(0xFF6B1D45),
    onSecondaryContainer = Color(0xFFFFD9EA),
    background = Color(0xFF0F0B16),
    onBackground = Color(0xFFEDE7F6),
    surface = Color(0xFF0F0B16),
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = Color(0xFF2A2236),
    onSurfaceVariant = Color(0xFFBDB2CE),
    surfaceContainerLowest = Color(0xFF0A070F),
    surfaceContainerLow = Color(0xFF15101D),
    surfaceContainer = Color(0xFF1A1424),
    surfaceContainerHigh = Color(0xFF231C2F),
    surfaceContainerHighest = Color(0xFF2D2539),
    outline = Color(0xFF7D7290),
    outlineVariant = Color(0xFF3A3049),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE0FF),
    onPrimaryContainer = Color(0xFF2A0B6B),
    secondary = Color(0xFFBE185D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9EA),
    onSecondaryContainer = Color(0xFF4A0A2C),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1D1626),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1D1626),
    surfaceVariant = Color(0xFFE8DFF4),
    onSurfaceVariant = Color(0xFF4B4259),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F0FC),
    surfaceContainer = Color(0xFFF0EAF8),
    surfaceContainerHigh = Color(0xFFEAE3F4),
    surfaceContainerHighest = Color(0xFFE4DCEF),
    outline = Color(0xFF7D7290),
    outlineVariant = Color(0xFFCFC5DD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val base = Typography()
private val SonaraTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorMath.hslToArgb(h, s, l))

/** Hue and saturation that define each non-default accent. */
private fun accentSeed(accent: Accent): Pair<Float, Float> = when (accent) {
    Accent.VIOLET -> 265f to 0.80f
    Accent.CRIMSON -> 352f to 0.75f
    Accent.OCEAN -> 203f to 0.75f
    Accent.SAGE -> 140f to 0.32f
    Accent.AMBER -> 38f to 0.85f
    Accent.ROSE -> 328f to 0.70f
    Accent.MONO -> 0f to 0f
}

/** Builds a full Material color scheme from one accent. Violet keeps the hand-tuned brand colors. */
fun accentColorScheme(accent: Accent, dark: Boolean): ColorScheme {
    if (accent == Accent.VIOLET) return if (dark) DarkColors else LightColors
    val (h, s) = accentSeed(accent)
    val tint = minOf(s * 0.35f, 0.30f) // how much of the accent leaks into backgrounds
    val h2 = h + 25f
    return if (dark) {
        darkColorScheme(
            primary = hsl(h, s, 0.78f),
            onPrimary = hsl(h, s * 0.9f, 0.16f),
            primaryContainer = hsl(h, s * 0.8f, 0.30f),
            onPrimaryContainer = hsl(h, s, 0.92f),
            secondary = hsl(h2, s * 0.7f, 0.76f),
            onSecondary = hsl(h2, s * 0.7f, 0.16f),
            secondaryContainer = hsl(h2, s * 0.5f, 0.28f),
            onSecondaryContainer = hsl(h2, s * 0.8f, 0.90f),
            background = hsl(h, tint, 0.07f),
            onBackground = hsl(h, tint * 0.5f, 0.92f),
            surface = hsl(h, tint, 0.07f),
            onSurface = hsl(h, tint * 0.5f, 0.92f),
            surfaceVariant = hsl(h, tint, 0.17f),
            onSurfaceVariant = hsl(h, tint * 0.8f, 0.75f),
            surfaceContainerLowest = hsl(h, tint, 0.05f),
            surfaceContainerLow = hsl(h, tint, 0.09f),
            surfaceContainer = hsl(h, tint, 0.11f),
            surfaceContainerHigh = hsl(h, tint, 0.15f),
            surfaceContainerHighest = hsl(h, tint, 0.19f),
            outline = hsl(h, tint * 0.6f, 0.50f),
            outlineVariant = hsl(h, tint, 0.25f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
    } else {
        lightColorScheme(
            primary = hsl(h, s, 0.38f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, s * 0.9f, 0.90f),
            onPrimaryContainer = hsl(h, s, 0.15f),
            secondary = hsl(h2, s * 0.8f, 0.38f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h2, s * 0.7f, 0.90f),
            onSecondaryContainer = hsl(h2, s, 0.15f),
            background = hsl(h, tint * 0.8f, 0.98f),
            onBackground = hsl(h, tint, 0.10f),
            surface = hsl(h, tint * 0.8f, 0.98f),
            onSurface = hsl(h, tint, 0.10f),
            surfaceVariant = hsl(h, tint, 0.91f),
            onSurfaceVariant = hsl(h, tint * 0.8f, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, tint, 0.96f),
            surfaceContainer = hsl(h, tint, 0.94f),
            surfaceContainerHigh = hsl(h, tint, 0.92f),
            surfaceContainerHighest = hsl(h, tint, 0.90f),
            outline = hsl(h, tint * 0.6f, 0.50f),
            outlineVariant = hsl(h, tint, 0.80f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
        )
    }
}

/** True black surfaces (saves battery on OLED screens). Only used in dark mode. */
private fun ColorScheme.asAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF111114),
)

@Composable
fun SonaraTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    amoled: Boolean = false,
    accent: Accent = Accent.VIOLET,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> accentColorScheme(accent, darkTheme)
    }
    val colors = if (darkTheme && amoled) base.asAmoled() else base
    MaterialTheme(colorScheme = colors, typography = SonaraTypography, content = content)
}
