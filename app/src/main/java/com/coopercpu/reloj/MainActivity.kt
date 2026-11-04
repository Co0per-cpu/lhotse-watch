package com.coopercpu.reloj

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.coopercpu.reloj.ui.AppRoot
import com.coopercpu.reloj.ui.RelojTheme

class MainActivity : ComponentActivity() {

    private val permsOk = mutableStateOf(false)

    private val needed: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private val askPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permsOk.value = result.values.all { it } || hasPerms()
        if (permsOk.value) WatchManager.start()
    }

    private fun hasPerms() = needed.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permsOk.value = hasPerms()
        if (permsOk.value) WatchManager.start() else askPerms.launch(needed)

        setContent {
            RelojTheme {
                AppRoot(
                    permsOk = permsOk.value,
                    onAskPerms = { askPerms.launch(needed) }
                )
            }
        }
    }
}
