package io.qzz.xandll.morgo.data.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.receiver.AlarmActionReceiver
import io.qzz.xandll.morgo.domain.model.AlarmAlertSpec
import io.qzz.xandll.morgo.presentation.alarm.AlarmActivity

object AlarmAlerts {
    const val NOTIFICATION_ID = 2101
    const val EXTRA_TITLE = "alarm_title"
    const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

    fun notification(
        context: Context,
        spec: AlarmAlertSpec,
        kind: String,
        label: String,
        snoozeMinutes: Int,
        test: Boolean
    ): android.app.Notification {
        val open = activityIntent(context, kind, label, spec.title)
        val stopAction = action(
            context,
            if (test) WakeSoundService.ACTION_STOP else WakeSoundService.ACTION_AWAKE,
            if (test) "Stoppen" else AlarmAlertSpec.STOP,
            android.R.drawable.ic_menu_close_clear_cancel,
            kind,
            72,
            null
        )
        val actions = ArrayList<NotificationCompat.Action>()
        if (spec.showSnooze && !test) {
            actions += action(
                context,
                WakeSoundService.ACTION_SNOOZE,
                AlarmAlertSpec.SNOOZE,
                android.R.drawable.ic_lock_idle_alarm,
                kind,
                71,
                snoozeMinutes
            )
        }
        actions += stopAction
        val wearable = NotificationCompat.WearableExtender()
        actions.forEach { wearable.addAction(it) }
        val builder = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(spec.title)
            .setContentText(spec.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(spec.ongoing)
            .setContentIntent(open)
            .setAutoCancel(false)
            .extend(wearable)
        actions.forEach { builder.addAction(it) }
        if (spec.fullScreen && !test) {
            builder.setFullScreenIntent(open, true)
        }
        return builder.build()
    }

    fun turnScreenOn(context: Context) {
        val manager = context.getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val lock = manager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "morgo:alarm-screen"
        )
        lock.acquire(3_000)
    }

    private fun activityIntent(context: Context, kind: String, label: String, title: String): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
            putExtra(SecondaryAlarmScheduler.EXTRA_LABEL, label)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getActivity(
            context,
            73,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun action(
        context: Context,
        action: String,
        title: String,
        icon: Int,
        kind: String,
        requestCode: Int,
        snoozeMinutes: Int?
    ): NotificationCompat.Action {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
            if (snoozeMinutes != null) putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(icon, title, pending).build()
    }
}
