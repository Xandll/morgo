package io.qzz.xandll.morgo.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.data.receiver.AlarmReceiver
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.ExtraAlarmPlan
import io.qzz.xandll.morgo.domain.model.FollowUpPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecondaryAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(date: LocalDate, wake: LocalTime, alarm: AlarmSettings, subjects: List<String>) {
        val wakeAt = LocalDateTime.of(date, wake)
        if (FollowUpPlan.shouldSchedule(rings = true, alarm)) {
            arm(
                FollowUpPlan.at(wakeAt, alarm.followUpMinutes),
                FOLLOW_UP_CODE,
                KIND_FOLLOW_UP,
                "Zweiter Wecker",
                withSound = true,
                alarmClock = true
            )
        } else {
            cancelFollowUp()
        }
        cancelExtras()
        val due = ExtraAlarmPlan.due(wakeAt, subjects, alarm.extraAlarms).take(MAX_EXTRAS)
        due.forEachIndexed { index, item ->
            arm(
                item.at,
                EXTRA_BASE + index,
                KIND_EXTRA,
                item.alarm.label,
                item.alarm.withSound,
                alarmClock = false
            )
        }
    }

    fun cancelAll() {
        cancelFollowUp()
        cancelExtras()
    }

    fun onAwakeConfirmed(alarm: AlarmSettings) {
        if (FollowUpPlan.cancelWhenConfirmedAwake(alarm)) {
            cancelFollowUp()
        }
    }

    fun cancelFollowUp() {
        cancel(FOLLOW_UP_CODE, KIND_FOLLOW_UP, "", true)
    }

    private fun cancelExtras() {
        repeat(MAX_EXTRAS) { index ->
            cancel(EXTRA_BASE + index, KIND_EXTRA, "", true)
        }
    }

    private fun arm(
        at: LocalDateTime,
        requestCode: Int,
        kind: String,
        label: String,
        withSound: Boolean,
        alarmClock: Boolean
    ) {
        val pending = pending(requestCode, kind, label, withSound)
        if (!at.isAfter(LocalDateTime.now())) {
            alarmManager.cancel(pending)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (alarmClock) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, pending), pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun cancel(requestCode: Int, kind: String, label: String, withSound: Boolean) {
        val pending = pending(requestCode, kind, label, withSound)
        alarmManager.cancel(pending)
    }

    private fun pending(requestCode: Int, kind: String, label: String, withSound: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_SOUND, withSound)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_KIND = "alarm_kind"
        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_SOUND = "alarm_sound"
        const val KIND_MAIN = "main"
        const val KIND_FOLLOW_UP = "follow_up"
        const val KIND_EXTRA = "extra"
        private const val FOLLOW_UP_CODE = 1002
        private const val EXTRA_BASE = 4100
        private const val MAX_EXTRAS = 16
    }
}
