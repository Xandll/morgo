package io.qzz.xandll.morgo.data.service

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController as SessionController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicShufflePlan
import io.qzz.xandll.morgo.domain.model.ShuffleStyle
import io.qzz.xandll.morgo.domain.model.ShuffleTarget
import io.qzz.xandll.morgo.domain.model.SonaraShuffle

/**
 * Bittet die bereits geöffnete Musik-App um Zufallswiedergabe.
 * Schlägt das fehl, bleibt der normale Start stehen. Kein Dialog, kein neues Recht.
 */
object MusicShuffle {
    private const val TAG = "MorgoShuffle"
    private const val BROWSER = "android.media.browse.MediaBrowserService"
    /** PlaybackState.SHUFFLE_MODE_ALL. Das SDK-Stub hier erklärt die Konstante nicht. */
    private const val SHUFFLE_MODE_ALL = 1
    private const val ENABLED_LISTENERS = "enabled_notification_listeners"

    private var browser: MediaBrowser? = null
    private var sonaraController: SessionController? = null

    fun request(context: Context, music: MusicAlarmSettings) {
        val target = MusicShufflePlan.target(music) ?: return
        val app = context.applicationContext
        if (target.style == ShuffleStyle.SONARA) {
            Handler(Looper.getMainLooper()).post {
                runCatching { connectSonara(app, music) }.onFailure { log("Sonara") }
            }
            return
        }
        Handler(Looper.getMainLooper()).post {
            runCatching { connect(app, target) }.onFailure { log("MediaBrowser") }
            runCatching { activeSession(app, target) }.onFailure { log("Session") }
            runCatching { openDeepLinks(context, target) }.onFailure { log("Deep-Link") }
        }
    }

    private fun connectSonara(context: Context, music: MusicAlarmSettings) {
        sonaraController?.runCatching { release() }
        sonaraController = null
        val token = SessionToken(
            context,
            ComponentName(SonaraShuffle.PACKAGE, SonaraShuffle.SERVICE)
        )
        val future = SessionController.Builder(context, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrElse {
                log("Sonara")
                return@addListener
            }
            sonaraController = controller
            driveSonara(controller, music)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun driveSonara(controller: SessionController, music: MusicAlarmSettings) {
        val names = controller.availableSessionCommands.commands
            .filter { it.commandCode == SessionCommand.COMMAND_CODE_CUSTOM }
            .map { it.customAction }
            .toSet()
        SonaraShuffle.steps(music.shuffle, names).forEach { step ->
            runCatching {
                when (step) {
                    SonaraShuffle.Step.PLAY_SHUFFLED -> {
                        val args = Bundle()
                        SonaraShuffle.playlistArgument(music.sonaraPlaylistId)?.let { id ->
                            args.putString(SonaraShuffle.PLAYLIST_ID, id)
                        }
                        controller.sendCustomCommand(
                            SessionCommand(SonaraShuffle.PLAY_SHUFFLED, Bundle()),
                            args
                        )
                    }
                    SonaraShuffle.Step.PLAY -> controller.play()
                }
            }.onFailure { log("Sonara") }
        }
    }

    private fun connect(context: Context, target: ShuffleTarget) {
        browser?.runCatching { disconnect() }
        browser = null
        val classes = MusicShufflePlan.serviceClasses(discover(context, target.packageName), target.serviceClasses)
        val components = classes.map { ComponentName(target.packageName, it) }
        if (components.isEmpty()) {
            log("kein MediaBrowser")
            return
        }
        openBrowser(context, components, 0)
    }

    private fun openBrowser(context: Context, components: List<ComponentName>, index: Int) {
        if (index >= components.size) return
        val component = components[index]
        lateinit var next: MediaBrowser
        next = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                runCatching {
                    val controller = MediaController(context, next.sessionToken)
                    shuffleAndPlay(controller.transportControls)
                }.onFailure { log("Steuerung") }
                next.runCatching { disconnect() }
                if (browser === next) browser = null
            }

            override fun onConnectionFailed() {
                log("Verbindung abgelehnt")
                next.runCatching { disconnect() }
                if (browser === next) browser = null
                openBrowser(context, components, index + 1)
            }

            override fun onConnectionSuspended() {
                next.runCatching { disconnect() }
                if (browser === next) browser = null
            }
        }, null)
        browser = next
        runCatching { next.connect() }.onFailure {
            log("Verbindung")
            next.runCatching { disconnect() }
            if (browser === next) browser = null
            openBrowser(context, components, index + 1)
        }
    }

    @Suppress("DEPRECATION")
    private fun discover(context: Context, packageName: String): List<String> {
        val probe = Intent(BROWSER).setPackage(packageName)
        return context.packageManager.queryIntentServices(probe, PackageManager.GET_META_DATA)
            .mapNotNull { it.serviceInfo?.name }
    }

    /**
     * Nur wenn diese App schon als Notification-Listener freigeschaltet ist.
     * Morgo legt keinen Listener an und fragt die Freigabe nicht an.
     */
    private fun activeSession(context: Context, target: ShuffleTarget) {
        val flat = Settings.Secure.getString(context.contentResolver, ENABLED_LISTENERS) ?: return
        val ours = flat.split(':').firstOrNull { it.startsWith(context.packageName + "/") } ?: return
        val listener = ComponentName.unflattenFromString(ours) ?: return
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return
        val controller = manager.getActiveSessions(listener)
            .firstOrNull { it.packageName == target.packageName }
            ?: return
        shuffleAndPlay(controller.transportControls)
    }

    /** setShuffleMode fehlt im vorhandenen SDK-Stub, ist auf dem Gerät aber seit API 24 da. */
    private fun shuffleAndPlay(controls: MediaController.TransportControls) {
        runCatching {
            val method = controls.javaClass.getMethod("setShuffleMode", Int::class.javaPrimitiveType)
            method.invoke(controls, SHUFFLE_MODE_ALL)
        }.onFailure { log("Steuerung") }
        controls.play()
    }

    private fun openDeepLinks(context: Context, target: ShuffleTarget) {
        val activity = findActivity(context) ?: return
        target.deepLinks.forEach { link ->
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).setPackage(target.packageName)
            runCatching { activity.startActivity(intent) }.onFailure { log("Deep-Link") }
        }
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun log(reason: String) {
        Log.w(TAG, "Zufallswiedergabe nicht gesetzt: $reason")
    }
}
