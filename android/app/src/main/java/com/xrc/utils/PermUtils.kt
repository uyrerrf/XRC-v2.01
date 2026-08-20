// ============================================================
// FILE: android/app/src/main/java/com/xrc/utils/PermUtils.kt
// ============================================================
package com.xrc.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * PermUtils — permission management utilities.
 *
 * Handles requesting:
 * - AccessibilityService
 * - SYSTEM_ALERT_WINDOW (overlay)
 * - Device Admin
 * - All runtime permissions
 */
object PermUtils {
    const val OVERLAY_PERMISSION_REQUEST_CODE = 9001
    const val ACCESSIBILITY_REQUEST_CODE = 9002
    const val ADMIN_REQUEST_CODE = 9003

    private const val TAG = "PermUtils"

    /**
     * Check if AccessibilityService is enabled for our package.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    /**
     * Open accessibility settings to enable our service.
     */
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        activity.startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE)
    }

    /**
     * Request SYSTEM_ALERT_WINDOW permission.
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Check if overlay permission is granted.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    /**
     * Open battery optimization settings to disable for our app.
     */
    fun openBatteryOptimizationSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Check if battery optimization is disabled for our app.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * Open manage storage settings (for MANAGE_EXTERNAL_STORAGE on Android 11+).
     */
    fun openManageStorageSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }

    /**
     * Open notification settings for our app.
     */
    fun openNotificationSettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
