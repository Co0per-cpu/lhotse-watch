package com.coopercpu.reloj

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchManager.init(this)
    }
}
