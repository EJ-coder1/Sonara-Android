package com.sonara.player.ui.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sonara.player.data.AppIcon
import com.sonara.player.data.AppIconGroup
import com.sonara.player.ui.components.LocalMessage
import com.sonara.player.util.AppIconManager

/** Settings > App icon: pick how Sonara looks on the home screen. */
@Composable
fun AppIconScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val message = LocalMessage.current
    var current by remember { mutableStateOf(AppIconManager.current(context)) }
    // Coming back from the home screen: show whatever is really enabled now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { current = AppIconManager.current(context) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("App icon", style = MaterialTheme.typography.titleLarge)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CurrentIconCard(current)
            }
            for (group in AppIconGroup.entries) {
                val icons = AppIcon.entries.filter { it.group == group }
                if (icons.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(icons, key = { it.name }) { icon ->
                    IconChoice(
                        icon = icon,
                        selected = icon == current,
                        onClick = {
                            if (icon != current) {
                                if (AppIconManager.apply(context, icon)) {
                                    current = icon
                                    message("Icon changed to ${icon.label}. Your home screen can take a few seconds to update.")
                                } else {
                                    message("Couldn't change the icon on this phone.")
                                }
                            }
                        },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Some phones close the app or take a moment to refresh the home screen when the icon changes. " +
                        "If you had Sonara pinned somewhere, pin it again to get the new icon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrentIconCard(current: AppIcon) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconPreview(current, 88.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Your icon",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(current.label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Pick any icon below. Phones with themed icons (Android 13 and up) can also tint it to match your wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconChoice(icon: AppIcon, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClickLabel = "Use the ${icon.label} icon", onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp)) {
            Box(
                Modifier
                    .size(84.dp)
                    .then(
                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                        else Modifier
                    )
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppIconPreview(icon, 72.dp)
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            icon.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Draws the real launcher icon (so it matches your phone's icon shape) at [size]. */
@Composable
fun AppIconPreview(icon: AppIcon, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(icon, px) { renderLauncherIcon(context, icon.iconRes, px) }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "${icon.label} icon", modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
    }
}

private fun renderLauncherIcon(context: Context, res: Int, px: Int): ImageBitmap? {
    if (px <= 0) return null
    return try {
        val drawable = ContextCompat.getDrawable(context, res) ?: return null
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
