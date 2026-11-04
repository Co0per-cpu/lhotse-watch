package com.coopercpu.reloj

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.idosmart.enums.IDOBindStatus
import com.idosmart.enums.IDOConnectErrorType
import com.idosmart.enums.IDODeviceStateType
import com.idosmart.enums.IDOLogType
import com.idosmart.enums.IDOStatusNotification
import com.idosmart.enums.IDOSyncDataType
import com.idosmart.model.*
import com.idosmart.model.IDOBleDeviceModel
import com.idosmart.model.IDOBluetoothStateModel
import com.idosmart.model.IDODeviceNotificationModel
import com.idosmart.model.IDODeviceStateModel
import com.idosmart.model.IDOOtaDeviceModel
import com.idosmart.model.IDOReceiveData
import com.idosmart.model.IDOSppStateModel
import com.idosmart.pigeon_implement.Cmds
import com.idosmart.pigeon_implement.IDOEpoManager
import com.idosmart.pigeon_implement.IDOEpoManagerDelegate
import com.idosmart.pigeon_implement.IDOEpoUpgradeStatus
import com.idosmart.pigeon_implement.IDOOtaGpsInfo
import com.idosmart.pigeon_implement.IDOMeasureManager
import com.idosmart.pigeon_implement.IDOMeasureStatus
import com.idosmart.pigeon_implement.IDOMeasureType
import com.idosmart.protocol_channel.sdk
import com.idosmart.protocol_sdk.IDOBleDelegate
import com.idosmart.protocol_sdk.IDOBridgeDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Conn { DESCONECTADO, CONECTANDO, CONECTADO }

data class Live(
    val steps: Int = 0,
    val calories: Int = 0,
    val distanceM: Int = 0,
    val activeMin: Int = 0,
    val heartRate: Int = 0
)

/**
 * Toda la comunicación con el reloj pasa por aquí, usando el kit oficial de IDO.
 */
object WatchManager {
    private const val TAG = "Reloj"
    private lateinit var store: Store
    lateinit var settings: Settings
        private set
    private val main = Handler(Looper.getMainLooper())
    private var userDisconnect = false
    private lateinit var appCtx: Context

    private val _conn = MutableStateFlow(Conn.DESCONECTADO)
    val conn: StateFlow<Conn> = _conn.asStateFlow()

    private val _device = MutableStateFlow<IDOBleDeviceModel?>(null)
    val device: StateFlow<IDOBleDeviceModel?> = _device.asStateFlow()

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()

    private val _scan = MutableStateFlow<List<IDOBleDeviceModel>>(emptyList())
    val scan: StateFlow<List<IDOBleDeviceModel>> = _scan.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _binding = MutableStateFlow(false)
    val binding: StateFlow<Boolean> = _binding.asStateFlow()

    /** El reloj pidió un código que aparece en su pantalla. */
    private val _needCode = MutableStateFlow(false)
    val needCode: StateFlow<Boolean> = _needCode.asStateFlow()

    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    private val _measuredHr = MutableStateFlow(0)
    val measuredHr: StateFlow<Int> = _measuredHr.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress.asStateFlow()

    private val _heart = MutableStateFlow<List<HeartDay>>(emptyList())
    val heart: StateFlow<List<HeartDay>> = _heart.asStateFlow()

    private val _workouts = MutableStateFlow<List<Workout>>(emptyList())
    val workouts: StateFlow<List<Workout>> = _workouts.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    /** Mensaje para mostrar al usuario (se borra al leerlo). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    private val osVersion: Int
        get() = Build.VERSION.RELEASE.substringBefore('.').toIntOrNull() ?: 15

    private var started = false

    fun init(context: Context) {
        appCtx = context.applicationContext
        store = Store(context)
        settings = Settings(context)
        sdk.init(context)
        sdk.ble.addBleDelegate(bleDelegate)
        sdk.ble.getBluetoothState { }
        sdk.bridge.setupBridge(bridgeDelegate, IDOLogType.RELEASE)

        IDOMeasureManager.shared.listenProcessMeasureData { r ->
            when (r.status) {
                IDOMeasureStatus.MEASURING_DATA, IDOMeasureStatus.SUCCESS -> {
                    if (r.value > 0) _measuredHr.value = r.value
                    if (r.status == IDOMeasureStatus.SUCCESS) _measuring.value = false
                }
                IDOMeasureStatus.UNWORN -> { _measuring.value = false; say("Ponte el reloj en la muñeca para medir.") }
                IDOMeasureStatus.DEVICE_IN_SPORT_MODE -> { _measuring.value = false; say("El reloj está en un ejercicio. Termínalo para medir.") }
                IDOMeasureStatus.FAILED, IDOMeasureStatus.TIMEOUT, IDOMeasureStatus.NOT_SUPPORT,
                IDOMeasureStatus.DISCONNECTED, IDOMeasureStatus.STOPPED -> _measuring.value = false
                else -> {}
            }
        }

        initGps()

        _device.value = store.loadDevice()
        _bound.value = store.boundMac().isNotEmpty()
        _heart.value = store.loadHeartDays()
        _workouts.value = store.loadWorkouts()
        _lastSync.value = store.lastSync
    }

    /** Se llama al abrir la app, cuando ya hay permisos de Bluetooth. */
    fun start() {
        if (started) return
        started = true
        val d = _device.value
        if (d != null && _bound.value) {
            _conn.value = Conn.CONECTANDO
            sdk.ble.autoConnect(d)
            ConexionService.start(appCtx)
        }
    }

