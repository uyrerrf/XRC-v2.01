// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/WiFi.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import com.xrc.core.toJsonString

/**
 * WiFi — WiFi scanning and monitoring module.
 *
 * Captures:
 * - Connected WiFi info (SSID, BSSID, signal strength)
 * - Available networks (scan results)
 * - WiFi state changes
 */
class WiFi(
    private val context: Context,
    // FIXED: nullable with default
    private val channelClient: ChannelClient? = null
) {
    companion object {
        private const val TAG = "WiFiModule"
    }

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Get current connected WiFi info.
     */
    fun getConnectionInfo(): Map<String, Any> {
        return try {
            val info = wifiManager.connectionInfo
            mapOf(
                "ssid" to (info.ssid?.removeSurrounding("\"") ?: "Unknown"),
                "bssid" to (info.bssid ?: "Unknown"),
                "rssi" to info.rssi,
                "frequency" to info.frequency,
                "speed" to info.linkSpeed,
                "ip_address" to android.text.format.Formatter.formatIpAddress(info.ipAddress)
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown"))
        }
    }

    /**
     * Scan for available WiFi networks.
     * Requires ACCESS_FINE_LOCATION on Android 10+.
     */
    fun scanNetworks(): List<Map<String, Any>> {
        val networks = mutableListOf<Map<String, Any>>()
        try {
            if (hasLocationPermission()) {
                wifiManager.startScan()
                val results = wifiManager.scanResults
                for (result in results) {
                    networks.add(
                        mapOf(
                            "ssid" to result.SSID,
                            "bssid" to result.BSSID,
                            "level" to result.level,
                            "frequency" to result.frequency,
                            "capabilities" to result.capabilities
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WiFi scan: ${e.message}")
        }
        return networks
    }

    /**
     * Enable or disable WiFi.
     */
    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            wifiManager.isWifiEnabled = enabled
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if WiFi is enabled.
     */
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    /**
     * Get detailed WiFi info as sensor data report.
     */
    fun reportWiFiInfo() {
        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "wifi_${System.currentTimeMillis()}",
            device_id = deviceId,
            // FIXED: Map<String, Any> → JSON via recursive helper
            payload = mapOf(
                "type" to "wifi_info",
                "data" to getConnectionInfo()
            ).toJsonString()
        )
        channelClient?.send(msg) // FIXED: null-safe
    }

    private fun hasLocationPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
