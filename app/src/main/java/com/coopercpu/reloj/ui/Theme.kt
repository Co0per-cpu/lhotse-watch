package com.coopercpu.reloj.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

val Fondo = Color(0xFF0E0F13)
val Tarjeta = Color(0xFF181A20)
val Borde = Color(0xFF262932)
val Texto = Color(0xFFF2F3F5)
val TextoSuave = Color(0xFF9AA0AB)
val Verde = Color(0xFF3DDC97)
val Ambar = Color(0xFFFFB547)

/** Colores de acento que se pueden elegir en Ajustes → Apariencia. */
val ACENTOS = listOf(
    "Rojo" to 0xFFFF4D5E,
    "Naranjo" to 0xFFFF8A3D,
    "Amarillo" to 0xFFFFC83D,
    "Verde" to 0xFF3DDC97,
    "Celeste" to 0xFF4DB8FF,
    "Azul" to 0xFF5B7CFF,
    "Morado" to 0xFFA36BFF,
    "Rosado" to 0xFFFF6BB5,
)

/** Color de acento actual. Cambiarlo repinta toda la app al instante. */
val acentoActual = mutableStateOf(Color(0xFFFF4D5E))

/** Se llama "Rojo" por historia, pero es el acento que elijas. */
val Rojo: Color get() = acentoActual.value

@Composable
fun RelojTheme(content: @Composable () -> Unit) {
    val colores = darkColorScheme(
        primary = Rojo,
        onPrimary = Color.White,
        secondary = Verde,
        background = Fondo,
        onBackground = Texto,
        surface = Tarjeta,
        onSurface = Texto,
        surfaceVariant = Borde,
        onSurfaceVariant = TextoSuave,
        surfaceContainer = Tarjeta,
    )
    MaterialTheme(colorScheme = colores, content = content)
}
