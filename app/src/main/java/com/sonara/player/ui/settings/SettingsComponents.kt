package com.sonara.player.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonara.player.BuildConfig
import com.sonara.player.data.ThemeMode
import com.sonara.player.ui.components.BrandGradient
import com.sonara.player.ui.components.SonaraLogo
import com.sonara.player.ui.components.pressScale
import com.sonara.player.ui.components.rememberMotionEnabled

/** Which pair of theme colors an icon chip uses: 0 = main, 1 = second, 2 = accent. */
@Composable
private fun toneColors(tone: Int): Pair<Color, Color> = when (tone) {
    1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
fun IconChip(icon: ImageVector, modifier: Modifier = Modifier, tone: Int = 0) {
    val (container, tint) = toneColors(tone)
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The big gradient card at the top of Settings. Its colors follow the palette and a soft light drifts across. */
@Composable
fun SettingsHeroCard(
    paletteLabel: String,
    onOpenStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionEnabled()
    val glow: State<Float> = if (motion) {
        rememberInfiniteTransition(label = "heroGlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
            label = "glow",
        )
    } else {
        remember { mutableFloatStateOf(0.4f) }
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(BrandGradient)
            .drawBehind {
                val g = glow.value
                drawRect(Color.Black.copy(alpha = 0.16f)) // keeps white text readable on light palettes
                drawCircle(
                    Color.White.copy(alpha = 0.20f),
                    radius = size.minDimension * 0.75f,
                    center = Offset(size.width * (0.15f + 0.7f * g), size.height * 0.05f),
                )
                drawCircle(
                    Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * (0.9f - 0.6f * g), size.height * 0.95f),
                )
            }
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SonaraLogo(56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Sonara", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.size(20.dp))
            Text("Make it yours", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "Colors, shapes and motion, with a live preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.size(16.dp))
            val source = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier
                    .pressScale(source)
                    .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onOpenStudio),
                shape = RoundedCornerShape(50),
                color = Color.White,
                contentColor = Color(0xFF1D1626),
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Theme Studio", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        paletteLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF1D1626).copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/** A rounded card with a title; the rows of one topic live inside it. */
@Composable
fun SettingsGroup(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: Int = 0,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier
            .pressScale(source, pressedScale = 0.985f)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon, tone = tone)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tone: Int = 0,
    enabled: Boolean = true,
) {
    SettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        tone = tone,
        modifier = modifier,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** System / Light / Dark as three pills; the selected one slides its color in. */
@Composable
fun ThemeModePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModePill("Auto", Icons.Rounded.PhoneAndroid, selected == ThemeMode.SYSTEM, { onSelect(ThemeMode.SYSTEM) }, Modifier.weight(1f))
        ModePill("Light", Icons.Rounded.LightMode, selected == ThemeMode.LIGHT, { onSelect(ThemeMode.LIGHT) }, Modifier.weight(1f))
        ModePill("Dark", Icons.Rounded.DarkMode, selected == ThemeMode.DARK, { onSelect(ThemeMode.DARK) }, Modifier.weight(1f))
    }
}

@Composable
fun ModePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pillBackground",
    )
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillForeground",
    )
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(role = Role.RadioButton, onClickLabel = "Use $label", onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}
