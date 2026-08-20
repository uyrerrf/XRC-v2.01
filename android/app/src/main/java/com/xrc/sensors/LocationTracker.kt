// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/LocationTracker.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * LocationTracker — acquires device GPS location.
 *
 * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
 */
class LocationTracker(private val context: Context) {
    companion object {
        private const val TAG = "LocationTracker"
        private const val TIMEOUT_MS = 15000L
    }

    /**
     * Get current location (lat, lon).
     * Blocks until location acquired or timeout.
     */
    fun getLocation(): Pair<Double, Double>? {
        if (!hasPermission()) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try GPS first, then network
        var location: Location? = null

        val gpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val networkEnabled = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }

        if (gpsEnabled) {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (location == null && networkEnabled) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        // If stale, request fresh fix
        if (location == null || location.time < System.currentTimeMillis() - 60000) {
            location = requestFreshFix(lm, gpsEnabled, networkEnabled)
        }

        return location?.let { Pair(it.latitude, it.longitude) }
    }

    /**
     * Get location with accuracy information.
     */
    fun getLocationDetailed(): Map<String, Any>? {
        val loc = getLocation() ?: return null
        return mapOf(
            "lat" to loc.first,
            "lon" to loc.second,
            "timestamp" to System.currentTimeMillis()
        )
    }

    private fun requestFreshFix(
        lm: LocationManager,
        gpsEnabled: Boolean,
        networkEnabled: Boolean
    ): Location? {
        val latch = CountDownLatch(1)
        var result: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                result = location
                latch.countDown()
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (gpsEnabled) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
            }
            if (networkEnabled) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            }
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Location fix failed: ${e.message}")
        } finally {
            lm.removeUpdates(listener)
        }

        return result
    }

    private fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
