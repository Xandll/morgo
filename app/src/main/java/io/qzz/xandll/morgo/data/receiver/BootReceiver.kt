package io.qzz.xandll.morgo.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import io.qzz.xandll.morgo.data.update.UpdateScheduler
import io.qzz.xandll.morgo.data.worker.AlarmRefresh
import io.qzz.xandll.morgo.data.service.EveningCheckScheduler
import io.qzz.xandll.morgo.data.service.LeaveLightScheduler
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Plant Wecker und Abend-Check nach Boot, Zeitänderung und App-Update neu.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!relevant) return
        val entry = EntryPointAccessors.fromApplication(context, RescheduleEntryPoint::class.java)
        val settings = entry.settingsRepository().loadSettings()
        entry.eveningCheckScheduler().reschedule(settings)
        entry.leaveLightScheduler().rearm(settings)
        UpdateScheduler.schedule(context, settings.update.enabled)
        AlarmRefresh.enqueue(context)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RescheduleEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun eveningCheckScheduler(): EveningCheckScheduler
    fun leaveLightScheduler(): LeaveLightScheduler
}
