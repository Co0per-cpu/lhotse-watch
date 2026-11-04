package com.coopercpu.reloj

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import org.json.JSONObject

/**
 * Muestra en el reloj la canción que suena (Spotify u otra app de música)
 * y deja controlarla desde el reloj. También hace sonar el teléfono
 * cuando usas "Buscar teléfono" en el reloj.
 */
object MusicBridge {
    private const val TAG = "Reloj"
    private val main = Handler(Looper.getMainLooper())
    private lateinit var ctx: Context
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var lastSent = ""

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = send()
        override fun onPlaybackStateChanged(state: PlaybackState?) = send()
        override fun onSessionDestroyed() {
            controller = null
            send()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) send(force = true)
            main.postDelayed(this, 10_000)
        }
    }

    /** Se llama cuando Android conecta el lector de notificaciones (ahí ya hay permiso). */
    fun start(context: Context) {
        ctx = context.applicationContext
        try {
            val m = ctx.getSystemService(MediaSessionManager::class.java)
            msm = m
            val cn = ComponentName(ctx, NotifService::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            pick(m.getActiveSessions(cn))
            main.removeCallbacks(tick)
            main.postDelayed(tick, 10_000)
        } catch (e: SecurityException) {
            Log.w(TAG, "sin acceso a la música", e)
        }
    }

    fun stop() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        controller?.unregisterCallback(callback)
        controller = null
        main.removeCallbacks(tick)
    }

    /** Prefiere Spotify; si no, la app de música que esté sonando. */
    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.packageName == "com.spotify.music" && isPlaying(it) }
            ?: list.firstOrNull { isPlaying(it) }
            ?: list.firstOrNull { it.packageName == "com.spotify.music" }
            ?: list.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, main)
        send(force = true)
    }

    private fun isPlaying(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING

    /** Manda al reloj título, artista y estado. */
    fun send(force: Boolean = false) {
        if (!::ctx.isInitialized) return
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        val total = ((md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L) / 1000).toInt()
        val ps = c?.playbackState
        val status = when (ps?.state) {
            PlaybackState.STATE_PLAYING -> 1
            PlaybackState.STATE_PAUSED -> 2
            PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, null -> 3
            else -> 2
        }
        val pos = ((ps?.position ?: 0L) / 1000).toInt().coerceAtLeast(0)
        val key = "$title|$artist|$status"
        if (!force && key == lastSent) return
        lastSent = key
        WatchManager.sendMusic(status, pos, total, title, artist)
    }

    /** Órdenes que llegan desde el reloj (códigos oficiales de IDO). */
    fun onWatchControl(evt: Int, json: String?) {
        val t = controller?.transportControls
        val audio = if (::ctx.isInitialized) ctx.getSystemService(AudioManager::class.java) else null
        when (evt) {
            551 -> t?.play()
            552 -> t?.pause()
            553 -> t?.stop()
            554 -> t?.skipToPrevious()
            555 -> t?.skipToNext()
            558 -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            559 -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            565 -> try {
                val pct = JSONObject(json ?: "{}").optInt("volume_percentage", -1)
                if (pct >= 0 && audio != null) {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct / 100f).toInt(), 0)
                }
            } catch (_: Exception) {}
            570 -> FindPhone.start(ctx)
            571 -> FindPhone.stop()
        }
        if (evt in 551..555) main.postDelayed({ send(force = true) }, 700)
    }
}

/** Hace sonar y vibrar el teléfono a todo volumen por 30 segundos. */
object FindPhone {
    private var ringtone: android.media.Ringtone? = null
    private val main = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        stop()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(ctx, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .build()
                play()
            }
            vib = vibrator(ctx)
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (e: Exception) {
            Log.w("Reloj", "buscar teléfono", e)
        }
        main.postDelayed({ stop() }, 30_000)
    }

    private var vib: Vibrator? = null

    fun stop() {
        main.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vib?.cancel() } catch (_: Exception) {}
    }

    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
}
