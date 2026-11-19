package com.sonara.player.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Country-level music recommendations. Raw coordinates are never sent to the music catalogue. */
data class MusicRegion(
    val countryIso2: String,
    val countryIso3: String,
    val displayName: String,
) {
    companion object {
        fun fromCountryCode(countryCode: String?): MusicRegion? {
            val iso2 = countryCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (iso2.length != 2) return null
            val locale = runCatching { Locale.Builder().setRegion(iso2).build() }.getOrNull() ?: return null
            val iso3 = runCatching { locale.isO3Country }.getOrNull()
                ?.takeIf { it.length == 3 && it != iso2 }
                ?: return null
            val name = locale.getDisplayCountry(Locale.getDefault()).ifBlank { iso2 }
            return MusicRegion(iso2, iso3, name)
        }

        fun fromDeviceRegion(): MusicRegion =
            fromCountryCode(Locale.getDefault().country)
                ?: MusicRegion("PH", "PHL", "Philippines")
    }
}

/**
 * Makes a one-time, foreground-only approximate location request. The app resolves that location
 * to a country and passes only the ISO country code to Jamendo for regional charts.
 * No background location or precise-location permission is requested.
 */
class LocationRepository(context: Context) {
    private val appContext = context.applicationContext

    suspend fun detectMusicRegion(): MusicRegion? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) return@withContext null

        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val cached = lastKnownLocation(manager)
            ?.takeIf { System.currentTimeMillis() - it.time <= MAX_CACHED_AGE_MS }
        val current = requestCurrentLocation(manager)
        val location = current ?: cached ?: return@withContext null

        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            MusicRegion.fromCountryCode(addresses?.firstOrNull()?.countryCode)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnownLocation(manager: LocationManager): Location? {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    @Suppress("MissingPermission")
    private suspend fun requestCurrentLocation(manager: LocationManager): Location? {
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { name -> runCatching { manager.isProviderEnabled(name) }.getOrDefault(false) }
            ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            withTimeoutOrNull(7_000L) {
                suspendCancellableCoroutine { continuation ->
                    val signal = CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    try {
                        manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    } catch (_: SecurityException) {
                        if (continuation.isActive) continuation.resume(null)
                    } catch (_: RuntimeException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        } else {
            withTimeoutOrNull(7_000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (continuation.isActive) continuation.resume(location)
                            manager.removeUpdates(this)
                        }

                        @Deprecated("Deprecated by Android")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    try {
                        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_CACHED_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
