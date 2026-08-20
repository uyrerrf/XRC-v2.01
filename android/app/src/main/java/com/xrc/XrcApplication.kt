// ============================================================
// FILE: android/app/src/main/java/com/xrc/XrcApplication.kt
// ============================================================
package com.xrc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.xrc.core.di.ServiceLocator
import com.xrc.core.pref.SecurePrefs
import java.security.Security
import org.conscrypt.Conscrypt

/**
 * XRC Application class.
 *
 * Initializes:
 * - Security providers (Conscrypt for TLS 1.3)
 * - ServiceLocator (manual DI)
 * - SecurePrefs
 * - Notification channels (Android 8+)
 * - Foreground service notification
 */
class XrcApplication : Application() {

    companion object {
        const val CHANNEL_CORE = "xrc_core"
        const val CHANNEL_OVERLAY = "xrc_overlay"
        const val CHANNEL_ALERT = "xrc_alert"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_OVERLAY = 1002

        lateinit var instance: XrcApplication
            private set
    }

    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install Conscrypt as a TLS provider
        installConscrypt()

        // Create notification channels
        createNotificationChannels()

        // Initialize ServiceLocator (manual DI)
        serviceLocator = ServiceLocator(this)
        serviceLocator.initialize()
    }

    private fun installConscrypt() {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Exception) {
            android.util.Log.w("XRC", "Conscrypt installation failed", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val coreChannel = NotificationChannel(
                CHANNEL_CORE,
                "XRC Core Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Required for persistent operation"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(coreChannel)

            val overlayChannel = NotificationChannel(
                CHANNEL_OVERLAY,
                "XRC Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay display notifications"
                setShowBadge(false)
            }
            nm.createNotificationChannel(overlayChannel)

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "XRC Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security alerts"
                setShowBadge(true)
            }
            nm.createNotificationChannel(alertChannel)
        }
    }
}
