package com.coopercpu.reloj.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coopercpu.reloj.Alarm
import com.coopercpu.reloj.ConexionService
import com.coopercpu.reloj.Conn
import com.coopercpu.reloj.NOTIF_APPS
import com.coopercpu.reloj.Place
import com.coopercpu.reloj.WatchManager
import com.coopercpu.reloj.WeatherClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AjustesScreen(notifAccess: Boolean, onOpenNotifAccess: () -> Unit) {
    val conn by WatchManager.conn.collectAsState()
    val device by WatchManager.device.collectAsState()
    var confirmUnbind by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf("") }
    fun toggle(k: String) { open = if (open == k) "" else k }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Titulo("Ajustes", "Personaliza tu reloj") }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Watch, null, tint = Rojo, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device?.name ?: "Reloj", color = Texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(device?.macAddress ?: "", color = TextoSuave, fontSize = 12.sp)
                    }
                    EstadoChip(conn)
                }
            }
        }
        if (conn == Conn.DESCONECTADO) item { BotonSecundario("Reconectar") { WatchManager.reconnect() } }

        item {
            Seccion("Notificaciones", Icons.Filled.Notifications, open == "notif", { toggle("notif") }) {
                NotificacionesPanel(notifAccess, onOpenNotifAccess)
            }
        }
        item {
            Seccion("Pantallas del reloj", Icons.Filled.ViewCarousel, open == "menu", { toggle("menu") }) { MenuPanel() }
        }
        item {
            Seccion("Apariencia de la app", Icons.Filled.Palette, open == "apariencia", { toggle("apariencia") }) { AparienciaPanel() }
        }
        item {
            Seccion("Actualizaciones del reloj", Icons.Filled.SystemUpdate, open == "update", { toggle("update") }) {
                ActualizacionesPanel()
            }
        }
        item {
            Seccion("Música y buscar teléfono", Icons.Filled.MusicNote, open == "musica", { toggle("musica") }) {
                MusicaPanel(notifAccess, onOpenNotifAccess)
            }
        }
        item {
            Seccion("Clima", Icons.Filled.Cloud, open == "clima", { toggle("clima") }) { ClimaPanel() }
        }
        item {
            Seccion("Pantalla", Icons.Filled.Brightness6, open == "pantalla", { toggle("pantalla") }) { PantallaPanel() }
        }
        item {
            Seccion("Pulso automático", Icons.Filled.Favorite, open == "pulso", { toggle("pulso") }) { PulsoPanel() }
        }
        item {
            Seccion("Meta de pasos", Icons.Filled.DirectionsWalk, open == "meta", { toggle("meta") }) { MetaPanel() }
        }
        item {
            Seccion("Alarmas", Icons.Filled.Alarm, open == "alarmas", { toggle("alarmas") }) { AlarmasPanel() }
        }

        item {
            Seccion("Diagnóstico", Icons.Filled.BugReport, open == "diag", { toggle("diag") }) {
                DiagnosticoPanel(notifAccess)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { BotonSecundario("Desvincular reloj") { confirmUnbind = true } }
        item {
            Text(
                "Tus datos se guardan solo en este teléfono. El clima viene de Open-Meteo (solo se envían las coordenadas de tu ciudad).",
                color = TextoSuave, fontSize = 12.sp
            )
        }
    }

    if (confirmUnbind) {
        AlertDialog(
            onDismissRequest = { confirmUnbind = false },
            title = { Text("¿Desvincular?") },
            text = { Text("El reloj dejará de estar conectado a esta app. Tus datos guardados no se borran.") },
            confirmButton = { TextButton(onClick = { confirmUnbind = false; WatchManager.unbind() }) { Text("Desvincular") } },
            dismissButton = { TextButton(onClick = { confirmUnbind = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Seccion(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Tarjeta, RoundedCornerShape(20.dp))
            .border(1.dp, Borde, RoundedCornerShape(20.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Rojo)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextoSuave)
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { content() }
        }
    }
}

@Composable
private fun Interruptor(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Texto)
            if (sub != null) Text(sub, color = TextoSuave, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Rojo, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun Opciones(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier
                    .background(if (sel) Rojo else Borde, RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text(o, color = if (sel) Color.White else Texto, fontSize = 14.sp) }
        }
    }
}

// ---------------- Notificaciones ----------------

@Composable
private fun NotificacionesPanel(access: Boolean, onOpen: () -> Unit) {
    val s = WatchManager.settings
    var enabled by remember { mutableStateOf(s.notifLabels) }
    var calls by remember { mutableStateOf(s.calls) }

    if (!access) {
        Text(
            "Para mandar tus notificaciones al reloj, Android necesita que le des acceso a Mi Reloj.",
            color = TextoSuave
        )
        BotonPrincipal("Dar acceso a notificaciones", onClick = onOpen)
    } else {
        Text("Acceso concedido. Elige qué apps llegan al reloj:", color = TextoSuave)
    }

    Interruptor("Llamadas entrantes", checked = calls) {
        calls = it; s.calls = it
    }
    NOTIF_APPS.forEach { app ->
        Interruptor(app.label, checked = app.label in enabled) { on ->
            enabled = if (on) enabled + app.label else enabled - app.label
            s.notifLabels = enabled
        }
    }
    BotonSecundario("Enviar notificación de prueba", enabled = access) { WatchManager.testNotice() }
    BotonSecundario("Volver a encender avisos en el reloj") { WatchManager.enableNoticesOnWatch() }
}

// ---------------- Diagnóstico ----------------

@Composable
private fun DiagnosticoPanel(notifAccess: Boolean) {
    val ctx = LocalContext.current
    val lines by WatchManager.log.collectAsState()
    Text(
        "Prueba la música, el clima y el menú, y anota exactamente qué responde el reloj. " +
            "Ten Spotify sonando antes de empezar.",
        color = TextoSuave
    )
    BotonPrincipal("Probar todo") { WatchManager.runDiagnostics(notifAccess) }
    BotonSecundario("Copiar resultado", enabled = lines.isNotEmpty()) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Mi Reloj", lines.joinToString("\n")))
        android.widget.Toast.makeText(ctx, "Copiado. Pégalo en el chat.", android.widget.Toast.LENGTH_SHORT).show()
    }
    if (lines.isNotEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(320.dp)
                .background(Fondo, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    lines.takeLast(120).joinToString("\n"),
                    color = TextoSuave,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                )
            }
        }
    }
}

