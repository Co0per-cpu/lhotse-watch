package com.coopercpu.reloj

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Comparte los datos del reloj con Lumi (u otra app tuya) en el mismo teléfono.
 * Solo pueden leer las apps a las que tú les des el permiso "Datos de Mi Reloj".
 *
 *   content://com.coopercpu.reloj.datos/ahora       estado + datos del día
 *   content://com.coopercpu.reloj.datos/pulso       pulso por día
 *   content://com.coopercpu.reloj.datos/ejercicios  ejercicios
 *
 * Acciones con call(): "notificar" (extras: titulo, texto), "medir_pulso", "sincronizar".
 */
class DatosProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = when (uri.lastPathSegment) {
        "ahora" -> {
            val c = MatrixCursor(arrayOf(
                "conectado", "vinculado", "reloj", "pulso", "pulso_medido",
                "pasos", "calorias", "distancia_m", "minutos_activos",
                "meta_pasos", "clima", "ultima_sincronizacion"
            ))
            val live = WatchManager.live.value
            c.addRow(arrayOf<Any?>(
                if (WatchManager.conn.value == Conn.CONECTADO) 1 else 0,
                if (WatchManager.bound.value) 1 else 0,
                WatchManager.device.value?.name ?: "",
                live.heartRate,
                WatchManager.measuredHr.value,
                live.steps, live.calories, live.distanceM, live.activeMin,
                WatchManager.settings.stepGoal,
                WatchManager.weatherInfo.value,
                WatchManager.lastSync.value
            ))
            c
        }
        "pulso" -> {
            val c = MatrixCursor(arrayOf("fecha", "reposo", "minimo", "promedio", "maximo"))
            WatchManager.heart.value.forEach { c.addRow(arrayOf<Any?>(it.key, it.resting, it.min, it.avg, it.max)) }
            c
        }
        "ejercicios" -> {
            val c = MatrixCursor(arrayOf(
                "inicio", "tipo", "nombre", "duracion_s", "distancia_m", "calorias",
                "pasos", "pulso_prom", "pulso_max", "pulso_min"
            ))
            WatchManager.workouts.value.forEach {
                c.addRow(arrayOf<Any?>(
                    it.key, it.type, com.coopercpu.reloj.ui.deporte(it.type), it.durationSec, it.distanceM,
                    it.calories, it.steps, it.avgHr, it.maxHr, it.minHr
                ))
            }
            c
        }
        else -> null
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // call() no queda protegido por el permiso del manifiesto: se revisa a mano
        val ctx = context ?: throw SecurityException("sin contexto")
        if (ctx.checkCallingPermission(PERMISO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Falta el permiso $PERMISO")
        }
        val out = Bundle()
        val main = Handler(Looper.getMainLooper())
        when (method) {
            "notificar" -> {
                val title = extras?.getString("titulo") ?: "Lumi"
                val text = extras?.getString("texto") ?: arg ?: ""
                main.post { WatchManager.sendNotice(0x18, title, text) }
                out.putBoolean("ok", WatchManager.conn.value == Conn.CONECTADO)
            }
            "medir_pulso" -> {
                main.post { WatchManager.measureHeart() }
                out.putBoolean("ok", WatchManager.conn.value == Conn.CONECTADO)
            }
            "sincronizar" -> {
                main.post { WatchManager.sync() }
                out.putBoolean("ok", WatchManager.conn.value == Conn.CONECTADO)
            }
            "refrescar" -> {
                main.post { WatchManager.refreshLive() }
                out.putBoolean("ok", true)
            }
            else -> out.putBoolean("ok", false)
        }
        return out
    }

    override fun getType(uri: Uri): String? = null

    companion object {
        const val PERMISO = "com.coopercpu.reloj.permission.DATOS"
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
