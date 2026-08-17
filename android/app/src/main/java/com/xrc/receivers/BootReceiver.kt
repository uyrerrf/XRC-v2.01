package com.xrc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xrc.svc.XrcForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED
        ) {
            try {
                XrcForegroundService.start(context)
            } catch (_: Exception) {
                // Android 15+ may restrict FGS type on boot; the
                // accessibility watchdog + next app launch will re-trigger.
            }
        }
    }
}