    // ---------- Buscar y conectar ----------

    fun startScan() {
        _scan.value = emptyList()
        _scanning.value = true
        sdk.ble.stopScan()
        sdk.ble.startScan { }
        main.postDelayed({ stopScan() }, 20_000)
    }

    fun stopScan() {
        if (_scanning.value) sdk.ble.stopScan()
        _scanning.value = false
    }

    fun connect(d: IDOBleDeviceModel) {
        stopScan()
        _device.value = d
        _conn.value = Conn.CONECTANDO
        sdk.ble.connect(d)
    }

    fun reconnect() {
        val d = _device.value ?: return
        _conn.value = Conn.CONECTANDO
        sdk.ble.autoConnect(d)
    }

    // ---------- Vincular ----------

    fun bind() {
        val d = _device.value ?: return
        if (_conn.value != Conn.CONECTADO) { say("Primero conecta el reloj."); return }
        _binding.value = true
        sdk.cmd.bind(osVersion, { }, { }) { status ->
            Log.d(TAG, "bind: $status")
            when (status) {
                IDOBindStatus.SUCCESSFUL, IDOBindStatus.BINDED -> bindOk(d)
                IDOBindStatus.NEEDCONFIRMBYAPP -> {
                    Cmds.sendBindResult(true).send { r ->
                        if (r.error.code == 0) {
                            sdk.cmd.appMarkBindResult(true)
                            bindOk(d)
                        } else {
                            sdk.cmd.appMarkBindResult(false)
                            bindFail("El reloj no confirmó la vinculación.")
                        }
                    }
                }
                IDOBindStatus.NEEDAUTH -> {
                    _binding.value = false
                    _needCode.value = true
                }
                IDOBindStatus.REFUSEDBIND -> bindFail("Rechazaste la vinculación en el reloj.")
                IDOBindStatus.TIMEOUT -> bindFail("Se acabó el tiempo. Acepta la vinculación en la pantalla del reloj.")
                else -> bindFail("No se pudo vincular (${status.name}).")
            }
        }
    }

    /** Código de vinculación que aparece en la pantalla del reloj. */
    fun sendCode(code: String) {
        val d = _device.value ?: return
        _needCode.value = false
        _binding.value = true
        sdk.cmd.setAuthCode(code.trim(), osVersion) { ok ->
            if (ok) bindOk(d) else bindFail("El código no es correcto.")
        }
    }

    fun cancelCode() { _needCode.value = false }

    private fun bindOk(d: IDOBleDeviceModel) {
        store.saveDevice(d)
        _bound.value = true
        _binding.value = false
        say("¡Reloj vinculado! Ahora puedes sincronizar tus datos.")
        main.postDelayed({ enableNoticesOnWatch() }, 2000)
        ConexionService.start(appCtx)
    }

    private fun bindFail(msg: String) {
        _binding.value = false
        say(msg)
    }

    fun unbind() {
        val d = _device.value
        val mac = d?.macAddress
        if (mac == null) { forget(); return }
        userDisconnect = true
        sdk.cmd.unbind(mac, true) { _ ->
            sdk.ble.cancelConnect(mac) { }
            forget()
            say("Reloj desvinculado de esta app.")
        }
    }

    private fun forget() {
        ConexionService.stop(appCtx)
        store.clearDevice()
        _bound.value = false
        _device.value = null
        _conn.value = Conn.DESCONECTADO
    }

    // ---------- Pulso y datos en vivo ----------

    fun measureHeart() {
        if (!canTalk()) return
        _measuring.value = true
        _measuredHr.value = 0
        IDOMeasureManager.shared.startMeasure(IDOMeasureType.HEART_RATE) { ok ->
            if (!ok) {
                _measuring.value = false
                say("El reloj no pudo empezar a medir.")
            }
        }
        // Por si el reloj no avisa que terminó
        main.postDelayed({
            if (_measuring.value) stopMeasure()
        }, 60_000)
    }

