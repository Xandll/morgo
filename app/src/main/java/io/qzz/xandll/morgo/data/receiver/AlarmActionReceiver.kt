package io.qzz.xandll.morgo.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.qzz.xandll.morgo.data.service.WakeSoundService

/** Stopp und Schlummern vom Handy und von der Uhr. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forward = Intent(context, WakeSoundService::class.java).apply {
            action = intent.action
            intent.extras?.let { putExtras(it) }
        }
        context.startForegroundService(forward)
    }
}
