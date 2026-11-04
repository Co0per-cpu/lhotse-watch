package com.coopercpu.reloj

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Mantiene la app viva en segundo plano para que el reloj siga conectado,
 * reciba notificaciones y se actualice el clima cada hora.
 */
class ConexionService : Service() {

    private val main = Handler(Looper.getMainLooper())

    private val weatherTick = object : Runnable {
        override fun run() {
            updateWeather(this@ConexionService, force = false)
            main.postDelayed(this, 30 * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Conexión con el reloj", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Mi Reloj")
            .setContentText("Conectado a tu reloj")
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, 1, notif, type)
        } catch (e: Exception) {
            Log.w("Reloj", "no se pudo iniciar en primer plano", e)
            stopSelf()
            return
        }
        WatchManager.onConnectedHook = { updateWeather(this, force = false) }
        main.postDelayed(weatherTick, 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        main.removeCallbacks(weatherTick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CANAL = "conexion"

        fun start(context: Context) {
            try {
                val i = Intent(context, ConexionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (e: Exception) {
                Log.w("Reloj", "no se pudo iniciar el servicio", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConexionService::class.java))
        }

        /** Descarga el clima y lo manda al reloj (si pasó más de una hora o si se fuerza). */
        fun updateWeather(context: Context, force: Boolean, onDone: ((String) -> Unit)? = null) {
            val s = WatchManager.settings
            if (s.cityName.isBlank()) { WatchManager.dlog("clima: falta elegir ciudad"); onDone?.invoke("Primero elige tu ciudad."); return }
            if (!force && (!s.weatherAuto || System.currentTimeMillis() - s.lastWeather < 55 * 60_000L)) return
            Thread {
                try {
                    val w = WeatherClient.forecast(s.lat, s.lon)
                    Handler(Looper.getMainLooper()).post {
                        WatchManager.sendWeather(w, s.cityName) { ok ->
                            onDone?.invoke(
                                if (ok) "Clima enviado: ${w.temp}° en ${s.cityName}."
                                else "El reloj no aceptó el clima. Revisa el diagnóstico en Ajustes."
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Reloj", "clima", e)
                    Handler(Looper.getMainLooper()).post { WatchManager.dlog("clima: no se pudo descargar: $e") }
                    onDone?.invoke("No pude descargar el clima. Revisa tu conexión a internet.")
                }
            }.start()
        }
    }
}