    fun stopMeasure() {
        IDOMeasureManager.shared.stopMeasure(IDOMeasureType.HEART_RATE) { }
        _measuring.value = false
    }

    fun refreshLive() {
        if (_conn.value != Conn.CONECTADO || !_bound.value || _syncing.value) return
        Cmds.getLiveData(0).send { r ->
            if (r.error.code == 0) {
                r.res?.let {
                    _live.value = Live(
                        steps = it.totalStep,
                        calories = it.totalCalories,
                        distanceM = it.totalDistances,
                        activeMin = it.totalActiveTime,
                        heartRate = it.heartRate
                    )
                }
            }
        }
    }

    // ---------- Sincronizar historial ----------

    fun sync() {
        if (!canTalk()) return
        _syncing.value = true
        _syncProgress.value = 0
        var nHeart = 0
        var nSport = 0
        sdk.syncData.startSync({ p ->
            val pct = if (p <= 1.0) (p * 100).toInt() else p.toInt()
            _syncProgress.value = pct.coerceIn(0, 100)
        }, { type, json, err ->
            Log.d(TAG, "sync $type err=$err")
            if (err == 0 && json.isNotBlank()) {
                when (type) {
                    IDOSyncDataType.HEARTRATE -> if (store.addHeartJson(json)) nHeart++
                    IDOSyncDataType.ACTIVITY, IDOSyncDataType.ACTIVITYMERGE ->
                        if (store.addWorkoutJson(json)) nSport++
                    else -> {}
                }
            }
        }, { code ->
            _syncing.value = false
            _syncProgress.value = 100
            _heart.value = store.loadHeartDays()
            _workouts.value = store.loadWorkouts()
            store.lastSync = System.currentTimeMillis()
            _lastSync.value = store.lastSync
            say(
                if (code == 0) "Listo: $nHeart día(s) de pulso y $nSport ejercicio(s) nuevos."
                else "La sincronización terminó con error ($code). Intenta de nuevo."
            )
            refreshLive()
        })
    }

    private fun canTalk(): Boolean {
        if (_conn.value != Conn.CONECTADO) { say("El reloj no está conectado."); return false }
        if (!_bound.value) { say("Primero vincula el reloj."); return false }
        if (_syncing.value) { say("Espera a que termine la sincronización."); return false }
        return true
    }

    // ---------- Notificaciones ----------

    /** Se llama cada vez que el reloj termina de conectarse (lo usa el servicio para el clima). */
    var onConnectedHook: (() -> Unit)? = null

    private val ready: Boolean
        get() = _conn.value == Conn.CONECTADO && _bound.value && !_syncing.value

    /** Enciende en el reloj los avisos de llamadas y de todas las apps. */
    fun enableNoticesOnWatch() {
        if (_conn.value != Conn.CONECTADO || !_bound.value) return
        step("Avisos: encender", Cmds.setNoticeStatus(IDOSetNoticeStatusModel.createDefaultModel()))
    }

    private var lastNoticeKey = ""
    private var lastNoticeAt = 0L

    fun sendNotice(type: Int, title: String, text: String) {
        if (!ready) return
        val key = "$type|$title|$text"
        val now = System.currentTimeMillis()
        if (key == lastNoticeKey && now - lastNoticeAt < 5000) return   // evita repetidos
        lastNoticeKey = key; lastNoticeAt = now
        sdk.cmd.setV2NoticeEvt(type, title.take(40), "", text.take(240)) { ok ->
            Log.d(TAG, "aviso $type enviado: $ok")
        }
    }

    fun sendCall(name: String) {
        if (!ready) return
        sdk.cmd.setV2CallEvt(name.take(40), "") { }
    }

    fun stopCall() {
        if (_conn.value != Conn.CONECTADO) return
        sdk.cmd.stopV2CallEvt { }
    }

    fun testNotice() {
        if (!ready) { say("El reloj no está conectado."); return }
        sdk.cmd.setV2NoticeEvt(0x18, "Mi Reloj", "", "¡Las notificaciones funcionan!") { ok ->
            say(if (ok) "Mira tu reloj." else "El reloj no aceptó la notificación.")
        }
    }

    // ---------- Firmware, batería y GPS (AGPS) ----------

    data class Info(val firmware: String = "", val battery: Int = -1, val gpsUpdated: Long = 0L)

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()

    private val _gpsState = MutableStateFlow("")
    val gpsState: StateFlow<String> = _gpsState.asStateFlow()

