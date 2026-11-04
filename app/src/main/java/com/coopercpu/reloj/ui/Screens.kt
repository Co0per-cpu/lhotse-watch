package com.coopercpu.reloj.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coopercpu.reloj.Conn
import com.coopercpu.reloj.HeartDay
import com.coopercpu.reloj.WatchManager
import com.coopercpu.reloj.Workout
import com.idosmart.model.IDOBleDeviceModel
import kotlinx.coroutines.delay

@Composable
fun AppRoot(
    permsOk: Boolean,
    onAskPerms: () -> Unit,
    notifAccess: Boolean,
    onOpenNotifAccess: () -> Unit
) {
    val snack = remember { SnackbarHostState() }
    val message by WatchManager.message.collectAsState()
    val bound by WatchManager.bound.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            WatchManager.clearMessage()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Fondo,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { if (permsOk && bound) BottomBar(tab) { tab = it } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !permsOk -> PermisosScreen(onAskPerms)
                !bound -> SetupScreen()
                tab == 0 -> AhoraScreen()
                tab == 1 -> PulsoScreen()
                tab == 2 -> EjerciciosScreen()
                else -> AjustesScreen(notifAccess, onOpenNotifAccess)
            }
        }
    }
    CodeDialog()
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        "Ahora" to Icons.Filled.Home,
        "Pulso" to Icons.Filled.Favorite,
        "Ejercicios" to Icons.Filled.DirectionsRun,
        "Ajustes" to Icons.Filled.Watch
    )
    NavigationBar(containerColor = Tarjeta) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = tab == i,
                onClick = { onTab(i) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Rojo,
                    selectedTextColor = Rojo,
                    indicatorColor = Borde,
                    unselectedIconColor = TextoSuave,
                    unselectedTextColor = TextoSuave
                )
            )
        }
    }
}

// ---------------- Piezas comunes ----------------

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Tarjeta, RoundedCornerShape(20.dp))
            .border(1.dp, Borde, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
internal fun Titulo(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(text, color = Texto, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = TextoSuave, fontSize = 14.sp)
    }
}

@Composable
internal fun EstadoChip(conn: Conn) {
    val (txt, color) = when (conn) {
        Conn.CONECTADO -> "Conectado" to Verde
        Conn.CONECTANDO -> "Conectando…" to Ambar
        Conn.DESCONECTADO -> "Desconectado" to TextoSuave
    }
    Row(
        Modifier
            .background(Borde, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(txt, color = Texto, fontSize = 13.sp)
    }
}

@Composable
internal fun BotonPrincipal(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Rojo, contentColor = Color.White)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun BotonSecundario(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(text, color = Texto) }
}

// ---------------- Permisos ----------------

@Composable
private fun PermisosScreen(onAsk: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Titulo("Mi Reloj")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Bluetooth, null, tint = Rojo, modifier = Modifier.size(36.dp))
                Text("Necesito permiso de Bluetooth", color = Texto, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Lo uso solo para buscar y hablar con tu reloj. Tus datos se quedan en este teléfono.",
                    color = TextoSuave
                )
                BotonPrincipal("Dar permiso", onClick = onAsk)
            }
        }
    }
}

// ---------------- Primera vez: buscar y vincular ----------------

