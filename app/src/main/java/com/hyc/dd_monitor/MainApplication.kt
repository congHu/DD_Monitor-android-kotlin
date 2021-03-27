package com.hyc.dd_monitor

import android.app.Application
import com.hyc.dd_monitor.utils.CrashHandler





class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val crashHandler = CrashHandler.getInstance()
        crashHandler.init(this)
    }
}