    private val _gpsBusy = MutableStateFlow(false)
    val gpsBusy: StateFlow<Boolean> = _gpsBusy.asStateFlow()

    fun refreshInfo() {
        if (_conn.value != Conn.CONECTADO) return
        sdk.device.refreshDeviceInfo(true) { _ ->
            val d = sdk.device
            val fw = listOf(d.fwVersion1, d.fwVersion2, d.fwVersion3).joinToString(".")
            _info.value = _info.value.copy(firmware = fw, battery = d.battLevel)
        }
        IDOEpoManager.shared.lastUpdateTimestamp { t -> _info.value = _info.value.copy(gpsUpdated = t) }
    }

    /** Datos de satélites (AGPS): con ellos el GPS del reloj encuentra tu posición mucho más rápido. */
    private fun initGps() {
        val epo = IDOEpoManager.shared
        epo.delegateGetGps = object : IDOEpoManagerDelegate {
            override fun getAppGpsInfo(): IDOOtaGpsInfo {
                // Posición aproximada: la ciudad del clima (o Santiago si no hay)
                val lat = settings.lat.takeIf { it != 0.0 } ?: -33.45
                val lon = settings.lon.takeIf { it != 0.0 } ?: -70.66
                return IDOOtaGpsInfo(lon.toFloat(), lat.toFloat(), 550f)
            }
        }
        epo.enableAutoUpgrade = true
        epo.listenEpoUpgrade(
            { st ->
                _gpsBusy.value = st != IDOEpoUpgradeStatus.IDLE && st != IDOEpoUpgradeStatus.SUCCESS &&
                    st != IDOEpoUpgradeStatus.FAILURE
                _gpsState.value = when (st) {
                    IDOEpoUpgradeStatus.READY -> "Preparando…"
                    IDOEpoUpgradeStatus.DOWNING -> "Descargando datos de satélites…"
                    IDOEpoUpgradeStatus.MAKING -> "Preparando el archivo…"
                    IDOEpoUpgradeStatus.SENDING -> "Enviando al reloj…"
                    IDOEpoUpgradeStatus.INSTALLING -> "El reloj lo está instalando…"
                    IDOEpoUpgradeStatus.SUCCESS -> "GPS actualizado."
                    IDOEpoUpgradeStatus.FAILURE -> "No se pudo actualizar el GPS."
                    else -> _gpsState.value
                }
            },
            { p -> _gpsState.value = "Descargando… ${pct(p)}%" },
            { p -> _gpsState.value = "Enviando al reloj… ${pct(p)}%" },
            { code ->
                _gpsBusy.value = false
                if (code == 0) {
                    _gpsState.value = "GPS actualizado."
                    IDOEpoManager.shared.lastUpdateTimestamp { t -> _info.value = _info.value.copy(gpsUpdated = t) }
                } else _gpsState.value = "No se pudo actualizar el GPS (código $code)."
            }
        )
    }

    private fun pct(p: Float): Int = (if (p <= 1f) p * 100 else p).toInt().coerceIn(0, 100)

    fun updateGps() {
        if (!ready) { say("El reloj no está conectado."); return }
        if (!IDOEpoManager.shared.isSupported) { say("Tu reloj no usa datos AGPS."); return }
        val bat = _info.value.battery
        if (bat in 0..29) { say("Carga el reloj sobre 30% antes de actualizar."); return }
        _gpsBusy.value = true
        _gpsState.value = "Empezando…"
        IDOEpoManager.shared.willStartInstall(true, 1)
    }

    // ---------- Registro de diagnóstico ----------

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun dlog(text: String) {
        Log.d(TAG, text)
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        _log.value = (_log.value + "$t $text").takeLast(400)
    }

    fun clearLog() { _log.value = emptyList() }

    /** El reloj terminó de prepararse después de conectar (ya tiene su lista de funciones). */
    private val _protocolReady = MutableStateFlow(false)
    private var afterReadyDone = false

    /** Lo que se hace una vez por conexión, cuando el reloj ya está listo. */
    private fun afterReady(why: String) {
        if (afterReadyDone || _conn.value != Conn.CONECTADO) return
        afterReadyDone = true
        _protocolReady.value = true
        dlog("reloj listo ($why)")
        refreshInfo()
        if (_bound.value) {
            enableNoticesOnWatch()
            main.postDelayed({ enableMusicOnWatch() }, 1500)
            main.postDelayed({ onConnectedHook?.invoke() }, 3000)
        }
    }

    private fun describe(res: Any?): String = when (res) {
        null -> "-"
        is IDOBaseModel -> res.toJsonString()
        else -> res.toString()
    }

