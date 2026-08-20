// ============================================================
// FILE: android/app/src/main/java/com/xrc/core/net/NetworkUtils.kt
// ============================================================
package com.xrc.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * NetworkUtils — detect network type, get IP addresses,
 * check connectivity.
 */
object NetworkUtils {

    enum class NetworkType {
        WIFI, MOBILE, ETHERNET, VPN, BLUETOOTH, OTHER, NONE
    }

    /**
     * Get current network type.
     */
    fun getNetworkType(context: Context): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.BLUETOOTH
            else -> NetworkType.OTHER
        }
    }

    /**
     * Check if device is connected to the internet.
     */
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Check if currently on WiFi.
     */
    fun isWifi(context: Context): Boolean {
        return getNetworkType(context) == NetworkType.WIFI
    }

    /**
     * Check if currently on mobile data.
     */
    fun isMobileData(context: Context): Boolean {
        return getNetworkType(context) == NetworkType.MOBILE
    }

    /**
     * Check if VPN is active.
     */
    fun isVpnActive(context: Context): Boolean {
        return getNetworkType(context) == NetworkType.VPN
    }

    /**
     * Get local IP address (IPv4).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through
        }
        return "0.0.0.0"
    }

    /**
     * Get WiFi SSID (requires ACCESS_FINE_LOCATION on Android 10+).
     */
    fun getWifiSsid(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo: WifiInfo = wifiManager.connectionInfo
            wifiInfo.ssid?.removeSurrounding("\"") ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Get cellular network operator name.
     */
    fun getCarrierName(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Check if airplane mode is on.
     */
    fun isAirplaneModeOn(context: Context): Boolean {
        return android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }
}