// ---------------- Actualizaciones ----------------

@Composable
private fun ActualizacionesPanel() {
    val info by WatchManager.info.collectAsState()
    val gps by WatchManager.gpsState.collectAsState()
    val busy by WatchManager.gpsBusy.collectAsState()
    val conn by WatchManager.conn.collectAsState()

    androidx.compose.runtime.LaunchedEffect(conn) { WatchManager.refreshInfo() }

    Text("Firmware: ${info.firmware.ifBlank { "—" }}", color = Texto)
    Text("Batería: ${if (info.battery >= 0) "${info.battery}%" else "—"}", color = Texto)
    Text("GPS (AGPS) actualizado: ${haceCuanto(info.gpsUpdated)}", color = Texto)

    Text(
        "Los datos AGPS le dicen al reloj dónde están los satélites, así el GPS encuentra tu posición en segundos " +
            "en vez de minutos. Se actualizan solos cada pocos días; aquí puedes forzarlo.",
        color = TextoSuave, fontSize = 13.sp
    )
    if (busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Rojo, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(gps, color = Texto)
        }
    } else {
        if (gps.isNotBlank()) Text(gps, color = TextoSuave, fontSize = 13.sp)
        BotonPrincipal("Actualizar GPS del reloj", enabled = conn == Conn.CONECTADO) { WatchManager.updateGps() }
    }

    Text(
        "Firmware: el sistema interno del reloj solo lo puede actualizar un archivo oficial del fabricante, " +
            "y Lhotse ya no publica versiones nuevas para este modelo.",
        color = TextoSuave, fontSize = 13.sp
    )
}

// ---------------- Música ----------------

