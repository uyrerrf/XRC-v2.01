// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/NotificationInterceptor.kt
// ============================================================
package com.xrc.sensors

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * NotificationInterceptor — captures notifications via A11Y service.
 *
 * Processes TYPE_NOTIFICATION_STATE_CHANGED events
 * and extracts title, text, package, and timestamp.
 */
class NotificationInterceptor {
    companion object {
        private const val TAG = "NotifInterceptor"
        private const val MAX_CAPTURED = 500
    }

    private val capturedNotifications = mutableListOf<Map<String, Any>>()
    private var isCapturing = false

    /**
     * Start capturing notifications.
     */
    fun startCapture() {
        isCapturing = true
        Log.i(TAG, "Notification capture started")
    }

    /**
     * Stop capturing notifications.
     */
    fun stopCapture() {
        isCapturing = false
        Log.i(TAG, "Notification capture stopped")
    }

    /**
     * Process an accessibility event for notification capture.
     */
    fun processEvent(event: AccessibilityEvent) {
        if (!isCapturing) return
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val text = event.text?.joinToString(" ") ?: ""

        // Try to extract more info from the notification node
        val nodeInfo = event.source
        var title = ""
        var body = ""

        if (nodeInfo != null) {
            title = extractTitle(nodeInfo)
            body = extractText(nodeInfo)
        }

        val notification = mapOf<String, Any>(
            "package" to pkg,
            "title" to (title.ifEmpty { text.take(80) }),
            "body" to (body.ifEmpty { text }),
            "timestamp" to System.currentTimeMillis(),
            "event_time" to event.eventTime
        )

        synchronized(capturedNotifications) {
            capturedNotifications.add(0, notification)
            if (capturedNotifications.size > MAX_CAPTURED) {
                capturedNotifications.removeAt(capturedNotifications.size - 1)
            }
        }

        Log.d(TAG, "Captured notification from $pkg: ${notification["title"]}")
        onNotification?.invoke(notification)
    }

    /**
     * Get all captured notifications.
     */
    fun getCaptured(): List<Map<String, Any>> {
        synchronized(capturedNotifications) {
            return capturedNotifications.toList()
        }
    }

    /**
     * Clear captured notifications.
     */
    fun clearCaptured() {
        synchronized(capturedNotifications) {
            capturedNotifications.clear()
        }
    }

    /**
     * Get count of captured notifications.
     */
    fun getCount(): Int {
        synchronized(capturedNotifications) {
            return capturedNotifications.size
        }
    }

    /**
     * Search captured notifications for keywords.
     */
    fun search(keywords: List<String>): List<Map<String, Any>> {
        synchronized(capturedNotifications) {
            return capturedNotifications.filter { notif ->
                val title = (notif["title"] as? String)?.lowercase() ?: ""
                val body = (notif["body"] as? String)?.lowercase() ?: ""
                keywords.any { title.contains(it.lowercase()) || body.contains(it.lowercase()) }
            }
        }
    }

    private fun extractTitle(node: AccessibilityNodeInfo): String {
        var title = ""
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (child.text != null) {
                    title = child.text.toString()
                    child.recycle()
                    return title
                }
                child.recycle()
            }
        }
        return title
    }

    private fun extractText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        extractAllText(node, texts)
        return texts.joinToString(" ").take(500)
    }

    private fun extractAllText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        if (node.text != null) {
            texts.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractAllText(child, texts)
                child.recycle()
            }
        }
    }

    var onNotification: ((Map<String, Any>) -> Unit)? = null
}
