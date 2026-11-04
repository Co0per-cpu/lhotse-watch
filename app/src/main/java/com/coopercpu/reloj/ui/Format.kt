package com.coopercpu.reloj.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val meses = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

fun fecha(year: Int, month: Int, day: Int): String =
    "$day ${meses.getOrElse(month - 1) { "?" }} $year"

fun duracion(seg: Int): String {
    val h = seg / 3600
    val m = (seg % 3600) / 60
    val s = seg % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        m > 0 -> "${m} min ${s} s"
        else -> "${s} s"
    }
}

fun distancia(metros: Int): String =
    if (metros >= 1000) String.format(Locale.US, "%.2f km", metros / 1000.0) else "$metros m"

fun haceCuanto(millis: Long): String {
    if (millis == 0L) return "nunca"
    return SimpleDateFormat("d MMM, HH:mm", Locale("es", "CL")).format(Date(millis))
}

/** Nombres en español de los tipos de ejercicio del reloj (tabla oficial IDO). */
fun deporte(tipo: Int): String = when (tipo) {
    0x01 -> "Caminata"
    0x02 -> "Carrera"
    0x03 -> "Ciclismo"
    0x04 -> "Senderismo"
    0x05 -> "Natación"
    0x06 -> "Escalada"
    0x07 -> "Bádminton"
    0x08 -> "Otro"
    0x09 -> "Fitness"
    0x0A -> "Spinning"
    0x0B -> "Elíptica"
    0x0C -> "Trotadora"
    0x0D -> "Abdominales"
    0x0E -> "Flexiones"
    0x0F -> "Mancuernas"
    0x10 -> "Pesas"
    0x11, 0x22, 0x27 -> "Aeróbicos"
    0x12 -> "Yoga"
    0x13 -> "Saltar la cuerda"
    0x14 -> "Ping-pong"
    0x15 -> "Básquetbol"
    0x16 -> "Fútbol"
    0x17 -> "Vóleibol"
    0x18 -> "Tenis"
    0x19 -> "Golf"
    0x1A -> "Béisbol"
    0x1B -> "Patinaje sobre hielo"
    0x1C -> "Patinaje"
    0x1D -> "Baile"
    0x1E -> "Pádel"
    0x20 -> "Pilates"
    0x21 -> "Entrenamiento cruzado"
    0x23 -> "Zumba"
    0x25 -> "Plancha"
    0x26 -> "Gimnasio"
    0x28 -> "Abdomen"
    0x29 -> "Tren inferior"
    else -> "Ejercicio #$tipo"
}