@Composable
private fun MusicaPanel(access: Boolean, onOpen: () -> Unit) {
    Text(
        "Cuando suena Spotify (u otra app de música), el reloj muestra la canción y el artista, " +
            "y desde la pantalla de Música del reloj puedes pausar, cambiar de canción y subir o bajar el volumen.",
        color = TextoSuave
    )
    Text(
        "Si usas \"Buscar teléfono\" en el reloj, el teléfono suena y vibra fuerte por 30 segundos.",
        color = TextoSuave
    )
    if (!access) {
        Text("Para esto también se necesita el acceso a notificaciones.", color = Ambar, fontSize = 13.sp)
        BotonPrincipal("Dar acceso", onClick = onOpen)
    } else {
        BotonSecundario("Enviar la canción actual al reloj") {
            WatchManager.enableMusicOnWatch()
        }
    }
    Text(
        "Si en el reloj no ves la pantalla de Música, agrégala en \"Pantallas del reloj\".",
        color = TextoSuave, fontSize = 13.sp
    )
    val debug by WatchManager.musicDebug.collectAsState()
    Text("Diagnóstico: $debug", color = TextoSuave, fontSize = 12.sp)
}

// ---------------- Pantallas del reloj ----------------

/** Pantallas que se pueden mostrar en el reloj (códigos oficiales de IDO). */
private val PANTALLAS = linkedMapOf(
    1 to "Pasos", 2 to "Pulso", 3 to "Sueño", 4 to "Cámara", 5 to "Alarma", 6 to "Música",
    7 to "Cronómetro", 8 to "Temporizador", 9 to "Deportes", 10 to "Clima", 11 to "Respiración",
    12 to "Buscar teléfono", 13 to "Estrés", 14 to "Anillos de actividad", 15 to "Hora",
    16 to "Última actividad", 17 to "Datos de salud", 18 to "Oxígeno en sangre", 19 to "Ajustes",
    22 to "Calorías", 23 to "Distancia", 24 to "Medición rápida", 29 to "Recordatorios",
    36 to "Hora mundial", 37 to "Asistente de voz", 41 to "Ciclo menstrual",
)

private fun nombrePantalla(id: Int) = PANTALLAS[id] ?: "Pantalla #$id"

@Composable
private fun MenuPanel() {
    val menu by WatchManager.menu.collectAsState()
    val conn by WatchManager.conn.collectAsState()
    var items by remember(menu) { mutableStateOf(menu?.items ?: emptyList()) }
    var adding by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(conn) { if (conn == Conn.CONECTADO && menu == null) WatchManager.loadMenu() }

    Text(
        "Elige qué pantallas aparecen al deslizar en el reloj y en qué orden. " +
            "Aquí puedes agregar Música o Clima si tu reloj no las muestra.",
        color = TextoSuave
    )
    if (menu == null) {
        BotonSecundario("Leer pantallas del reloj", enabled = conn == Conn.CONECTADO) { WatchManager.loadMenu() }
        return
    }
    menu?.let { m ->
        if (m.max > 0) Text("Tu reloj acepta ${if (m.min > 0) "entre ${m.min} y " else "hasta "}${m.max} pantallas.", color = TextoSuave, fontSize = 12.sp)
    }

    items.forEachIndexed { i, id ->
        Row(
            Modifier.fillMaxWidth().background(Borde, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${i + 1}. ${nombrePantalla(id)}", color = Texto, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (i > 0) items = items.toMutableList().also { l -> l.add(i - 1, l.removeAt(i)) }
            }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, "Subir", tint = TextoSuave) }
            IconButton(onClick = {
                if (i < items.lastIndex) items = items.toMutableList().also { l -> l.add(i + 1, l.removeAt(i)) }
            }, enabled = i < items.lastIndex) { Icon(Icons.Filled.ArrowDownward, "Bajar", tint = TextoSuave) }
            IconButton(onClick = { items = items - id }) { Icon(Icons.Filled.Close, "Quitar", tint = TextoSuave) }
        }
    }

    if (adding) {
        Text("Toca una pantalla para agregarla:", color = Texto)
        PANTALLAS.filterKeys { it !in items }.forEach { (id, name) ->
            Row(
                Modifier.fillMaxWidth().background(Tarjeta, RoundedCornerShape(12.dp))
                    .border(1.dp, Borde, RoundedCornerShape(12.dp))
                    .clickable { items = items + id; adding = false }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = Rojo)
                Spacer(Modifier.width(8.dp))
                Text(name, color = Texto)
                if (id == 6 || id == 10) {
                    Spacer(Modifier.width(8.dp))
                    Text("recomendada", color = Rojo, fontSize = 12.sp)
                }
            }
        }
        BotonSecundario("Cancelar") { adding = false }
    } else {
        BotonSecundario("Agregar pantalla") { adding = true }
    }
    BotonPrincipal("Guardar en el reloj") { WatchManager.saveMenu(items) }
    Text(
        "Si el reloj rechaza una pantalla es porque tu modelo no la tiene. Quítala y vuelve a guardar.",
        color = TextoSuave, fontSize = 12.sp
    )
}

