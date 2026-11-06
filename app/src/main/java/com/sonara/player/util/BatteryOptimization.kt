package com.sonara.player.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Phone makers (Xiaomi, Samsung, Oppo...) restrict background apps to save battery. A restricted
 * music app can stop moving to the next song, or stop updating its notification, once the
 * screen is off. The fix is for the user to let Sonara run in the background; these helpers
 * check that and open the right settings page.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system list where Sonara can be set to "Don't optimize" (no special permission
     * needed). Falls back to the app's own settings page. Returns false if neither could open.
     */
    fun openSettings(context: Context): Boolean {
        val attempts = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // try the next one
            }
        }
        return false
    }
}