@Composable
private fun SetupScreen() {
    val scanning by WatchManager.scanning.collectAsState()
    val list by WatchManager.scan.collectAsState()
    val conn by WatchManager.conn.collectAsState()
    val device by WatchManager.device.collectAsState()
    val binding by WatchManager.binding.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Titulo("Conecta tu reloj", "Solo hay que hacerlo una vez") }

        if (device != null && conn != Conn.DESCONECTADO) {
            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(device?.name ?: "Reloj", color = Texto, fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            EstadoChip(conn)
                        }
                        if (conn == Conn.CONECTADO) {
                            Text(
                                "Toca Vincular y luego acepta en la pantalla del reloj (o escribe el código que muestre).",
                                color = TextoSuave
                            )
                            if (binding) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = Rojo, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Esperando al reloj…", color = Texto)
                                }
                            } else {
                                BotonPrincipal("Vincular") { WatchManager.bind() }
                            }
                        } else {
                            Text("Conectando… deja el reloj cerca del teléfono.", color = TextoSuave)
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Antes de empezar", color = Texto, fontWeight = FontWeight.SemiBold)
                    Text("1. En VeryFit, entra a tu reloj y toca \"Elimine el dispositivo\".", color = TextoSuave)
                    Text("2. Cierra VeryFit para que no se vuelva a conectar.", color = TextoSuave)
                    Text("3. Si esta app no logra vincularse, restablece el reloj de fábrica desde su menú de ajustes.", color = TextoSuave)
                }
            }
        }

        item {
            BotonPrincipal(if (scanning) "Buscando…" else "Buscar reloj", enabled = !scanning) {
                WatchManager.startScan()
            }
        }

        if (scanning && list.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rojo, trackColor = Borde) }
        }

        items(list, key = { it.macAddress ?: it.hashCode().toString() }) { d ->
            DeviceRow(d) { WatchManager.connect(d) }
        }
    }
}

@Composable
private fun DeviceRow(d: IDOBleDeviceModel, onClick: () -> Unit) {
    val name = d.name?.takeIf { it.isNotBlank() } ?: "Sin nombre"
    val looksLikeMine = name.contains("217", ignoreCase = true) || name.contains("lhotse", ignoreCase = true)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (looksLikeMine) Borde else Tarjeta, RoundedCornerShape(16.dp))
            .border(1.dp, if (looksLikeMine) Rojo else Borde, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Watch, null, tint = if (looksLikeMine) Rojo else TextoSuave)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Texto, fontWeight = FontWeight.SemiBold)
            Text(d.macAddress ?: "", color = TextoSuave, fontSize = 12.sp)
        }
        Text("${d.rssi ?: "-"} dBm", color = TextoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun CodeDialog() {
    val need by WatchManager.needCode.collectAsState()
    if (!need) return
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { WatchManager.cancelCode() },
        title = { Text("Código del reloj") },
        text = {
            Column {
                Text("Escribe el código que aparece en la pantalla del reloj.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = code, onValueChange = { code = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { WatchManager.sendCode(code) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = { WatchManager.cancelCode() }) { Text("Cancelar") } }
    )
}

// ---------------- Ahora ----------------

@Composable
private fun AhoraScreen() {
    val conn by WatchManager.conn.collectAsState()
    val live by WatchManager.live.collectAsState()
    val measuring by WatchManager.measuring.collectAsState()
    val measured by WatchManager.measuredHr.collectAsState()
    val syncing by WatchManager.syncing.collectAsState()
    val progress by WatchManager.syncProgress.collectAsState()
    val lastSync by WatchManager.lastSync.collectAsState()

    // Mientras esta pantalla está abierta, pide los datos del día cada 15 s
    LaunchedEffect(conn) {
        while (conn == Conn.CONECTADO) {
            WatchManager.refreshLive()
            delay(15_000)
        }
    }

    val hr = if (measured > 0) measured else live.heartRate

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Titulo("Hoy") }
                EstadoChip(conn)
            }
        }
        if (conn == Conn.DESCONECTADO) {
            item { BotonSecundario("Reconectar reloj") { WatchManager.reconnect() } }
        }

        item {
            Card {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Corazon(latiendo = measuring)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (hr > 0) "$hr" else "--", color = Texto, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("ppm", color = TextoSuave, fontSize = 18.sp, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Text(
                        when {
                            measuring -> "Midiendo… no muevas la muñeca"
                            measured > 0 -> "Última medición"
                            live.heartRate > 0 -> "Pulso actual del reloj"
                            else -> "Toca medir para ver tu pulso"
                        },
                        color = TextoSuave
                    )
                    Spacer(Modifier.height(14.dp))
                    if (measuring) BotonSecundario("Detener") { WatchManager.stopMeasure() }
                    else BotonPrincipal("Medir pulso", enabled = conn == Conn.CONECTADO && !syncing) { WatchManager.measureHeart() }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Pasos", "${live.steps}", Modifier.weight(1f))
                Stat("Calorías", "${live.calories} kcal", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Distancia", distancia(live.distanceM), Modifier.weight(1f))
                Stat("Activo", "${live.activeMin} min", Modifier.weight(1f))
            }
        }

        item {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sync, null, tint = Rojo)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Historial", color = Texto, fontWeight = FontWeight.SemiBold)
                            Text("Última vez: ${haceCuanto(lastSync)}", color = TextoSuave, fontSize = 13.sp)
                        }
                    }
                    if (syncing) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Rojo,
                            trackColor = Borde
                        )
                        Text("Sincronizando… $progress%", color = TextoSuave, fontSize = 13.sp)
                    } else {
                        BotonPrincipal("Sincronizar ahora", enabled = conn == Conn.CONECTADO && !measuring) {
                            WatchManager.sync()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Corazon(latiendo: Boolean) {
    val t = rememberInfiniteTransition(label = "latido")
    val s by t.animateFloat(
        initialValue = 1f,
        targetValue = if (latiendo) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "escala"
    )
    Icon(Icons.Filled.Favorite, null, tint = Rojo, modifier = Modifier.size(44.dp).scale(s))
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Tarjeta, RoundedCornerShape(18.dp))
            .border(1.dp, Borde, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(label, color = TextoSuave, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Pulso ----------------

@Composable
private fun PulsoScreen() {
    val days by WatchManager.heart.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Titulo("Pulso", "Por día, desde tu reloj") }
        if (days.isEmpty()) item { Vacio("Todavía no hay datos. Ve a Ahora y toca Sincronizar.") }
        items(days, key = { it.key }) { DiaCard(it) }
    }
}

@Composable
private fun DiaCard(d: HeartDay) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(fecha(d.year, d.month, d.day), color = Texto, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Mini("Reposo", d.resting)
                Mini("Mín", d.min)
                Mini("Prom", d.avg)
                Mini("Máx", d.max)
            }
            if (d.samples.size >= 2) Grafico(d.samples)
        }
    }
}