// ---------------- Apariencia ----------------

@Composable
private fun AparienciaPanel() {
    val s = WatchManager.settings
    Text("Color de la app", color = Texto)
    ACENTOS.chunked(4).forEach { fila ->
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        fila.forEach { (_, argb) ->
            val c = Color(argb.toInt())
            val sel = acentoActual.value == c
            Box(
                Modifier
                    .size(44.dp)
                    .background(c, CircleShape)
                    .border(if (sel) 3.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                    .clickable {
                        acentoActual.value = c
                        s.accent = argb
                    }
            )
        }
    }
    }
    Text("El color se aplica al instante en toda la app.", color = TextoSuave, fontSize = 12.sp)
}

// ---------------- Clima ----------------

@Composable
private fun ClimaPanel() {
    val s = WatchManager.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by WatchManager.weatherInfo.collectAsState()
    var city by remember { mutableStateOf(s.cityName) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(s.weatherAuto) }

    Text(if (city.isBlank()) "Todavía no eliges ciudad." else "Ciudad: $city", color = Texto)
    if (info.isNotBlank()) Text("Último envío: $info", color = TextoSuave, fontSize = 13.sp)

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Buscar ciudad") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    BotonSecundario(if (busy) "Buscando…" else "Buscar", enabled = !busy && query.isNotBlank()) {
        busy = true
        scope.launch {
            results = try {
                withContext(Dispatchers.IO) { WeatherClient.searchCity(query) }
            } catch (e: Exception) {
                status = "No pude buscar. Revisa tu conexión a internet."
                emptyList()
            }
            busy = false
            if (results.isEmpty() && status.isBlank()) status = "No encontré esa ciudad."
        }
    }
    results.forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(Borde, RoundedCornerShape(12.dp))
                .clickable {
                    s.cityName = p.name; s.lat = p.lat; s.lon = p.lon; s.lastWeather = 0L
                    city = p.name; results = emptyList(); query = ""
                    status = "Enviando clima…"
                    ConexionService.updateWeather(ctx, force = true) { msg -> status = msg }
                }
                .padding(12.dp)
        ) {
            Column {
                Text(p.name, color = Texto, fontWeight = FontWeight.SemiBold)
                Text(p.detail, color = TextoSuave, fontSize = 12.sp)
            }
        }
    }

    Interruptor("Actualizar solo cada hora", checked = auto) { auto = it; s.weatherAuto = it }
    BotonPrincipal("Enviar clima ahora", enabled = city.isNotBlank()) {
        status = "Enviando clima…"
        ConexionService.updateWeather(ctx, force = true) { msg -> status = msg }
    }
    if (status.isNotBlank()) Text(status, color = TextoSuave, fontSize = 13.sp)
}

// ---------------- Pantalla ----------------

@Composable
private fun PantallaPanel() {
    val s = WatchManager.settings
    var level by remember { mutableFloatStateOf(s.brightness.toFloat()) }
    var raise by remember { mutableStateOf(s.raiseToWake) }
    var secs by remember { mutableFloatStateOf(s.screenSeconds.toFloat()) }

    Text("Brillo: ${level.toInt()}%", color = Texto)
    Slider(
        value = level, onValueChange = { level = it }, valueRange = 10f..100f, steps = 8,
        colors = SliderDefaults.colors(thumbColor = Rojo, activeTrackColor = Rojo)
    )
    BotonPrincipal("Aplicar brillo") {
        s.brightness = level.toInt()
        WatchManager.applyBrightness(level.toInt())
    }

    Spacer(Modifier.height(4.dp))
    Interruptor("Levantar la muñeca para ver la hora", checked = raise) { raise = it }
    Text("Pantalla encendida: ${secs.toInt()} s", color = Texto)
    Slider(
        value = secs, onValueChange = { secs = it }, valueRange = 3f..15f, steps = 11,
        colors = SliderDefaults.colors(thumbColor = Rojo, activeTrackColor = Rojo)
    )
    BotonPrincipal("Aplicar") {
        s.raiseToWake = raise; s.screenSeconds = secs.toInt()
        WatchManager.applyRaiseToWake(raise, secs.toInt())
    }
}

