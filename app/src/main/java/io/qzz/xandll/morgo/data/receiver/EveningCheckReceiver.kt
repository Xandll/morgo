package io.qzz.xandll.morgo.data.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.EveningCheckScheduler
import io.qzz.xandll.morgo.domain.model.EveningMessage
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import io.qzz.xandll.morgo.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class EveningCheckReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var calculateAlarmTimeUseCase: CalculateAlarmTimeUseCase
    @Inject lateinit var scheduler: EveningCheckScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CONFIRM) {
            val tomorrow = LocalDate.now().plusDays(1)
            regularConfirm(context, tomorrow)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.loadSettings()
                if (!settings.alarm.eveningCheckEnabled) return@launch
                val tomorrow = LocalDate.now().plusDays(1)
                val plan = calculateAlarmTimeUseCase.execute(tomorrow, settings).getOrNull()
                if (plan != null) {
                    post(
                        context,
                        EveningMessage.title(plan),
                        EveningMessage.body(plan, settings.alarm.extraAlarms)
                    )
                } else {
                    post(context, "Morgen läutet kein Wecker", "Wecker ist ausgeschaltet")
                }
                scheduler.reschedule(settings)
            } finally {
                pending.finish()
            }
        }
    }

    private fun regularConfirm(context: Context, date: LocalDate) {
        val prefs = context.getSharedPreferences("morgo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIRMED, date.toString()).apply()
    }

    private fun post(context: Context, title: String, body: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_OVERRIDE, true)
        }
        val content = PendingIntent.getActivity(
            context,
            1,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val confirm = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, EveningCheckReceiver::class.java).apply { action = ACTION_CONFIRM },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val change = PendingIntent.getActivity(
            context,
            3,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, MorgoApplication.CHANNEL_EVENING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(content)
            .setAutoCancel(true)
            .addAction(0, "Passt", confirm)
            .addAction(0, "Ändern", change)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CHECK = "io.qzz.xandll.morgo.EVENING_CHECK"
        const val ACTION_CONFIRM = "io.qzz.xandll.morgo.EVENING_CONFIRM"
        const val KEY_CONFIRMED = "evening_confirmed_date"
        private const val NOTIFICATION_ID = 42
    }
}
