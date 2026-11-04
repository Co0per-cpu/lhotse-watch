package com.coopercpu.reloj.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Notifications
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
