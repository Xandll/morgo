package io.qzz.xandll.morgo.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.qzz.xandll.morgo.data.receiver.AlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton

/**
 * Service zur Verwaltung des AlarmManagers
 */
class AlarmSchedulerService(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Plant einen Wecker
     */
    fun scheduleAlarm(date: LocalDate, time: LocalTime) {
        val alarmDateTime = LocalDateTime.of(date, time)
        val alarmTimeMillis = alarmDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(alarmTimeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            // Fallback ohne Exact Alarm Permission
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTimeMillis, pendingIntent)
            } catch (e: Exception) {
                // Letzter Fallback
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTimeMillis, pendingIntent)
            }
        }
    }

    /**
     * Storniert den Wecker
     */
    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
    
    /**
     * Snooze: Wecker für X Minuten später planen
     */
    fun snoozeAlarm(snoozeMinutes: Int) {
        val snoozeTime = LocalDateTime.now().plusMinutes(snoozeMinutes.toLong())
        val snoozeTimeMillis = snoozeTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(snoozeTimeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, snoozeTimeMillis, pendingIntent)
            } catch (e: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, snoozeTimeMillis, pendingIntent)
            }
        }
    }

    /**
     * Prüft, ob ein Wecker geplant ist
     */
    fun hasScheduledAlarm(): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return pendingIntent != null
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1001
    }
}
