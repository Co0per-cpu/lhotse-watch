package com.coopercpu.reloj

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchManager.init(this)
        // Si Android abre la app en segundo plano (notificaciones, Lumi), también reconecta el reloj
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            WatchManager.start()
        }
    }
}
