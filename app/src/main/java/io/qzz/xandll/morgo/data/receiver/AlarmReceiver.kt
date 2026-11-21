package io.qzz.xandll.morgo.data.receiver

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.service.AlarmAlerts
import io.qzz.xandll.morgo.data.service.SecondaryAlarmScheduler
import io.qzz.xandll.morgo.data.service.WakeSoundService
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.presentation.alarm.AlarmActivity

/**
 * BroadcastReceiver für Hauptwecker, Nachwecker und Extra-Wecker.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_KIND) ?: SecondaryAlarmScheduler.KIND_MAIN
        val label = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_LABEL).orEmpty()
        val withSound = intent.getBooleanExtra(SecondaryAlarmScheduler.EXTRA_SOUND, true)
        if (kind == SecondaryAlarmScheduler.KIND_EXTRA && !withSound) {
            notifyOnly(context, label)
            return
        }
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val inUse = AlarmFace.deviceInUse(
            interactive = power?.isInteractive == true,
            keyguardLocked = keyguard?.isKeyguardLocked == true
        )
        if (!inUse) {
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
                putExtra(SecondaryAlarmScheduler.EXTRA_LABEL, label)
            }
            context.startActivity(alarmIntent)
            AlarmAlerts.turnScreenOn(context)
        }
        WakeSoundService.start(context, kind, label)
    }

    private fun notifyOnly(context: Context, label: String) {
        val open = PendingIntent.getActivity(
            context,
            8,
            Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(SecondaryAlarmScheduler.EXTRA_KIND, SecondaryAlarmScheduler.KIND_EXTRA)
                putExtra(SecondaryAlarmScheduler.EXTRA_LABEL, label)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(label.ifBlank { "Extra-Wecker" })
            .setContentText("Erinnerung")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(label.hashCode(), notification)
    }
}
