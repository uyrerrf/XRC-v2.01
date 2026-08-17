package com.xrc

import android.app.Application
import com.xrc.core.di.ServiceLocator
import com.xrc.svc.XrcForegroundService

class XrcApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        try {
            XrcForegroundService.start(this)
        } catch (_: Exception) {
            // Best-effort: never let startup fail the process.
        }
    }
}
