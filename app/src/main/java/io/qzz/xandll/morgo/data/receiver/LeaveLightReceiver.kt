package io.qzz.xandll.morgo.data.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.service.LeaveLightRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Schaltet das Licht zum Losgehen aus. Stopp am Wecker löst das nicht aus. */
@AndroidEntryPoint
class LeaveLightReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: LeaveLightRunner

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_OFF) return
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        val pending = goAsync()
        val appContext = context.applicationContext
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "morgo:leave-light")
        lock.setReferenceCounted(false)
        lock.acquire(30_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notify(appContext, runner.turnOff(test))
            } finally {
                if (lock.isHeld) lock.release()
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_LIGHT)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Licht beim Losgehen")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_OFF = "io.qzz.xandll.morgo.LEAVE_LIGHT_OFF"
        const val EXTRA_TEST = "leave_light_test"
        private const val NOTIFICATION_ID = 45
    }
}
