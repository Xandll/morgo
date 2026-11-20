package io.qzz.xandll.morgo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.EveningCheckScheduler
import io.qzz.xandll.morgo.data.update.UpdateScheduler
import javax.inject.Inject

@HiltAndroidApp
class MorgoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var eveningCheckScheduler: EveningCheckScheduler

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        val settings = settingsRepository.loadSettings()
        eveningCheckScheduler.reschedule(settings)
        UpdateScheduler.schedule(this, settings.update.enabled)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                getString(R.string.notification_channel_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alarm_desc)
                enableVibration(true)
                setBypassDnd(true)
            }

            val trainChannel = NotificationChannel(
                CHANNEL_TRAIN,
                getString(R.string.notification_channel_train),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_train_desc)
            }

            val eveningChannel = NotificationChannel(
                CHANNEL_EVENING,
                "Abend-Check",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Abends die Weckzeit für den nächsten Morgen"
            }

            val lightChannel = NotificationChannel(
                CHANNEL_LIGHT,
                "Licht vor dem Wecken",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kurze Lichtsequenz vor dem Wecker"
            }

            val updateChannel = NotificationChannel(
                CHANNEL_UPDATE,
                "Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Neue Morgo-Versionen von GitHub"
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(trainChannel)
            notificationManager.createNotificationChannel(eveningChannel)
            notificationManager.createNotificationChannel(lightChannel)
            notificationManager.createNotificationChannel(updateChannel)
        }
    }

    companion object {
        const val CHANNEL_ALARM = "alarm_channel"
        const val CHANNEL_TRAIN = "train_channel"
        const val CHANNEL_EVENING = "evening_check"
        const val CHANNEL_LIGHT = "prewake_light"
        const val CHANNEL_UPDATE = "app_update"
    }
}