    /**
     * Envía un comando, anota qué se mandó y qué respondió el reloj, y sigue con [next].
     * Si el reloj no responde en 8 s, sigue igual (así una falla no deja todo colgado).
     */
    private fun <T> step(
        name: String,
        cmd: com.idosmart.protocol_sdk.CmdProtocol<T>,
        next: (code: Int, res: T?) -> Unit = { _, _ -> }
    ) {
        val sent = (cmd as? com.idosmart.pigeon_implement.CmdProtocolParam)?.json.orEmpty()
        var answered = false
        val timeout = Runnable {
            if (!answered) { answered = true; dlog("$name → SIN RESPUESTA en 8 s"); next(-999, null) }
        }
        main.postDelayed(timeout, 8000)
        cmd.send { r ->
            main.post {
                if (answered) return@post
                answered = true
                main.removeCallbacks(timeout)
                dlog("$name → código ${r.error.code}${r.error.message?.let { " ($it)" } ?: ""} | respuesta ${describe(r.res).take(400)} | enviado ${sent.take(300)}")
                next(r.error.code, r.res)
            }
        }
    }

    /** Un comando "set" funcionó si no hubo error y el reloj no dijo isSuccess = 0. */
    private fun setOk(code: Int, res: Any?): Boolean =
        code == 0 && (res !is com.idosmart.pigeon_implement.IDOCmdSetResponseModel || res.isSuccess != 0)

    // ---------- Música ----------

    private val _musicDebug = MutableStateFlow("Todavía no se envió nada.")
    val musicDebug: StateFlow<String> = _musicDebug.asStateFlow()
    fun noteMusic(text: String) { _musicDebug.value = text; dlog("música: $text") }

    private var musicModeOn = false

    fun enableMusicOnWatch() {
        if (!ready) { noteMusic("No se envió: el reloj no está conectado."); return }
        step("Música: activar", Cmds.setMusicOnOff(IDOMusicOnOffParamModel(1, 1))) { c, r ->
            noteMusic(if (setOk(c, r)) "Música activada en el reloj." else "El reloj no activó la música (código $c).")
            musicModeOn = false
            MusicBridge.send(force = true)
        }
    }

    /** status: 1 sonando, 2 pausa, 3 detenido. */
    fun sendMusic(status: Int, pos: Int, total: Int, title: String, artist: String) {
        if (!ready) return
        val control = {
            val p = IDOMusicControlParamModel(status, pos, total, title.take(60), artist.take(60))
            step("Música: canción", Cmds.musicControl(p)) { c, r ->
                noteMusic(
                    if (setOk(c, r)) "Enviado al reloj: \"${title.ifBlank { "(sin título)" }}\""
                    else "El reloj no aceptó la canción (código $c)."
                )
            }
        }
        // Algunos relojes IDO solo muestran la música si el teléfono avisa que hay un reproductor activo
        if (status == 1 && !musicModeOn) {
            musicModeOn = true
            step("Música: inicio", Cmds.musicStart()) { _, _ -> control() }
        } else if (status == 3 && musicModeOn) {
            musicModeOn = false
            control()
            step("Música: fin", Cmds.musicStop())
        } else control()
    }

    // ---------- Menú del reloj ----------

    data class Menu(val items: List<Int>, val min: Int, val max: Int, val v3: Boolean)

    private val _menu = MutableStateFlow<Menu?>(null)
    val menu: StateFlow<Menu?> = _menu.asStateFlow()

    fun loadMenu() {
        if (!ready) { say("El reloj no está conectado."); return }
        // Prueba primero el formato nuevo y, si el reloj no lo entiende, el antiguo
        step("Menú V3: leer", Cmds.getMenuListV3()) { c, m ->
            val list = m?.itemList?.filter { it > 0 }.orEmpty()
            if (c == 0 && m != null && list.isNotEmpty()) {
                _menu.value = Menu(list, m.minShowNum, m.maxShowNum.takeIf { it > 0 } ?: m.supportMaxNum, true)
            } else {
                step("Menú V2: leer", Cmds.getMenuList()) { c2, m2 ->
                    val l2 = m2?.items?.sortedBy { it.index }?.map { it.value }?.filter { it > 0 }.orEmpty()
                    if (c2 == 0 && m2 != null && l2.isNotEmpty()) {
                        _menu.value = Menu(l2, m2.minShowNum, m2.maxShowNum.takeIf { it > 0 } ?: m2.maxNum, false)
                    } else say("Tu reloj no entregó su menú (V3: $c, V2: $c2). Mira el diagnóstico.")
                }
            }
        }
    }

