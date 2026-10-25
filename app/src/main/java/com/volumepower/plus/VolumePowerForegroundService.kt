package com.volumepower.plus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/**
 * Keeps the app alive, plays inaudible audio (so the volume keys keep reaching the app with the screen off),
 * and re-enables the accessibility service the moment the system turns it off.
 */
class VolumePowerForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    @Volatile private var playing = false
    private var session: MediaSession? = null
    private var lastTrigger = 0L
    private val keyReader = ShizukuKeyReader { code -> handler.post { onShizukuKey(code) } }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            SelfHeal.healIfWanted(this@VolumePowerForegroundService)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            SelfHeal.healIfWanted(this@VolumePowerForegroundService)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        val level = Prefs.notifPriority(this)
        val channelId = CHANNEL_PREFIX + level
        val importance = when (level) {
            0 -> NotificationManager.IMPORTANCE_MIN
            1 -> NotificationManager.IMPORTANCE_LOW
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Volume Power Plus", importance).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle("Volume Power Plus is running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        nm.notificationChannels
            .filter { it.id != channelId && it.id.startsWith("vpp_keepalive") }
            .forEach { try { nm.deleteNotificationChannel(it.id) } catch (_: Exception) {} }

        contentResolver.unregisterContentObserver(observer)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer
        )
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)

        if (Prefs.keepAlive(this)) startSilentAudio() else stopSilentAudio()
        startVolumeSession()
        if (Prefs.shizukuKeys(this)) keyReader.start() else keyReader.stop()
        return START_STICKY
    }

    /**
     * With the screen off Android never sends volume keys to accessibility services.
     * It sends them to the active media session instead, so we publish a "remote volume"
     * session and receive the key presses in onAdjustVolume().
     */
    private fun startVolumeSession() {
        if (session != null) return
        try {
            val s = MediaSession(this, "VolumePowerPlus")
            s.setPlaybackToRemote(object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    handler.post { onScreenOffKey(direction) }
                }
            })
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .setActions(0L)
                    .build()
            )
            s.isActive = true
            session = s
        } catch (_: Exception) {
        }
    }

    private fun onScreenOffKey(direction: Int) {
        if (!Prefs.featureEnabled(this)) return
        val triggerDir = if (Prefs.triggerKey(this) == KeyEvent.KEYCODE_VOLUME_DOWN) -1 else 1
        if (direction == triggerDir) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTrigger < 400) return
            lastTrigger = now
            if (ScreenWaker.screenOff(this)) ScreenWaker.wake(this)
        } else if (direction != 0) {
            // The other volume key keeps its normal job.
            getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        }
    }

    /** Hardware key seen by Shizuku (works with the screen off, whatever app is playing). */
    private fun onShizukuKey(code: Int) {
        if (!Prefs.featureEnabled(this) || code != Prefs.triggerKey(this)) return
        if (ScreenWaker.screenOff(this)) ScreenWaker.wake(this)
        else if (!SelfHeal.isServiceEnabled(this)) ScreenWaker.sleep(this) // accessibility is off: still lock
    }

    private fun stopVolumeSession() {
        try {
            session?.isActive = false
            session?.release()
        } catch (_: Exception) {
        }
        session = null
    }

    private fun startSilentAudio() {
        if (playing) return
        try {
            val rate = 8000
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(min * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            playing = true
            t.play()
            Thread {
                val silence = ByteArray(min)
                while (playing) {
                    if (t.write(silence, 0, silence.size) < 0) break
                }
            }.start()
        } catch (_: Exception) {
            playing = false
        }
    }

    private fun stopSilentAudio() {
        playing = false
        try {
            track?.stop()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        contentResolver.unregisterContentObserver(observer)
        stopSilentAudio()
        keyReader.stop()
        stopVolumeSession()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_PREFIX = "vpp_keepalive_"

        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, VolumePowerForegroundService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, VolumePowerForegroundService::class.java))
        }
    }
}
