package com.coopercpu.reloj.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Fondo = Color(0xFF0E0F13)
val Tarjeta = Color(0xFF181A20)
val Borde = Color(0xFF262932)
val Texto = Color(0xFFF2F3F5)
val TextoSuave = Color(0xFF9AA0AB)
val Rojo = Color(0xFFFF4D5E)      // como el botón rojo del reloj
val Verde = Color(0xFF3DDC97)
val Ambar = Color(0xFFFFB547)

private val colores = darkColorScheme(
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

@Composable
fun RelojTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, content = content)
}