    fun saveMenu(items: List<Int>) {
        if (!ready) { say("El reloj no está conectado."); return }
        val m = _menu.value
        if (m != null && m.min > 0 && items.size < m.min) { say("El reloj necesita al menos ${m.min} pantallas."); return }
        if (m != null && m.max > 0 && items.size > m.max) { say("Tu reloj acepta hasta ${m.max} pantallas."); return }
        val ok = { _menu.value = m?.copy(items = items); say("Menú guardado. Desliza en el reloj para verlo.") }
        val first: (Boolean) -> Unit = { v3 ->
            if (v3) step("Menú V3: guardar", Cmds.setMenuListV3(items)) { c, r ->
                if (setOk(c, r)) ok() else step("Menú V2: guardar", Cmds.setMenuList(IDOMenuListParamModel(items))) { c2, r2 ->
                    if (setOk(c2, r2)) ok() else say("El reloj no aceptó el menú (V3: $c, V2: $c2). Puede que alguna pantalla no exista en tu modelo.")
                }
            } else step("Menú V2: guardar", Cmds.setMenuList(IDOMenuListParamModel(items))) { c, r ->
                if (setOk(c, r)) ok() else step("Menú V3: guardar", Cmds.setMenuListV3(items)) { c2, r2 ->
                    if (setOk(c2, r2)) ok() else say("El reloj no aceptó el menú (V2: $c, V3: $c2). Puede que alguna pantalla no exista en tu modelo.")
                }
            }
        }
        first(m?.v3 ?: true)
    }

    // ---------- Clima ----------

    private val _weatherInfo = MutableStateFlow("")
    val weatherInfo: StateFlow<String> = _weatherInfo.asStateFlow()

    fun sendWeather(w: WeatherNow, city: String, onDone: (Boolean) -> Unit = {}) {
        if (!ready) { dlog("clima: no enviado, reloj no listo"); onDone(false); return }
        val now = java.time.LocalDateTime.now()
        val ok = {
            settings.lastWeather = System.currentTimeMillis()
            _weatherInfo.value = "$city: ${w.temp}° (${w.min}°/${w.max}°)"
            onDone(true)
        }
        val v3 = {
            val p = IDOWeatherV3ParamModel(
                now.monthValue, now.dayOfMonth, now.hour, now.minute, now.second,
                now.dayOfWeek.value,
                w.type,
                w.temp + 100, w.max + 100, w.min + 100,
                city.take(30),
                0,
                w.rain, w.humidity, w.uv * 10, w.wind, 0,
                w.sunrise.first, w.sunrise.second, w.sunset.first, w.sunset.second,
                w.sunTimes.size.coerceAtMost(7),
                "",
                w.hours.take(24).map { IDOHoursWeatherItem(it.type, it.temp + 100, it.rain) },
                w.days.drop(1).take(6).map { IDOFutureItem(it.type, it.max + 100, it.min + 100) },
                w.sunTimes.take(7).map { IDOSunriseItem(it.first.first, it.first.second, it.second.first, it.second.second) },
                emptyList(),
                emptyList()
            )
            Cmds.setWeatherV3(p)
        }
        val v2 = {
            val p = IDOWeatherDataParamModel(
                w.type, w.temp, w.max, w.min, w.humidity, w.uv, 0,
                w.days.drop(1).take(3).map { IDOWeatherDataFuture(it.type, it.min, it.max) }
            )
            Cmds.setWeatherData(p)
        }
        // Enciende el clima en el reloj, manda la ciudad y prueba los dos formatos
        step("Clima: encender", Cmds.setWeatherSwitch(true)) { _, _ ->
            step("Clima: ciudad", Cmds.setWeatherCityName(city.take(30))) { _, _ ->
                val preferV3 = sdk.funcTable.setSetV3Weather || !sdk.funcTable.supportSetWeatherDataV2
                if (preferV3) {
                    step("Clima: V3", v3()) { c, r ->
                        if (setOk(c, r)) ok()
                        else step("Clima: V2 (respaldo)", v2()) { c2, r2 -> if (setOk(c2, r2)) ok() else onDone(false) }
                    }
                } else {
                    step("Clima: V2", v2()) { c, r ->
                        if (setOk(c, r)) ok()
                        else step("Clima: V3 (respaldo)", v3()) { c2, r2 -> if (setOk(c2, r2)) ok() else onDone(false) }
                    }
                }
            }
        }
    }

    // ---------- Prueba completa ----------

