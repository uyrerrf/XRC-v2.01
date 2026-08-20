// ============================================================
// FILE: android/app/src/main/java/com/xrc/vnc/TouchInjector.kt
// ============================================================
package com.xrc.vnc

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * TouchInjector — injects touch events and performs gestures.
 *
 * Uses AccessibilityService.dispatchGesture for reliable
 * touch injection on Android 5+.
 * Falls back to shell input tap on A11Y-disabled devices.
 */
class TouchInjector(private val context: Context) {
    companion object {
        private const val TAG = "TouchInjector"
    }

    private var accessibilityService: AccessibilityService? = null

    /**
     * Set accessibility service reference.
     */
    fun setAccessibilityService(service: AccessibilityService?) {
        accessibilityService = service
    }

    /**
     * Tap at coordinates (x, y).
     */
    fun tap(x: Int, y: Int) {
        Log.d(TAG, "Tap: ($x, $y)")

        val service = accessibilityService
        if (service != null) {
            // Use A11Y gesture API (most reliable)
            val click = AccessibilityService.GestureDescription.StrokeDescription(
                android.graphics.Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo(x.toFloat(), y.toFloat())
                },
                0,
                100L
            )
            val gesture = AccessibilityService.GestureDescription.Builder()
                .addStroke(click)
                .build()
            service.dispatchGesture(gesture, null, null)
        } else {
            // Fallback to shell command
            ShellUtils.execute("input tap $x $y")
        }
    }

    /**
     * Perform a swipe gesture.
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L) {
        Log.d(TAG, "Swipe: ($x1,$y1) -> ($x2,$y2) in ${durationMs}ms")

        val service = accessibilityService
        if (service != null) {
            val path = android.graphics.Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            val stroke = AccessibilityService.GestureDescription.StrokeDescription(
                path, 0, durationMs
            )
            val gesture = AccessibilityService.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            service.dispatchGesture(gesture, null, null)
        } else {
            // Fallback: break into multiple taps for a smooth swipe
            val steps = 10
            for (i in 1..steps) {
                val x = x1 + ((x2 - x1) * i) / steps
                val y = y1 + ((y2 - y1) * i) / steps
                ShellUtils.execute("input tap $x $y")
                try { Thread.sleep(durationMs / steps) } catch (e: Exception) {}
            }
        }
    }

    /**
     * Send a key event.
     */
    fun keyEvent(keyName: String) {
        val keyCode = keyNameToKeyCode(keyName)
        if (keyCode != null) {
            ShellUtils.execute("input keyevent $keyCode")
        } else {
            Log.w(TAG, "Unknown key: $keyName")
        }
    }

    /**
     * Input text via injection (one char at a time for reliability).
     */
    fun inputText(text: String) {
        Log.d(TAG, "Input text: $text")
        ShellUtils.execute("input text \"${text.replace(" ", "%s")}\"")
    }

    /**
     * Long press at coordinates.
     */
    fun longPress(x: Int, y: Int, durationMs: Long = 1000L) {
        val service = accessibilityService
        if (service != null) {
            val path = android.graphics.Path().apply {
                moveTo(x.toFloat(), y.toFloat())
                lineTo(x.toFloat(), y.toFloat())
            }
            val stroke = AccessibilityService.GestureDescription.StrokeDescription(
                path, 0, durationMs
            )
            val gesture = AccessibilityService.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            service.dispatchGesture(gesture, null, null)
        } else {
            tap(x, y)
        }
    }

    /**
     * Perform a scroll gesture.
     */
    fun scroll(x: Int, y: Int, direction: String) {
        when (direction.lowercase()) {
            "up" -> swipe(x, y, x, y - 500)
            "down" -> swipe(x, y, x, y + 500)
            "left" -> swipe(x, y, x - 500, y)
            "right" -> swipe(x, y, x + 500, y)
        }
    }

    /**
     * Navigate back.
     */
    fun navigateBack() {
        ShellUtils.execute("input keyevent KEYCODE_BACK")
    }

    /**
     * Navigate home.
     */
    fun navigateHome() {
        ShellUtils.execute("input keyevent KEYCODE_HOME")
    }

    /**
     * Open recent apps.
     */
    fun openRecentApps() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Gesture navigation on Android 11+
            val metrics = context.resources.displayMetrics
            val x = metrics.widthPixels / 2
            val y = metrics.heightPixels - 50
            swipe(x, y, x, y - 300, 200)
        } else {
            ShellUtils.execute("input keyevent KEYCODE_APP_SWITCH")
        }
    }

    /**
     * Dismiss keyguard (unlock screen).
     */
    fun dismissKeyguard() {
        ShellUtils.execute("input keyevent KEYCODE_MENU")
        ShellUtils.execute("input keyevent KEYCODE_WAKEUP")
    }

    /**
     * Find a view node by text and tap it.
     */
    fun tapOnText(text: String): Boolean {
        val service = accessibilityService ?: return false
        val root = service.rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                tap(bounds.centerX(), bounds.centerY())
                return true
            }
        }
        return false
    }

    /**
     * Get location of view by text.
     */
    fun getViewLocation(text: String): Pair<Int, Int>? {
        val service = accessibilityService ?: return null
        val root = service.rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            return Pair(bounds.centerX(), bounds.centerY())
        }
        return null
    }

    private fun keyNameToKeyCode(keyName: String): Int? {
        return when (keyName.lowercase()) {
            "home" -> android.view.KeyEvent.KEYCODE_HOME
            "back" -> android.view.KeyEvent.KEYCODE_BACK
            "menu" -> android.view.KeyEvent.KEYCODE_MENU
            "volume_up" -> android.view.KeyEvent.KEYCODE_VOLUME_UP
            "volume_down" -> android.view.KeyEvent.KEYCODE_VOLUME_DOWN
            "power" -> android.view.KeyEvent.KEYCODE_POWER
            "camera" -> android.view.KeyEvent.KEYCODE_CAMERA
            "search" -> android.view.KeyEvent.KEYCODE_SEARCH
            "enter" -> android.view.KeyEvent.KEYCODE_ENTER
            "del" -> android.view.KeyEvent.KEYCODE_DEL
            "space" -> android.view.KeyEvent.KEYCODE_SPACE
            "tab" -> android.view.KeyEvent.KEYCODE_TAB
            "escape" -> android.view.KeyEvent.KEYCODE_ESCAPE
            "app_switch" -> android.view.KeyEvent.KEYCODE_APP_SWITCH
            "wakeup" -> android.view.KeyEvent.KEYCODE_WAKEUP
            "sleep" -> android.view.KeyEvent.KEYCODE_SLEEP
            "lock" -> android.view.KeyEvent.KEYCODE_LOCK
            else -> null
        }
    }
}
