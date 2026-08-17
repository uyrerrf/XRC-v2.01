package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xrc.svc.XrcForegroundService

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                XrcForegroundService.start(context)
            } catch (_: Exception) {
                // Best-effort restart after OTA update.
            }
        }
    }
}
