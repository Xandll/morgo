package io.qzz.xandll.morgo.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.data.receiver.PreWakeLightReceiver
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreWakeLightScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences("morgo_prefs", Context.MODE_PRIVATE)

    fun schedule(date: LocalDate, wake: LocalTime, light: PreWakeLightSettings) {
        prefs.edit().putString(KEY_WAKE, "$date|$wake").apply()
        if (!light.isActive()) {
            cancelPending()
            return
        }
        val trigger = PreWakeLightPlan.triggerAt(
            LocalDateTime.now(),
            LocalDateTime.of(date, wake),
            light.leadMinutes
        )
        if (trigger == null) {
            cancelPending()
            return
        }
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        cancelChain(test = false)
        arm(millis, step = 0, test = false, requestCode = REQUEST_CODE)
    }

    /** Folgeschritt nach dem Einschalten. Liegt nicht auf dem ersten Alarm. */
    fun scheduleStep(triggerAtMillis: Long, step: Int, test: Boolean) {
        val code = if (test) TEST_CODE else CHAIN_CODE
        arm(triggerAtMillis, step, test, code)
    }

    fun cancel() {
        prefs.edit().remove(KEY_WAKE).apply()
        cancelChain(test = false)
    }

    /** Lead, Toggles oder die Lampen haben sich geändert, die Weckzeit bleibt. */
    fun rescheduleStored(light: PreWakeLightSettings) {
        val stored = readWake()
        if (stored == null) {
            cancelPending()
            return
        }
        schedule(stored.first, stored.second, light)
    }

    private fun readWake(): Pair<LocalDate, LocalTime>? {
        val raw = prefs.getString(KEY_WAKE, null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val time = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return null
        return date to time
    }

    private fun cancelPending() {
        cancelChain(test = false)
    }

    private fun cancelChain(test: Boolean) {
        listOf(REQUEST_CODE, CHAIN_CODE).forEach { code ->
            val pending = pendingIntent(0, test = false, code)
            alarmManager.cancel(pending)
            pending.cancel()
        }
        if (test) {
            val pending = pendingIntent(0, test = true, TEST_CODE)
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    private fun arm(triggerAtMillis: Long, step: Int, test: Boolean, requestCode: Int) {
        val pending = pendingIntent(step, test, requestCode)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun pendingIntent(step: Int, test: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(context, PreWakeLightReceiver::class.java).apply {
            action = PreWakeLightReceiver.ACTION_RUN
            putExtra(PreWakeLightReceiver.EXTRA_STEP, step)
            putExtra(PreWakeLightReceiver.EXTRA_TEST, test)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE = 3003
        private const val CHAIN_CODE = 3004
        private const val TEST_CODE = 3005
        private const val KEY_WAKE = "prewake_scheduled_wake"
    }
}
