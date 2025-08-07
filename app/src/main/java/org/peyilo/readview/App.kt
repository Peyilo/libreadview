package org.peyilo.readview

import android.app.Application
import org.peyilo.libreadview.utils.LogHelper

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        LogHelper.ENABLE_LOG = false            // 关闭内部的日志
    }

}