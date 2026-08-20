// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/ClipboardMonitor.kt
// ============================================================
package com.xrc.sensors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * ClipboardMonitor — monitors and captures clipboard content.
 *
 * On Android 10+, ClipboardManager.OnPrimaryClipChangedListener
 * is restricted; falls back to polling in background.
 */
class ClipboardMonitor(private val context: Context) {
    companion object {
        private const val TAG = "ClipboardMonitor"
        private const val POLL_INTERVAL_MS = 3000L
    }

    private var lastClip: String? = null
    private var isMonitoring = false
    private var monitorThread: Thread? = null

    /**
     * Get current clipboard content.
     */
    fun getCurrent(): String? {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard read failed: ${e.message}")
            null
        }
    }

    /**
     * Start monitoring clipboard changes (polling).
     */
    fun startMonitoring(): Boolean {
        if (isMonitoring) return false
        isMonitoring = true

        monitorThread = Thread {
            while (isMonitoring) {
                try {
                    val current = getCurrent()
                    if (current != null && current != lastClip) {
                        lastClip = current
                        onClipChanged?.invoke(current)
                    }
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Clipboard poll error: ${e.message}")
                }
            }
        }
        monitorThread?.start()
        return true
    }

    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitorThread?.interrupt()
        monitorThread = null
    }

    /**
     * Set clipboard content.
     */
    fun set(text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            cm.setPrimaryClip(clip)
            lastClip = text
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard set failed: ${e.message}")
        }
    }

    /**
     * Clear clipboard.
     */
    fun clear() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", "")
            cm.setPrimaryClip(clip)
            lastClip = null
        } catch (e: Exception) {}
    }

    var onClipChanged: ((String) -> Unit)? = null
}