@Composable
private fun Mini(label: String, v: Int) {
    Column {
        Text(label, color = TextoSuave, fontSize = 12.sp)
        Text(if (v > 0) "$v" else "--", color = Texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Grafico(values: List<Int>) {
    val min = (values.minOrNull() ?: 0) - 5
    val max = (values.maxOrNull() ?: 0) + 5
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val span = (max - min).coerceAtLeast(1).toFloat()
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / span) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Borde, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        drawPath(path, Rojo, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

// ---------------- Ejercicios ----------------

@Composable
private fun EjerciciosScreen() {
    val list by WatchManager.workouts.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Titulo("Ejercicios", "Los que registraste en el reloj") }
        if (list.isEmpty()) item { Vacio("Todavía no hay ejercicios. Registra uno en el reloj y luego sincroniza.") }
        items(list, key = { it.key }) { EjercicioCard(it) }
    }
}

@Composable
private fun EjercicioCard(w: Workout) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsRun, null, tint = Rojo)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(deporte(w.type), color = Texto, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("${fecha(w.year, w.month, w.day)} · %02d:%02d".format(w.hour, w.minute), color = TextoSuave, fontSize = 13.sp)
                }
                Text(duracion(w.durationSec), color = Texto, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (w.distanceM > 0) Dato("Distancia", distancia(w.distanceM))
                Dato("Calorías", "${w.calories}")
                if (w.steps > 0) Dato("Pasos", "${w.steps}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Dato("Pulso prom", if (w.avgHr > 0) "${w.avgHr}" else "--")
                Dato("Máx", if (w.maxHr > 0) "${w.maxHr}" else "--")
                Dato("Mín", if (w.minHr > 0) "${w.minHr}" else "--")
            }
        }
    }
}

@Composable
private fun Dato(label: String, value: String) {
    Column {
        Text(label, color = TextoSuave, fontSize = 12.sp)
        Text(value, color = Texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Vacio(text: String) {
    Card { Text(text, color = TextoSuave) }
}
