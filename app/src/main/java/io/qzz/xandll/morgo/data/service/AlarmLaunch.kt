package io.qzz.xandll.morgo.data.service

import android.content.Context
import android.content.Intent
import io.qzz.xandll.morgo.presentation.alarm.AlarmActivity

/**
 * Der Test-Button läuft, während Morgo vorn ist. Dann öffnet die Activity direkt.
 * Liegt die App nicht vorn, bleibt es beim Full-Screen-Intent der Benachrichtigung.
 */
object AlarmLaunch {
    const val EXTRA_TEST = "alarm_test"

    fun openActivityDirectly(appInForeground: Boolean): Boolean = appInForeground

    fun testIntent(context: Context): Intent {
        return Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, SecondaryAlarmScheduler.KIND_MAIN)
            putExtra(EXTRA_TEST, true)
        }
    }
}
