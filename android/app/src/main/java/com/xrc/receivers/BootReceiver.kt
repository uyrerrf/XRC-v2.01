// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/BootReceiver.kt
// ============================================================
package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xrc.svc.XrcForegroundService

/**
 * BootReceiver — starts XRC service on device boot.
 *
 * Handles:
 * - BOOT_COMPLETED
 * - LOCKED_BOOT_COMPLETED (direct boot)
 * - QUICKBOOT_POWERON (HTC)
 *
 * Sets alarm-based watchdog as fallback.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val WATCHDOG_DELAY_MS = 30000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Boot event: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_USER_PRESENT -> {

                // Start foreground service
                val serviceIntent = Intent(context, XrcForegroundService::class.java).apply {
                    action = XrcForegroundService.ACTION_START
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "Foreground service started from boot")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start service from boot: ${e.message}")
                }

                // Set alarm-based watchdog
                scheduleWatchdog(context)
            }
        }
    }

    private fun scheduleWatchdog(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, XrcForegroundService::class.java).apply {
                action = XrcForegroundService.ACTION_START
            }
            val pendingIntent = android.app.PendingIntent.getService(
                context, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_DELAY_MS,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_DELAY_MS,
                    pendingIntent
                )
            }
            Log.d(TAG, "Watchdog alarm set for ${WATCHDOG_DELAY_MS}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog alarm failed: ${e.message}")
        }
    }
}
