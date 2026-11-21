package io.qzz.xandll.morgo.data.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.SonaraShuffle
import java.lang.ref.WeakReference

/**
 * Startet Sonara 1.0.119: erst der MediaController, bei Verbindungsfehler der Dienst,
 * danach die unsichtbare Activity. Bekannte Fehler enden sofort beim Klingelton.
 */
object SonaraPlayback {
    private const val TAG = "MorgoShuffle"
    private const val ACTIVITY_WAIT_MS = 1_500L

    @Volatile var playing: Boolean = false
        private set

    @Volatile var failure: String? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private var ticket = 0
    private var controller: MediaController? = null
    private var activityRef: WeakReference<Activity>? = null

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun cancel() {
        ticket++
        main.removeCallbacksAndMessages(null)
        controller?.runCatching { release() }
        controller = null
    }

    fun start(context: Context, music: MusicAlarmSettings, foregroundStartAllowed: Boolean) {
        val mine = ++ticket
        playing = false
        failure = null
        controller?.runCatching { release() }
        controller = null
        val app = context.applicationContext
        main.post {
            if (mine != ticket) return@post
            connect(app, music, foregroundStartAllowed, mine)
        }
    }

    private fun connect(
        context: Context,
        music: MusicAlarmSettings,
        foregroundStartAllowed: Boolean,
        mine: Int
    ) {
        val timing = SonaraShuffle.timing(music.fallbackSeconds)
        val token = runCatching {
            SessionToken(context, ComponentName(SonaraShuffle.PACKAGE, SonaraShuffle.SERVICE))
        }.getOrElse {
            log("Verbindung")
            transports(context, music, foregroundStartAllowed, serviceAccepted = false, mine)
            return
        }
        val future = MediaController.Builder(context, token).buildAsync()
        val timeout = Runnable {
            if (mine != ticket) return@Runnable
            MediaController.releaseFuture(future)
            log("Zeit")
            transports(context, music, foregroundStartAllowed, serviceAccepted = false, mine)
        }
        main.postDelayed(timeout, timing.connectMs)
        future.addListener({
            main.removeCallbacks(timeout)
            if (mine != ticket) return@addListener
            val connected = runCatching { future.get() }.getOrNull()
            if (connected == null) {
                log("Verbindung")
                transports(context, music, foregroundStartAllowed, serviceAccepted = false, mine)
                return@addListener
            }
            controller = connected
            send(context, connected, music, timing.commandMs, foregroundStartAllowed, mine)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun send(
        context: Context,
        connected: MediaController,
        music: MusicAlarmSettings,
        commandMs: Long,
        foregroundStartAllowed: Boolean,
        mine: Int
    ) {
        if (!offersPlay(connected)) {
            log("Befehl fehlt")
            transports(context, music, foregroundStartAllowed, serviceAccepted = false, mine)
            return
        }
        val wasPlaying = connected.isPlaying
        var settled = false
        fun finish(reading: SonaraShuffle.Reading) {
            if (mine != ticket) return
            if (reading.verdict == SonaraShuffle.Verdict.RINGTONE) {
                settled = true
                playing = false
                failure = reading.message
                return
            }
            if (settled || failure != null) return
            when (reading.verdict) {
                SonaraShuffle.Verdict.PLAYING -> {
                    settled = true
                    playing = true
                }
                SonaraShuffle.Verdict.NEXT_TRANSPORT -> {
                    settled = true
                    log("Befehl")
                    transports(context, music, foregroundStartAllowed, serviceAccepted = false, mine)
                }
                SonaraShuffle.Verdict.RINGTONE -> Unit
            }
        }
        connected.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying || wasPlaying) return
                finish(SonaraShuffle.read(SonaraShuffle.Code.OTHER, null, playing = true))
            }
        })
        val args = Bundle()
        SonaraShuffle.playlistArgument(music.sonaraPlaylistId)?.let { id ->
            args.putString(SonaraShuffle.PLAYLIST_ID, id)
        }
        val pending = connected.sendCustomCommand(
            SessionCommand(SonaraShuffle.PLAY_SHUFFLED, Bundle()),
            args
        )
        val timeout = Runnable {
            if (connected.isPlaying && !wasPlaying) {
                finish(SonaraShuffle.read(SonaraShuffle.Code.SUCCESS, null, playing = true))
            } else {
                finish(SonaraShuffle.read(SonaraShuffle.Code.OTHER, null, playing = false))
            }
        }
        main.postDelayed(timeout, commandMs)
        pending.addListener({
            main.removeCallbacks(timeout)
            val result = runCatching { pending.get() }.getOrNull()
            if (result == null) {
                finish(SonaraShuffle.read(SonaraShuffle.Code.OTHER, null, connected.isPlaying && !wasPlaying))
                return@addListener
            }
            finish(SonaraShuffle.read(codeOf(result.resultCode), result.sessionError?.message, connected.isPlaying && !wasPlaying))
        }, ContextCompat.getMainExecutor(context))
    }

    private fun transports(
        context: Context,
        music: MusicAlarmSettings,
        foregroundStartAllowed: Boolean,
        serviceAccepted: Boolean,
        mine: Int
    ) {
        if (mine != ticket || playing || failure != null) return
        var accepted = serviceAccepted
        SonaraShuffle.transports(foregroundStartAllowed).forEach { transport ->
            if (mine != ticket || playing || failure != null) return
            when (transport) {
                SonaraShuffle.Transport.CONTROLLER -> Unit
                SonaraShuffle.Transport.SERVICE -> {
                    if (!foregroundStartAllowed) return@forEach
                    accepted = startService(context, music)
                    if (!accepted) log("Dienst")
                }
                SonaraShuffle.Transport.ACTIVITY -> {
                    if (!SonaraShuffle.activityAfterService(accepted)) return@forEach
                    if (activityRef?.get() == null) {
                        main.postDelayed({
                            if (mine != ticket || playing || failure != null) return@postDelayed
                            if (!startActivity(context, music)) {
                                log("Activity")
                                failure = SonaraShuffle.defaultMessage(SonaraShuffle.Code.OTHER)
                            }
                        }, ACTIVITY_WAIT_MS)
                        return
                    }
                    if (!startActivity(context, music)) {
                        log("Activity")
                        failure = SonaraShuffle.defaultMessage(SonaraShuffle.Code.OTHER)
                    }
                }
            }
        }
    }

    private fun startService(context: Context, music: MusicAlarmSettings): Boolean {
        val intent = intent(music).setComponent(ComponentName(SonaraShuffle.PACKAGE, SonaraShuffle.SERVICE))
        return runCatching { context.startForegroundService(intent) }.isSuccess
    }

    private fun startActivity(context: Context, music: MusicAlarmSettings): Boolean {
        val intent = intent(music).setPackage(SonaraShuffle.PACKAGE)
        val activity = activityRef?.get()
        if (activity != null && !activity.isFinishing) {
            return runCatching { activity.startActivity(intent) }.isSuccess
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun intent(music: MusicAlarmSettings): Intent {
        return Intent(SonaraShuffle.ACTION_PLAY_SHUFFLED).apply {
            SonaraShuffle.playlistArgument(music.sonaraPlaylistId)?.let { id ->
                putExtra(SonaraShuffle.PLAYLIST_ID, id)
            }
        }
    }

    private fun offersPlay(controller: MediaController): Boolean {
        return controller.availableSessionCommands.commands.any { command ->
            command.commandCode == SessionCommand.COMMAND_CODE_CUSTOM &&
                command.customAction == SonaraShuffle.PLAY_SHUFFLED
        }
    }

    private fun codeOf(resultCode: Int): SonaraShuffle.Code {
        return when (resultCode) {
            SessionResult.RESULT_SUCCESS -> SonaraShuffle.Code.SUCCESS
            SessionResult.RESULT_ERROR_BAD_VALUE -> SonaraShuffle.Code.BAD_VALUE
            SessionResult.RESULT_ERROR_SESSION_AUTHENTICATION_EXPIRED -> SonaraShuffle.Code.AUTHENTICATION_EXPIRED
            SessionResult.RESULT_ERROR_INVALID_STATE -> SonaraShuffle.Code.INVALID_STATE
            SessionResult.RESULT_ERROR_IO -> SonaraShuffle.Code.IO
            else -> SonaraShuffle.Code.OTHER
        }
    }

    private fun log(reason: String) {
        Log.w(TAG, "Sonara nicht gestartet: $reason")
    }
}