    fun runDiagnostics(notifAccess: Boolean) {
        clearLog()
        dlog("=== Diagnóstico Mi Reloj ===")
        dlog("Conexión: ${_conn.value}, vinculado: ${_bound.value}, preparado: ${_protocolReady.value}")
        dlog("Acceso a notificaciones: ${if (notifAccess) "SÍ" else "NO"}")
        dlog("Música en el teléfono: ${_musicDebug.value}")
        if (_conn.value != Conn.CONECTADO) { dlog("El reloj no está conectado. Conéctalo y repite."); return }
        val d = sdk.device
        dlog("Reloj: ${d.deviceName} id=${d.deviceId} plataforma=${d.platform} fw=${d.fwVersion1}.${d.fwVersion2}.${d.fwVersion3}")
        try {
            val ft = sdk.funcTable
            val on = ft.javaClass.methods
                .filter { it.parameterCount == 0 && it.returnType == java.lang.Boolean.TYPE && it.name.startsWith("get") }
                .filter { runCatching { it.invoke(ft) as Boolean }.getOrDefault(false) }
                .map { it.name.removePrefix("get") }
                .sorted()
            dlog("Funciones activas (${on.size}): ${on.joinToString(", ")}")
            dlog("Alarmas máx: ${ft.alarmCount}")
        } catch (e: Exception) {
            dlog("No pude leer la lista de funciones: $e")
        }
        step("Menú V3: leer", Cmds.getMenuListV3()) { _, _ ->
            step("Menú V2: leer", Cmds.getMenuList()) { _, _ ->
                step("Música: activar", Cmds.setMusicOnOff(IDOMusicOnOffParamModel(1, 1))) { _, _ ->
                    step("Música: inicio", Cmds.musicStart()) { _, _ ->
                        val p = IDOMusicControlParamModel(1, 30, 200, "Prueba Mi Reloj", "Diagnóstico")
                        step("Música: canción de prueba", Cmds.musicControl(p)) { _, _ ->
                            val w = WeatherNow(
                                1, 21, 25, 12, 40, 0, 3, 10, 7 to 0, 20 to 30,
                                List(7) { DayForecast(1, 25, 12) }, List(24) { HourForecast(1, 21, 0) },
                                List(7) { (7 to 0) to (20 to 30) }
                            )
                            sendWeather(w, settings.cityName.ifBlank { "Prueba" }) { ok ->
                                dlog("Clima de prueba: ${if (ok) "ACEPTADO" else "RECHAZADO"}")
                                dlog("=== Fin. Toca Copiar y pégalo en el chat. ===")
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- Ajustes del reloj ----------

    private fun reply(label: String): (Int) -> Unit = { code ->
        say(if (code == 0) "$label: listo en el reloj." else "$label: el reloj no lo aceptó ($code).")
    }

    fun applyBrightness(level: Int) {
        if (!ready) { say("El reloj no está conectado."); return }
        val p = IDOScreenBrightnessModel(level.coerceIn(0, 100), 1, 0, 0, 0, 0, 0, 0, 0, 0)
        Cmds.setScreenBrightness(p).send { r -> reply("Brillo")(r.error.code) }
    }

    fun applyRaiseToWake(on: Boolean, seconds: Int) {
        if (!ready) { say("El reloj no está conectado."); return }
        val p = IDOUpHandGestureParamModel(if (on) 1 else 0, seconds.coerceIn(2, 30), 0, 0, 0, 0, 0)
        Cmds.setUpHandGesture(p).send { r -> reply("Levantar para ver")(r.error.code) }
    }

    fun applyStepGoal(steps: Int) {
        if (!ready) { say("El reloj no está conectado."); return }
        Cmds.setSportGoal(IDOSportGoalParamModel(steps, 0, 1)).send { r -> reply("Meta de pasos")(r.error.code) }
    }

    /** mode: 0 apagado, 1 automático (cada [interval] min), 2 continuo. */
    fun applyHeartMode(mode: Int, interval: Int) {
        if (!ready) { say("El reloj no está conectado."); return }
        val p = IDOHeartRateModeParamModel(mode, 0, 0, 0, 23, 59, interval)
        Cmds.setHeartRateMode(p).send { r -> reply("Pulso automático")(r.error.code) }
    }

    fun applyAlarms(list: List<Alarm>) {
        if (!ready) { say("El reloj no está conectado."); return }
        val max = sdk.funcTable.alarmCount.takeIf { it > 0 } ?: 5
        if (list.size > max) { say("Tu reloj acepta hasta $max alarmas."); return }
        val week = listOf(
            IDOWeek.MONDAY, IDOWeek.TUESDAY, IDOWeek.WEDNESDAY, IDOWeek.THURSDAY,
            IDOWeek.FRIDAY, IDOWeek.SATURDAY, IDOWeek.SUNDAY
        )
        // El reloj no borra alarmas: las que sobran se mandan ocultas
        val items = (0 until max).map { i ->
            val a = list.getOrNull(i)
            IDOAlarmItem(
                i + 1, 0,
                a?.hour ?: 0, a?.minute ?: 0,
                "Alarma ${i + 1}",
                a?.on ?: false,
                HashSet(a?.days?.mapNotNull { week.getOrNull(it - 1) } ?: emptyList()),
                0, 1,
                if (a != null) IDOAlarmStatus.DISPLAYED else IDOAlarmStatus.HIDDEN,
                0,
                IDOAlarmType.WAKEUP
            )
        }
        Cmds.setAlarmV3(IDOAlarmModel(items)).send { r -> reply("Alarmas")(r.error.code) }
    }

    // ---------- Respuestas del kit IDO ----------

    private val bleDelegate = object : IDOBleDelegate {
        override fun scanResult(list: List<IDOBleDeviceModel>?) {
            val l = list ?: return
            _scan.value = l
                .filter { !it.macAddress.isNullOrBlank() }
                .distinctBy { it.macAddress }
                .sortedByDescending { it.rssi ?: -999 }
        }

        override fun bluetoothState(state: IDOBluetoothStateModel) {}

        override fun deviceState(state: IDODeviceStateModel) {
            Log.d(TAG, "estado: ${state.state} error=${state.errorState}")
            when (state.state) {
                IDODeviceStateType.CONNECTED -> {
                    _conn.value = Conn.CONECTADO
                    userDisconnect = false
                    main.postDelayed({ refreshLive() }, 1500)
                    // Al reconectar, manda el clima si ya está viejo y la canción actual
                    // Espera a que el reloj termine de prepararse (lista de funciones) antes de mandarle cosas.
                    // Si no avisa, sigue igual a los 12 s.
                    _protocolReady.value = false
                    afterReadyDone = false
                    main.postDelayed({ afterReady("tiempo de espera") }, 12_000)
                }
                IDODeviceStateType.CONNECTING -> _conn.value = Conn.CONECTANDO
                else -> {
                    _conn.value = Conn.DESCONECTADO
                    _measuring.value = false
                    _syncing.value = false
                    // Reintenta solo si el reloj se alejó o se cortó la conexión
                    if (_bound.value && !userDisconnect &&
                        state.errorState != IDOConnectErrorType.DEVICEALREADYBINDANDNOTSUPPORTREBIND &&
                        state.errorState != IDOConnectErrorType.DEVICEHASBEENRESET
                    ) {
                        main.postDelayed({
                            if (_conn.value == Conn.DESCONECTADO && _bound.value) reconnect()
                        }, 15_000)
                    }
                    when (state.errorState) {
                        IDOConnectErrorType.DEVICEALREADYBINDANDNOTSUPPORTREBIND ->
                            say("El reloj sigue vinculado a otra app (VeryFit). Elimínalo en VeryFit y restablece el reloj de fábrica, luego vuelve a intentarlo.")
                        IDOConnectErrorType.DEVICEHASBEENRESET -> {
                            forget()
                            say("El reloj fue restablecido. Vuelve a buscarlo y vincularlo.")
                        }
                        IDOConnectErrorType.BLUETOOTHOFF -> say("Enciende el Bluetooth del teléfono.")
                        else -> {}
                    }
                }
            }
        }

        override fun stateSPP(state: IDOSppStateModel) {}
        override fun stateBT(isPair: Boolean) {}
        override fun writeSPPCompleteState(btMacAddress: String) {}
        override fun receiveData(data: IDOReceiveData) {}
    }

    private val bridgeDelegate = object : IDOBridgeDelegate {
        override fun listenStatusNotification(status: IDOStatusNotification) {
            dlog("aviso del reloj: $status")
            when (status) {
                IDOStatusNotification.FASTSYNCCOMPLETED,
                IDOStatusNotification.FASTSYNCFAILED -> main.postDelayed({ afterReady(status.name) }, 800)
                IDOStatusNotification.UNBINDONAUTHCODEERROR,
                IDOStatusNotification.UNBINDONBINDSTATEERROR -> {
                    store.clearDevice()
                    _bound.value = false
                    say("El reloj ya no reconoce esta app. Vuelve a vincularlo.")
                }
                else -> {}
            }
        }

        override fun checkDeviceBindState(macAddress: String): Boolean =
            store.boundMac().equals(macAddress, ignoreCase = true)

        override fun listenDeviceNotification(status: IDODeviceNotificationModel) {
            val evt = status.controlEvt ?: return
            Log.d(TAG, "control del reloj: $evt ${status.controlJson}")
            main.post { MusicBridge.onWatchControl(evt, status.controlJson) }
        }
        override fun listenWaitingOtaDevice(otaDevice: IDOOtaDeviceModel) {}
    }
}
