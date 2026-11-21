package io.qzz.xandll.morgo.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.data.receiver.LeaveLightReceiver
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.LeaveLightPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Exakter Alarm zum Losgehen. Kein neues Recht, derselbe Weg wie das Licht vor dem Wecken. */
@Singleton
class LeaveLightScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences("morgo_prefs", Context.MODE_PRIVATE)

    fun schedule(date: LocalDate, departure: LocalTime, settings: AppSettings) {
        prefs.edit().putString(KEY_DEPARTURE, "$date|$departure").apply()
        val light = settings.homeAssistant.preWake
        if (!light.leaveOffEnabled) {
            cancelPending()
            return
        }
        val leave = LeaveLightPlan.leaveAt(
            date,
            departure,
            settings.train.walkingTimeMinutes,
            light.leaveOffOffsetMinutes
        )
        val trigger = LeaveLightPlan.triggerAt(LocalDateTime.now(), leave)
        if (trigger == null) {
            cancelPending()
            return
        }
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = pendingIntent()
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    /** Nach Reboot oder geänderter Einstellung, ohne den Zug neu zu suchen. */
    fun rearm(settings: AppSettings) {
        val stored = readDeparture()
        if (stored == null) {
            cancelPending()
            return
        }
        schedule(stored.first, stored.second, settings)
    }

    fun cancel() {
        prefs.edit().remove(KEY_DEPARTURE).apply()
        cancelPending()
    }

    private fun readDeparture(): Pair<LocalDate, LocalTime>? {
        val raw = prefs.getString(KEY_DEPARTURE, null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
        return date to time
    }

    private fun cancelPending() {
        val pending = pendingIntent()
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, LeaveLightReceiver::class.java).apply {
            action = LeaveLightReceiver.ACTION_OFF
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE = 3010
        private const val KEY_DEPARTURE = "leave_light_departure"
    }
}
