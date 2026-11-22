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
import io.qzz.xandll.morgo.data.service.PreWakeLightRunner
import io.qzz.xandll.morgo.data.service.PreWakeLightScheduler
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Licht-Schritt. Der nächste Schritt ist ein eigener exakter Alarm. */
@AndroidEntryPoint
class PreWakeLightReceiver : BroadcastReceiver() {

    @Inject lateinit var runner: PreWakeLightRunner
    @Inject lateinit var scheduler: PreWakeLightScheduler

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RUN) return
        val step = intent.getIntExtra(EXTRA_STEP, 0)
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        val pending = goAsync()
        val appContext = context.applicationContext
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "morgo:prewake-step")
        lock.setReferenceCounted(false)
        lock.acquire(30_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = runner.runStep(step)
                notify(appContext, outcome)
                val delay = outcome.nextDelay?.let { if (test) PreWakeLightPlan.testDelay(it) else it }
                if (delay != null) {
                    scheduler.scheduleStep(
                        System.currentTimeMillis() + delay.toMillis(),
                        step + 1,
                        test
                    )
                }
            } finally {
                if (lock.isHeld) lock.release()
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, outcome: io.qzz.xandll.morgo.data.service.LightStepResult) {
        val text = buildString {
            append(outcome.progress ?: outcome.message)
            outcome.errors.forEach { append('\n').append(it) }
        }
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_LIGHT)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Licht vor dem Wecken")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_RUN = "io.qzz.xandll.morgo.PREWAKE_LIGHT"
        const val EXTRA_STEP = "prewake_step"
        const val EXTRA_TEST = "prewake_test"
        private const val NOTIFICATION_ID = 43
    }
}