// ---------------- Pulso automático ----------------

@Composable
private fun PulsoPanel() {
    val s = WatchManager.settings
    var mode by remember { mutableIntStateOf(s.hrMode) }
    val intervals = listOf(10, 20, 30)
    var iv by remember { mutableIntStateOf(intervals.indexOf(s.hrInterval).coerceAtLeast(0)) }

    Text("¿Cada cuánto mide el pulso el reloj por su cuenta?", color = TextoSuave)
    Opciones(listOf("Apagado", "Automático", "Continuo"), mode) { mode = it }
    if (mode == 1) {
        Text("Intervalo", color = Texto)
        Opciones(intervals.map { "$it min" }, iv) { iv = it }
    }
    if (mode == 2) Text("Continuo da más detalle pero gasta más batería.", color = Ambar, fontSize = 13.sp)
    BotonPrincipal("Aplicar") {
        s.hrMode = mode; s.hrInterval = intervals[iv]
        // En el protocolo de IDO: 0 apagado, 1 automático, 2 continuo
        WatchManager.applyHeartMode(mode, intervals[iv])
    }
}

// ---------------- Meta de pasos ----------------

@Composable
private fun MetaPanel() {
    val s = WatchManager.settings
    var goal by remember { mutableFloatStateOf(s.stepGoal.toFloat()) }
    Text("${(goal / 500).toInt() * 500} pasos al día", color = Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Slider(
        value = goal, onValueChange = { goal = it }, valueRange = 2000f..20000f,
        colors = SliderDefaults.colors(thumbColor = Rojo, activeTrackColor = Rojo)
    )
    BotonPrincipal("Aplicar meta") {
        val g = (goal / 500).toInt() * 500
        s.stepGoal = g
        WatchManager.applyStepGoal(g)
    }
}

// ---------------- Alarmas ----------------

private val DIAS = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
private fun AlarmasPanel() {
    val s = WatchManager.settings
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(s.alarms) }
    fun save(l: List<Alarm>) { list = l; s.alarms = l }

    if (list.isEmpty()) Text("No tienes alarmas.", color = TextoSuave)
    list.forEach { a ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(Borde, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%02d:%02d".format(a.hour, a.minute),
                    color = Texto, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).clickable {
                        TimePickerDialog(ctx, { _, h, m ->
                            save(list.map { if (it.id == a.id) it.copy(hour = h, minute = m) else it })
                        }, a.hour, a.minute, true).show()
                    }
                )
                Switch(
                    checked = a.on,
                    onCheckedChange = { on -> save(list.map { if (it.id == a.id) it.copy(on = on) else it }) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Rojo, checkedThumbColor = Color.White)
                )
                IconButton(onClick = { save(list.filter { it.id != a.id }) }) {
                    Icon(Icons.Filled.Delete, "Borrar", tint = TextoSuave)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DIAS.forEachIndexed { i, d ->
                    val day = i + 1
                    val sel = day in a.days
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(if (sel) Rojo else Tarjeta, CircleShape)
                            .clickable {
                                save(list.map {
                                    if (it.id == a.id) it.copy(days = if (sel) it.days - day else it.days + day) else it
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) { Text(d, color = if (sel) Color.White else TextoSuave, fontSize = 13.sp) }
                }
            }
        }
    }
    BotonSecundario("Agregar alarma") {
        TimePickerDialog(ctx, { _, h, m ->
            val id = (list.maxOfOrNull { it.id } ?: 0) + 1
            save(list + Alarm(id, h, m, true, setOf(1, 2, 3, 4, 5)))
        }, 7, 0, true).show()
    }
    BotonPrincipal("Enviar alarmas al reloj") { WatchManager.applyAlarms(list) }
}
