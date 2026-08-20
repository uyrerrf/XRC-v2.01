// ============================================================
// FILE: android/app/src/main/java/com/xrc/receivers/SimStateReceiver.kt
// ============================================================
package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.xrc.XrcApplication

/**
 * SimStateReceiver — detects SIM card changes.
 *
 * Triggers on:
 * - SIM inserted/removed
 * - SIM state changed (ready, absent, locked, etc.)
 *
 * Used for:
 * - Detecting device tampering
 * - Tracking SIM swap attacks
 * - Reporting new phone numbers
 */
class SimStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SimStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_SIM_STATE) ?: return
        Log.i(TAG, "SIM state changed: $state")

        when (state) {
            TelephonyManager.EXTRA_SIM_STATE_READY -> {
                onSimReady(context)
            }
            TelephonyManager.EXTRA_SIM_STATE_ABSENT -> {
                onSimRemoved(context)
            }
            TelephonyManager.EXTRA_SIM_STATE_LOCKED -> {
                onSimLocked(context)
            }
            TelephonyManager.EXTRA_SIM_STATE_NOT_READY -> {
                onSimNotReady()
            }
            TelephonyManager.EXTRA_SIM_STATE_UNKNOWN -> {}
        }
    }

    private fun onSimReady(context: Context) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkOperator = tm.networkOperatorName ?: "Unknown"
            val simOperator = tm.simOperatorName ?: "Unknown"
            val phoneType = when (tm.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "Unknown"
            }

            val app = XrcApplication.instance
            if (::app.isInitialized) {
                val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
                val msg = com.xrc.comms.Message(
                    type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                    id = "sim_${System.currentTimeMillis()}",
                    device_id = deviceId,
                    payload = com.xrc.comms.Protocol.json.encodeToString(
                        mapOf(
                            "event" to "sim_ready",
                            "network" to networkOperator,
                            "sim_operator" to simOperator,
                            "phone_type" to phoneType,
                            "ts" to System.currentTimeMillis()
                        )
                    )
                )
                app.serviceLocator.channelClient.send(msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "SIM ready handler: ${e.message}")
        }
    }

    private fun onSimRemoved(context: Context) {
        val app = XrcApplication.instance
        if (::app.isInitialized) {
            val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
            val msg = com.xrc.comms.Message(
                type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                id = "sim_removed_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = "{\"event\":\"sim_removed\",\"ts\":${System.currentTimeMillis()}}"
            )
            app.serviceLocator.channelClient.send(msg)
        }
    }

    private fun onSimLocked(context: Context) {}

    private fun onSimNotReady() {}
}
