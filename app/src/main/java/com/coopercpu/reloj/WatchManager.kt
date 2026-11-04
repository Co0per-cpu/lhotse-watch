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
        Cmds.setNoticeStatus(IDOSetNoticeStatusModel.createDefaultModel()).send { r ->
            Log.d(TAG, "avisos encendidos: ${r.error.code}")
        }
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

    // ---------- Clima ----------

    private val _weatherInfo = MutableStateFlow("")
    val weatherInfo: StateFlow<String> = _weatherInfo.asStateFlow()

    fun sendWeather(w: WeatherNow, city: String, onDone: (Boolean) -> Unit = {}) {
        if (!ready) { onDone(false); return }
        Cmds.setWeatherSwitch(true).send { }
        val now = java.time.LocalDateTime.now()
        val done: (Int) -> Unit = { code ->
            val ok = code == 0
            if (ok) {
                settings.lastWeather = System.currentTimeMillis()
                _weatherInfo.value = "$city: ${w.temp}° (${w.min}°/${w.max}°)"
            }
            onDone(ok)
        }
        if (sdk.funcTable.setSetV3Weather) {
            // Formato V3: las temperaturas van con +100, igual que el ejemplo oficial de IDO
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
                w.hours.map { IDOHoursWeatherItem(it.type, it.temp + 100, it.rain) },
                w.days.drop(1).take(6).map { IDOFutureItem(it.type, it.max + 100, it.min + 100) },
                w.sunTimes.take(7).map { IDOSunriseItem(it.first.first, it.first.second, it.second.first, it.second.second) },
                emptyList(),
                emptyList()
            )
            Cmds.setWeatherV3(p).send { r -> done(r.error.code) }
        } else {
            val p = IDOWeatherDataParamModel(
                w.type, w.temp, w.max, w.min, w.humidity, w.uv, 0,
                w.days.drop(1).take(3).map { IDOWeatherDataFuture(it.type, it.min, it.max) }
            )
            Cmds.setWeatherCityName(city.take(30)).send { }
            Cmds.setWeatherData(p).send { r -> done(r.error.code) }
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
                    // Al reconectar, manda el clima si ya está viejo
                    main.postDelayed({ onConnectedHook?.invoke() }, 4000)
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
            Log.d(TAG, "aviso: $status")
            when (status) {
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

        override fun listenDeviceNotification(status: IDODeviceNotificationModel) {}
        override fun listenWaitingOtaDevice(otaDevice: IDOOtaDeviceModel) {}
    }
}
