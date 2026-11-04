package com.coopercpu.reloj

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.idosmart.model.IDOBleDeviceModel
import org.json.JSONObject
import java.io.File

/** Resumen de pulso de un día, armado desde lo que manda el reloj. */
data class HeartDay(
    val key: String,          // "2026-11-04"
    val year: Int,
    val month: Int,
    val day: Int,
    val resting: Int,         // pulso en reposo
    val min: Int,
    val max: Int,
    val avg: Int,
    val samples: List<Int>    // valores en orden, para el gráfico
)

/** Un ejercicio registrado en el reloj. */
data class Workout(
    val key: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val type: Int,
    val durationSec: Int,
    val distanceM: Int,
    val calories: Int,
    val steps: Int,
    val avgHr: Int,
    val maxHr: Int,
    val minHr: Int
)

/**
 * Todo se guarda solo en el teléfono, en la carpeta privada de la app.
 * Nada se envía a internet.
 */
class Store(context: Context) {
    private val gson = Gson()
    private val dir = File(context.filesDir, "datos").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("reloj", Context.MODE_PRIVATE)

    // ---------- Reloj vinculado ----------

    fun saveDevice(device: IDOBleDeviceModel) {
        prefs.edit()
            .putString("device", gson.toJson(device))
            .putString("bound_mac", device.macAddress ?: "")
            .apply()
    }

    fun loadDevice(): IDOBleDeviceModel? = try {
        prefs.getString("device", null)?.let { gson.fromJson(it, IDOBleDeviceModel::class.java) }
    } catch (e: Exception) {
        null
    }

    fun boundMac(): String = prefs.getString("bound_mac", "") ?: ""

    fun clearDevice() {
        prefs.edit().remove("device").remove("bound_mac").apply()
    }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(v) = prefs.edit().putLong("last_sync", v).apply()

    // ---------- Pulso ----------

    fun loadHeartDays(): List<HeartDay> =
        readList<HeartDay>("pulso.json").sortedByDescending { it.key }

    /** Recibe el JSON de pulso del reloj y lo guarda. Devuelve true si se pudo leer. */
    fun addHeartJson(json: String): Boolean {
        val day = parseHeart(json) ?: return false
        val all = readList<HeartDay>("pulso.json").filter { it.key != day.key } + day
        writeList("pulso.json", all)
        appendRaw("crudo_pulso.jsonl", json)
        return true
    }

    // ---------- Ejercicios ----------

    fun loadWorkouts(): List<Workout> =
        readList<Workout>("ejercicios.json").sortedByDescending { it.key }

    fun addWorkoutJson(json: String): Boolean {
        val w = parseWorkout(json) ?: return false
        val all = readList<Workout>("ejercicios.json").filter { it.key != w.key } + w
        writeList("ejercicios.json", all)
        appendRaw("crudo_ejercicios.jsonl", json)
        return true
    }

    // ---------- Lectura del formato IDO ----------

    private fun parseHeart(json: String): HeartDay? = try {
        val o = JSONObject(json)
        val y = o.optInt("year"); val m = o.optInt("month"); val d = o.optInt("day")
        if (y == 0 || m == 0 || d == 0) null else {
            val values = mutableListOf<Int>()
            o.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i)?.optInt("heart_rateVal") ?: 0
                    if (v in 25..240) values += v
                }
            }
            if (values.isEmpty()) {
                o.optJSONArray("five_min_data")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val v = arr.optInt(i)
                        if (v in 25..240) values += v
                    }
                }
            }
            HeartDay(
                key = "%04d-%02d-%02d".format(y, m, d),
                year = y, month = m, day = d,
                resting = o.optInt("silent_hr"),
                min = values.minOrNull() ?: 0,
                max = values.maxOrNull() ?: 0,
                avg = if (values.isEmpty()) 0 else values.average().toInt(),
                samples = values
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun parseWorkout(json: String): Workout? = try {
        val o = JSONObject(json)
        val y = o.optInt("year"); val m = o.optInt("month"); val d = o.optInt("day")
        val h = o.optInt("hour"); val mi = o.optInt("minute"); val s = o.optInt("second")
        if (y == 0 || m == 0 || d == 0) null else Workout(
            key = "%04d-%02d-%02d %02d:%02d:%02d".format(y, m, d, h, mi, s),
            year = y, month = m, day = d, hour = h, minute = mi,
            type = o.optInt("type"),
            durationSec = o.optInt("durations"),
            distanceM = o.optInt("distance"),
            calories = o.optInt("calories"),
            steps = o.optInt("step"),
            avgHr = o.optInt("avg_hr_value"),
            maxHr = o.optInt("max_hr_value"),
            minHr = o.optInt("min_hr_value")
        )
    } catch (e: Exception) {
        null
    }

    // ---------- Archivos ----------

    private inline fun <reified T> readList(name: String): List<T> = try {
        val f = File(dir, name)
        if (!f.exists()) emptyList()
        else gson.fromJson<List<T>>(f.readText(), object : TypeToken<List<T>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun <T> writeList(name: String, list: List<T>) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(gson.toJson(list))
        tmp.renameTo(File(dir, name))
    }

    /** Copia exacta de lo que mandó el reloj, por si después queremos leer más campos. */
    private fun appendRaw(name: String, json: String) {
        try {
            File(dir, name).appendText(json.replace("\n", " ") + "\n")
        } catch (_: Exception) {
        }
    }
}
