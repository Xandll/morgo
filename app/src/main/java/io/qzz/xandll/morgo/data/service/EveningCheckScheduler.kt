package io.qzz.xandll.morgo.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.qzz.xandll.morgo.data.receiver.EveningCheckReceiver
import io.qzz.xandll.morgo.domain.model.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class EveningCheckScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun reschedule(settings: AppSettings) {
        val pending = pendingIntent()
        if (!settings.alarm.eveningCheckEnabled) {
            alarmManager.cancel(pending)
            return
        }
        val trigger = nextTrigger(settings.alarm.eveningCheckTime)
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    fun nextTrigger(time: LocalTime, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        var candidate = LocalDate.now().atTime(time)
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, EveningCheckReceiver::class.java).apply {
            action = EveningCheckReceiver.ACTION_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE = 2002
    }
}
