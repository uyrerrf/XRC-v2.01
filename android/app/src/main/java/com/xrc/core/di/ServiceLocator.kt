package com.xrc.core.di

import android.content.Context
import com.xrc.core.config.XrcConfig

object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var config: XrcConfig? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    val app = context.applicationContext
                    appContext = app
                    config = XrcConfig.load(app)
                }
            }
        }
    }

    fun context(): Context =
        checkNotNull(appContext) { "ServiceLocator not initialized" }

    fun config(): XrcConfig =
        checkNotNull(config) { "ServiceLocator not initialized" }
}
