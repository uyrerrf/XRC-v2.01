// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/BootReceiver.kt  (COMPLETED ABOVE)
// ============================================================

// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/UpdateReceiver.kt
// ============================================================
package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xrc.core.di.ServiceLocator
import com.xrc.svc.XrcForegroundService

/**
 * UpdateReceiver — restarts XRC service after app update/reinstall.
 *
 * Handles:
 * - MY_PACKAGE_REPLACED (self-update)
 * - PACKAGE_REPLACED (when our package is replaced)
 *
 * This ensures the service remains active after an update.
 */
class UpdateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val data = intent.dataString ?: return
        val packageName = data.removePrefix("package:")

        Log.i(TAG, "Update event: $action for $packageName")

        if (packageName != context.packageName) return

        when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Our package was updated/replaced")

                // Restart foreground service
                val serviceIntent = Intent(context, XrcForegroundService::class.java).apply {
                    action = XrcForegroundService.ACTION_START
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restart service after update: ${e.message}")
                }
            }
        }
    }
}
