package com.coopercpu.reloj

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Una app cuyas notificaciones se pueden mandar al reloj. */
data class NotifApp(val label: String, val packages: List<String>, val type: Int)

/** Tipos de mensaje según la tabla oficial de IDO. */
val NOTIF_APPS = listOf(
    NotifApp("WhatsApp", listOf("com.whatsapp"), 0x08),
    NotifApp("WhatsApp Business", listOf("com.whatsapp.w4b"), 0x38),
    NotifApp("Mensajes (SMS)", listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"), 0x01),
    NotifApp("Instagram", listOf("com.instagram.android"), 0x0A),
    NotifApp("Messenger", listOf("com.facebook.orca"), 0x09),
    NotifApp("Facebook", listOf("com.facebook.katana"), 0x06),
    NotifApp("Telegram", listOf("org.telegram.messenger"), 0x17),
    NotifApp("Gmail", listOf("com.google.android.gm"), 0x14),
    NotifApp("Outlook", listOf("com.microsoft.office.outlook"), 0x15),
    NotifApp("Discord", listOf("com.discord"), 0x18),
    NotifApp("X (Twitter)", listOf("com.twitter.android"), 0x07),
    NotifApp("TikTok", listOf("com.zhiliaoapp.musically"), 0x26),
    NotifApp("YouTube", listOf("com.google.android.youtube"), 0x24),
    NotifApp("Snapchat", listOf("com.snapchat.android"), 0x16),
    NotifApp("Teams", listOf("com.microsoft.teams"), 0x48),
    NotifApp("Calendario", listOf("com.samsung.android.calendar", "com.google.android.calendar"), 0x0C),
)

/** Apps que muestran las llamadas entrantes. */
val CALL_PACKAGES = setOf(
    "com.samsung.android.incallui",
    "com.samsung.android.dialer",
    "com.google.android.dialer",
    "com.android.server.telecom",
)

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val on: Boolean,
    val days: Set<Int>   // 1 = lunes … 7 = domingo
)

/** Preferencias del usuario, guardadas en el teléfono. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Notificaciones
    var notifLabels: Set<String>
        get() = p.getStringSet("notif_apps", setOf("WhatsApp", "Mensajes (SMS)", "Instagram", "Gmail")) ?: emptySet()
        set(v) = p.edit().putStringSet("notif_apps", v).apply()
    var calls: Boolean
        get() = p.getBoolean("calls", true)
        set(v) = p.edit().putBoolean("calls", v).apply()

    // Clima
    var cityName: String
        get() = p.getString("city", "") ?: ""
        set(v) = p.edit().putString("city", v).apply()
    var lat: Double
        get() = p.getFloat("lat", 0f).toDouble()
        set(v) = p.edit().putFloat("lat", v.toFloat()).apply()
    var lon: Double
        get() = p.getFloat("lon", 0f).toDouble()
        set(v) = p.edit().putFloat("lon", v.toFloat()).apply()
    var weatherAuto: Boolean
        get() = p.getBoolean("weather_auto", true)
        set(v) = p.edit().putBoolean("weather_auto", v).apply()
    var lastWeather: Long
        get() = p.getLong("last_weather", 0L)
        set(v) = p.edit().putLong("last_weather", v).apply()

    // Pantalla
    var brightness: Int
        get() = p.getInt("brightness", 60)
        set(v) = p.edit().putInt("brightness", v).apply()
    var raiseToWake: Boolean
        get() = p.getBoolean("raise", true)
        set(v) = p.edit().putBoolean("raise", v).apply()
    var screenSeconds: Int
        get() = p.getInt("screen_sec", 5)
        set(v) = p.edit().putInt("screen_sec", v).apply()

    // Pulso automático: 0 apagado, 1 automático, 2 continuo
    var hrMode: Int
        get() = p.getInt("hr_mode", 1)
        set(v) = p.edit().putInt("hr_mode", v).apply()
    var hrInterval: Int
        get() = p.getInt("hr_interval", 10)
        set(v) = p.edit().putInt("hr_interval", v).apply()

    // Meta de pasos
    var stepGoal: Int
        get() = p.getInt("step_goal", 8000)
        set(v) = p.edit().putInt("step_goal", v).apply()

    // Alarmas
    var alarms: List<Alarm>
        get() = try {
            gson.fromJson<List<Alarm>>(p.getString("alarms", "[]"), object : TypeToken<List<Alarm>>() {}.type)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        set(v) = p.edit().putString("alarms", gson.toJson(v)).apply()

    fun typeForPackage(pkg: String): Int? {
        val enabled = notifLabels
        return NOTIF_APPS.firstOrNull { pkg in it.packages && it.label in enabled }?.type
    }
}
