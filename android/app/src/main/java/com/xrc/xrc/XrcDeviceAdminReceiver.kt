package com.xrc.xrc

import com.xrc.R
import android.os.Build
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class XrcDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_description)

    override fun onEnabled(context: Context, intent: Intent) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                dpm.setUninstallBlocked(getComponentName(context), context.packageName, true)
            }
        } catch (_: Exception) {
            // Requires DO/PO on some paths; best-effort.
        }
    }

    companion object {
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context, XrcDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(getComponentName(context))
        }
    }
}
