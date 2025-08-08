package org.peyilo.readview

import android.app.Application
import android.content.Context
import org.peyilo.libreadview.utils.LogHelper

class App: Application() {

    companion object {
        lateinit var applicationContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        App.applicationContext = applicationContext
        LogHelper.ENABLE_LOG = true            // 内部的日志开关
    }

}