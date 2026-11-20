package io.qzz.xandll.morgo.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import io.qzz.xandll.morgo.data.service.MorningAlarmSync
import java.time.LocalDate

/**
 * WorkManager Worker zur Neuplanung des Weckers
 */
@HiltWorker
class AlarmSchedulerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val calculateAlarmTimeUseCase: CalculateAlarmTimeUseCase,
    private val morningAlarmSync: MorningAlarmSync
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.loadSettings()
            val tomorrow = LocalDate.now().plusDays(1)
            
            val alarmPlan = calculateAlarmTimeUseCase.execute(
                date = tomorrow,
                settings = settings
            ).getOrNull()

            morningAlarmSync.apply(tomorrow, alarmPlan, settings)

            if (alarmPlan != null && alarmPlan.rings) {
                
                // Train Monitor Worker planen (morgens ab 5 Uhr alle 15 Minuten)
                val now = java.time.LocalTime.now()
                if (now.isAfter(java.time.LocalTime.of(5, 0)) && 
                    now.isBefore(alarmPlan.alarmTime.minusMinutes(30))) {
                    scheduleTrainMonitoring()
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private fun scheduleTrainMonitoring() {
        val monitorRequest = PeriodicWorkRequestBuilder<TrainMonitorWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "train_monitor",
            ExistingPeriodicWorkPolicy.REPLACE,
            monitorRequest
        )
    }
}
