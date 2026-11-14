package com.sonara.player.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sonara.player.MainActivity
import com.sonara.player.data.AppIcon

/**
 * Switches the launcher icon by enabling one manifest alias and disabling the others.
 * Nothing is stored by the app: Android remembers which alias is enabled.
 */
object AppIconManager {

    private fun component(context: Context, icon: AppIcon): ComponentName {
        // Alias class names are relative to the app's namespace, which is also where MainActivity lives.
        val base = MainActivity::class.java.name.substringBeforeLast('.')
        return ComponentName(context.packageName, base + "." + icon.alias)
    }

    /** The icon that is on the home screen right now. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        for (icon in AppIcon.entries) {
            val state = try {
                pm.getComponentEnabledSetting(component(context, icon))
            } catch (e: IllegalArgumentException) {
                continue
            }
            val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == AppIcon.DEFAULT)
            if (enabled) return icon
        }
        return AppIcon.DEFAULT
    }

    /** Returns true when the switch worked. The new icon is turned on first so there is never zero launcher icons. */
    fun apply(context: Context, icon: AppIcon): Boolean {
        return try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component(context, icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (other in AppIcon.entries) {
                if (other == icon) continue
                pm.setComponentEnabledSetting(
                    component(context, other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
