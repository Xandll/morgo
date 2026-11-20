package io.qzz.xandll.morgo.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes as PlayerAudio
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var generation = 0
    private var player: ExoPlayer? = null
    private var ringtone: MediaPlayer? = null
    private var streamFailed = false
    @Volatile private var externalFailed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val kind = intent?.getStringExtra(SecondaryAlarmScheduler.EXTRA_KIND) ?: SecondaryAlarmScheduler.KIND_MAIN
        if (action == ACTION_STOP || action == ACTION_AWAKE) {
            if (action == ACTION_AWAKE && kind == SecondaryAlarmScheduler.KIND_MAIN) {
                secondary.onAwakeConfirmed(settingsRepository.loadSettings().alarm)
            }
            if (action == ACTION_AWAKE) {
                sendBroadcast(Intent(ACTION_FINISH).setPackage(packageName))
            }
            externalFailed = false
            stopEverything()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
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
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(if (test) "Test läuft" else "Wecker", test, kind),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
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
        super.onDestroy()
    }

    private suspend fun play(kind: String, test: Boolean) {
        val alarm = settingsRepository.loadSettings().alarm
        val useMusic = (kind == SecondaryAlarmScheduler.KIND_MAIN || test) && alarm.wakeSound == WakeSound.MUSIC
        if (!useMusic) {
            startRingtone()
            return
        }
        val music = alarm.music
        streamFailed = false
        if (music.kind == MusicKind.STREAM) {
            if (!startStream(music)) {
                fallback()
                return
            }
        } else if (externalFailed) {
            fallback()
            return
        }
        val started = SystemClock.elapsedRealtime()
        val rampMs = music.rampSeconds.coerceAtLeast(0) * 1000L
        val target = music.volumePercent.coerceIn(0, 100) / 100f
        // Musik, die schon vor dem Wecker lief, zählt nicht als Erfolg.
        var heardQuiet = !musicActive()
        while (true) {
            val elapsedMs = SystemClock.elapsedRealtime() - started
            player?.volume = WakeSoundMath.volumeAt(elapsedMs, rampMs, target)
            if (!musicActive()) heardQuiet = true
            val externalPlaying = music.kind != MusicKind.STREAM && heardQuiet && musicActive()
            val playing = player?.isPlaying == true || externalPlaying
            val elapsedSec = (elapsedMs / 1000L).toInt()
            if (externalFailed || streamFailed || WakeSoundMath.fallbackNow(music.needsNetwork(), online(), elapsedSec, playing, music.fallbackSeconds)) {
                fallback()
                return
            }
            delay(250)
        }
    }

    private fun startStream(music: MusicAlarmSettings): Boolean {
        val url = music.streamUrl.trim()
        if (url.isEmpty()) return false
        streamFailed = false
        return try {
            releasePlayer()
            val exo = ExoPlayer.Builder(this).build()
            exo.setAudioAttributes(
                PlayerAudio.Builder()
                    .setUsage(C.USAGE_ALARM)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            exo.volume = 0f
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    streamFailed = true
                }
            })
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
            player = exo
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fallback() {
        releasePlayer()
        notificationManagerNotify("Klingelton, Musik hat nicht gespielt")
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
        releasePlayer()
        releaseRingtone()
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
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text, test = false, kind = SecondaryAlarmScheduler.KIND_MAIN))
    }

    private fun notification(text: String, test: Boolean, kind: String): Notification {
        val actionIntent = Intent(this, WakeSoundService::class.java).apply {
            action = if (test) ACTION_STOP else ACTION_AWAKE
            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
        }
        val action = PendingIntent.getService(
            this,
            7,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, MorgoApplication.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(if (test) "Musik testen" else "Morgo")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, if (test) "Stoppen" else "Ich bin wach", action)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 44
        const val ACTION_TEST = "io.qzz.xandll.morgo.WAKE_SOUND_TEST"
        const val ACTION_STOP = "io.qzz.xandll.morgo.WAKE_SOUND_STOP"
        const val ACTION_AWAKE = "io.qzz.xandll.morgo.WAKE_SOUND_AWAKE"
        const val ACTION_FINISH = "io.qzz.xandll.morgo.WAKE_SOUND_FINISH"
        const val ACTION_MUSIC_FAILED = "io.qzz.xandll.morgo.WAKE_SOUND_MUSIC_FAILED"

        fun start(context: Context, kind: String) {
            val intent = Intent(context, WakeSoundService::class.java).apply {
                putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
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
