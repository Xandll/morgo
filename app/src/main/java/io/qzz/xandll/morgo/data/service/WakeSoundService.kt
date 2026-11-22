package io.qzz.xandll.morgo.data.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes as PlayerAudio
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.data.datasource.RadioBrowser
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.AlarmCommand
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.StreamPhase
import io.qzz.xandll.morgo.domain.model.StreamSample
import io.qzz.xandll.morgo.domain.model.SonaraShuffle
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.domain.model.WakeSoundMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import javax.inject.Inject

@AndroidEntryPoint
class WakeSoundService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var secondary: SecondaryAlarmScheduler
    @Inject lateinit var alarms: AlarmSchedulerService
    @Inject lateinit var repository: MorgoRepository
    @Inject lateinit var soundStatus: WakeSoundStatus

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var job: Job? = null
    private var generation = 0
    private var player: ExoPlayer? = null
    private var ringtone: MediaPlayer? = null
    private var streamErrorCode: String? = null
    private var streamErrorDetail: String? = null
    private var testing = false
    @Volatile private var externalFailed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val kind = intent?.getStringExtra(SecondaryAlarmScheduler.EXTRA_KIND) ?: SecondaryAlarmScheduler.KIND_MAIN
        if (action == ACTION_STOP || action == ACTION_AWAKE || action == ACTION_SNOOZE) {
            val effect = when (action) {
                ACTION_SNOOZE -> AlarmFace.effect(AlarmCommand.SNOOZE)
                ACTION_AWAKE -> AlarmFace.effect(AlarmCommand.STOP)
                else -> null
            }
            if (effect?.confirmAwake == true && kind == SecondaryAlarmScheduler.KIND_MAIN) {
                secondary.onAwakeConfirmed(settingsRepository.loadSettings().alarm)
            }
            if (effect?.snooze == true) {
                val minutes = (intent?.getIntExtra(AlarmAlerts.EXTRA_SNOOZE_MINUTES, 0) ?: 0)
                    .takeIf { it > 0 }
                    ?: settingsRepository.loadSettings().alarm.snoozeMinutes
                alarms.snoozeAlarm(minutes.coerceAtLeast(1))
            }
            if (effect?.closeScreen == true) {
                sendBroadcast(Intent(ACTION_FINISH).setPackage(packageName))
            }
            externalFailed = false
            stopEverything()
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            AlarmAlerts.cancelWear(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_MUSIC_FAILED) {
            externalFailed = true
            if (job?.isActive == true) return START_NOT_STICKY
        } else {
            externalFailed = false
        }
        val test = action == ACTION_TEST
        val label = intent?.getStringExtra(SecondaryAlarmScheduler.EXTRA_LABEL).orEmpty()
        testing = test
        if (test) soundStatus.clear()
        acquireWakeLock()
        if (action == null) runWakeActions(kind)
        val notes = notifications(test, kind, label)
        try {
            ServiceCompat.startForeground(
                this,
                AlarmAlerts.NOTIFICATION_ID,
                notes.phone,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            publishWear(notes.wear)
        } catch (e: Exception) {
            val text = WakeSoundMath.streamFailure(e.javaClass.simpleName, e.message)
            if (test) soundStatus.reportFailure(text)
            getSystemService(android.app.NotificationManager::class.java)
                .notify(AlarmAlerts.NOTIFICATION_ID, notifications(test, kind, label, text).phone)
            stopSelf()
            return START_NOT_STICKY
        }
        job?.cancel()
        val gen = ++generation
        job = scope.launch {
            play(kind, test)
            if (gen != generation) return@launch
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        stopEverything()
        releaseWakeLock()
        releaseWifiLock()
        AlarmAlerts.cancelWear(this)
        super.onDestroy()
    }

    private fun runWakeActions(kind: String) {
        if (kind != SecondaryAlarmScheduler.KIND_MAIN) return
        val home = settingsRepository.loadSettings().homeAssistant
        if (home.url.isBlank() || home.token.isBlank() || home.wakeUpActions.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            home.wakeUpActions.forEach { entityId ->
                repository.callHomeAssistant(home.url, home.token, entityId, HaServiceKind.TURN_ON)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "morgo:alarm").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(WifiManager::class.java) ?: return
        @Suppress("DEPRECATION")
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "morgo:alarm-wifi").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.runCatching { if (isHeld) release() }
        wifiLock = null
    }

    private suspend fun play(kind: String, test: Boolean) {
        val alarm = settingsRepository.loadSettings().alarm
        val useMusic = (kind == SecondaryAlarmScheduler.KIND_MAIN || test) && alarm.wakeSound == WakeSound.MUSIC
        if (!useMusic) {
            startRingtone()
            return
        }
        val music = alarm.music
        streamErrorCode = null
        streamErrorDetail = null
        var lastRetrySecond = -3
        if (music.kind == MusicKind.STREAM) {
            acquireWifiLock()
            if (music.streamUrl.isBlank()) {
                fallback(WakeSoundMath.streamFailure("Stream fehlt", null))
                return
            }
            startStream(music)
        } else if (externalFailed) {
            fallback(WakeSoundMath.streamFailure("App", "Musik-App hat sich nicht geöffnet"))
            return
        }
        val sonara = music.kind == MusicKind.APP && music.appPackage == SonaraShuffle.PACKAGE
        if (sonara) {
            SonaraPlayback.start(this, music, foregroundStartAllowed = true)
        }
        val started = SystemClock.elapsedRealtime()
        val rampMs = music.rampSeconds.coerceAtLeast(0) * 1000L
        val target = music.volumePercent.coerceIn(0, 100) / 100f
        // Musik, die schon vor dem Wecker lief, zählt nicht als Erfolg.
        var heardQuiet = !musicActive()
        while (true) {
            val elapsedMs = SystemClock.elapsedRealtime() - started
            val elapsedSec = (elapsedMs / 1000L).toInt()
            player?.volume = WakeSoundMath.audibleVolume(WakeSoundMath.volumeAt(elapsedMs, rampMs, target))
            if (!musicActive()) heardQuiet = true
            val externalPlaying = music.kind != MusicKind.STREAM && heardQuiet && musicActive()
            val phase = phaseOf(player)
            val playing = SonaraPlayback.playing || externalPlaying ||
                WakeSoundMath.countsAsPlaying(phase, player?.playWhenReady == true)
            if (music.kind == MusicKind.STREAM &&
                WakeSoundMath.shouldRetry(elapsedSec, music.fallbackSeconds, streamErrorCode != null, lastRetrySecond)
            ) {
                lastRetrySecond = elapsedSec
                startStream(music)
            }
            val sonaraFailure = if (sonara) SonaraPlayback.failure else null
            val reason = when {
                sonaraFailure != null -> sonaraFailure
                externalFailed -> WakeSoundMath.streamFailure("App", "Musik-App hat sich nicht geöffnet")
                music.kind == MusicKind.STREAM -> WakeSoundMath.fallbackReason(
                    StreamSample(
                        elapsedSeconds = elapsedSec,
                        limitSeconds = music.fallbackSeconds,
                        online = online(),
                        phase = phase,
                        playWhenReady = player?.playWhenReady == true,
                        errorCode = streamErrorCode,
                        errorDetail = streamErrorDetail
                    )
                )
                sonara && WakeSoundMath.fallbackNow(false, true, elapsedSec, playing, music.fallbackSeconds) ->
                    "Sonara hat nach ${music.fallbackSeconds.coerceAtLeast(5)}s nicht gespielt, Klingelton"
                WakeSoundMath.fallbackNow(music.needsNetwork(), online(), elapsedSec, playing, music.fallbackSeconds) ->
                    "Klingelton, Musik hat nicht gespielt"
                else -> null
            }
            if (reason != null) {
                fallback(reason)
                return
            }
            delay(250)
        }
    }

    private fun phaseOf(exo: ExoPlayer?): StreamPhase {
        return when (exo?.playbackState) {
            Player.STATE_BUFFERING -> StreamPhase.BUFFERING
            Player.STATE_READY -> StreamPhase.READY
            Player.STATE_ENDED -> StreamPhase.ENDED
            else -> StreamPhase.IDLE
        }
    }

    private fun startStream(music: MusicAlarmSettings): Boolean {
        val url = music.streamUrl.trim()
        if (url.isEmpty()) {
            streamErrorCode = "Stream fehlt"
            streamErrorDetail = null
            return false
        }
        streamErrorCode = null
        streamErrorDetail = null
        return try {
            releasePlayer()
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(RadioBrowser.userAgent(BuildConfig.VERSION_NAME))
                .setAllowCrossProtocolRedirects(true)
            val exo = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(http))
                .build()
            exo.setAudioAttributes(
                PlayerAudio.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            exo.setWakeMode(C.WAKE_MODE_NETWORK)
            exo.volume = WakeSoundMath.audibleVolume(0f)
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    streamErrorCode = error.errorCodeName
                    streamErrorDetail = error.message ?: error.cause?.javaClass?.simpleName
                }
            })
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
            player = exo
            true
        } catch (e: Exception) {
            streamErrorCode = e.javaClass.simpleName
            streamErrorDetail = e.message
            false
        }
    }

    private fun fallback(reason: String) {
        SonaraPlayback.cancel()
        releasePlayer()
        val text = if (reason.contains("Klingelton")) reason else "$reason, Klingelton"
        notificationManagerNotify(text)
        if (testing) soundStatus.reportFailure(text)
        startRingtone()
    }

    private fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        try {
            releaseRingtone()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val media = MediaPlayer()
            media.setDataSource(this, uri)
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            media.isLooping = true
            media.prepare()
            media.start()
            ringtone = media
        } catch (e: Exception) {
            releaseRingtone()
        }
    }

    private fun musicActive(): Boolean {
        val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return audio.isMusicActive
    }

    private fun online(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun stopEverything() {
        job?.cancel()
        SonaraPlayback.cancel()
        releasePlayer()
        releaseRingtone()
        releaseWifiLock()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun releaseRingtone() {
        ringtone?.runCatching {
            stop()
            release()
        }
        ringtone = null
    }

    private fun notificationManagerNotify(text: String) {
        val notes = notifications(
            test = testing,
            kind = SecondaryAlarmScheduler.KIND_MAIN,
            label = "",
            bodyOverride = text
        )
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(AlarmAlerts.NOTIFICATION_ID, notes.phone)
        publishWear(notes.wear)
    }

    private fun publishWear(notification: Notification) {
        getSystemService(android.app.NotificationManager::class.java)
            .notify(AlarmAlerts.WEAR_NOTIFICATION_ID, notification)
    }

    private fun notifications(
        test: Boolean,
        kind: String,
        label: String,
        bodyOverride: String? = null
    ): AlarmNotes {
        val alarm = settingsRepository.loadSettings().alarm
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        val clock = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        val spec = if (test) {
            AlarmFace.alert(
                kind, label, "Musik testen", snoozeMinutes = 0, withSound = true,
                interactive = true, keyguardLocked = false, clock = "Test läuft"
            ).copy(title = "Musik testen", fullScreen = false, showSnooze = false)
        } else {
            AlarmFace.alert(
                kind = kind,
                label = label,
                storedTitle = settingsRepository.alarmTitle(),
                snoozeMinutes = alarm.snoozeMinutes,
                withSound = true,
                interactive = power?.isInteractive == true,
                keyguardLocked = keyguard?.isKeyguardLocked == true,
                clock = clock
            )
        }
        val shown = if (bodyOverride != null) spec.copy(body = bodyOverride) else spec
        return AlarmAlerts.notifications(this, shown, kind, label, alarm.snoozeMinutes, test)
    }

    companion object {
        const val ACTION_TEST = "io.qzz.xandll.morgo.WAKE_SOUND_TEST"
        const val ACTION_STOP = "io.qzz.xandll.morgo.WAKE_SOUND_STOP"
        const val ACTION_AWAKE = "io.qzz.xandll.morgo.WAKE_SOUND_AWAKE"
        const val ACTION_SNOOZE = "io.qzz.xandll.morgo.WAKE_SOUND_SNOOZE"
        const val ACTION_FINISH = "io.qzz.xandll.morgo.WAKE_SOUND_FINISH"
        const val ACTION_MUSIC_FAILED = "io.qzz.xandll.morgo.WAKE_SOUND_MUSIC_FAILED"

        fun start(context: Context, kind: String, label: String = "") {
            val intent = Intent(context, WakeSoundService::class.java).apply {
                putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
                putExtra(SecondaryAlarmScheduler.EXTRA_LABEL, label)
            }
            context.startForegroundService(intent)
        }

        fun startTest(context: Context) {
            val intent = Intent(context, WakeSoundService::class.java).apply {
                action = ACTION_TEST
                putExtra(SecondaryAlarmScheduler.EXTRA_KIND, SecondaryAlarmScheduler.KIND_MAIN)
            }
            context.startForegroundService(intent)
        }
    }
}
