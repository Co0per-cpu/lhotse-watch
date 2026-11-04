package com.coopercpu.reloj

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coopercpu.reloj.ui.AppRoot
import com.coopercpu.reloj.ui.RelojTheme

class MainActivity : ComponentActivity() {

    private val permsOk = mutableStateOf(false)
    private val notifAccess = mutableStateOf(false)

    private val needed: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()

    private val askPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permsOk.value = hasPerms()
        if (permsOk.value) {
            WatchManager.start()
            askPostNotifications()
        }
    }

    // La notificación fija de "Conectado" necesita este permiso en Android 13+
    private val askPost = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askPost.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasPerms() = needed.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotifAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openNotifAccess() {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, NotifService::class.java).flattenToString()
            )
        } else Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        try {
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permsOk.value = hasPerms()
        if (permsOk.value) {
            WatchManager.start()
            askPostNotifications()
        } else askPerms.launch(needed)

        com.coopercpu.reloj.ui.acentoActual.value =
            androidx.compose.ui.graphics.Color(WatchManager.settings.accent.toInt())
        setContent {
            RelojTheme {
                AppRoot(
                    permsOk = permsOk.value,
                    onAskPerms = { askPerms.launch(needed) },
                    notifAccess = notifAccess.value,
                    onOpenNotifAccess = { openNotifAccess() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notifAccess.value = hasNotifAccess()
    }
}
