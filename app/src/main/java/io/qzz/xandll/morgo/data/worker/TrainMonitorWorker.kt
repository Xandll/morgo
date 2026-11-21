package io.qzz.xandll.morgo.data.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.R
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.LeaveLightScheduler
import io.qzz.xandll.morgo.data.service.MorningAlarmSync
import io.qzz.xandll.morgo.domain.model.LeaveLightPlan
import io.qzz.xandll.morgo.domain.model.LessonGrid
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import io.qzz.xandll.morgo.domain.usecase.PlanBResult
import java.time.LocalDate
import java.time.LocalTime

/**
 * WorkManager Worker zur Überwachung der Zugverbindung
 * Prüft morgens periodisch, ob die geplante Verbindung pünktlich ist
 */
@HiltWorker
class TrainMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val repository: MorgoRepository,
    private val morningAlarmSync: MorningAlarmSync,
    private val calculateAlarmTimeUseCase: CalculateAlarmTimeUseCase,
    private val planBUseCase: io.qzz.xandll.morgo.domain.usecase.PlanBUseCase,
    private val leaveLights: LeaveLightScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.loadSettings()
            val leaveEnabled = settings.homeAssistant.preWake.leaveOffEnabled

            if (!settings.alarm.planBEnabled && !leaveEnabled) {
                return Result.success()
            }
            
            val tomorrow = LocalDate.now().plusDays(1)
            val override = settings.alarm.overrideFor(tomorrow)
            if (override?.mode == io.qzz.xandll.morgo.domain.model.DayOverrideMode.OFF ||
                override?.mode == io.qzz.xandll.morgo.domain.model.DayOverrideMode.CUSTOM
            ) {
                return Result.success()
            }
            
            val lessons = repository.getTimetable(
                settings = settings.webUntis,
                startDate = tomorrow,
                endDate = tomorrow
            ).getOrNull() ?: emptyList()

            val slots = repository.loadTimegrid(settings.webUntis)
                .getOrNull()
                ?.let { LessonGrid.fromTimegrids(it) }
                .orEmpty()

            val planBResult = if (settings.alarm.planBEnabled) {
                planBUseCase.evaluateForFirstLesson(
                    date = tomorrow,
                    lessons = lessons,
                    slots = slots,
                    settings = settings
                ).getOrNull()
            } else {
                null
            }

            if (planBResult != null && planBResult.needsPlanB) {
                sendNotification(
                    "Plan B aktiviert",
                    planBResult.message
                )

                val newWakeTime = planBResult.newWakeTime
                if (newWakeTime != null) {
                    val subjects = io.qzz.xandll.morgo.domain.model.ExtraAlarmPlan.activeSubjects(lessons, tomorrow)
                    morningAlarmSync.move(tomorrow, newWakeTime, settings, subjects, title = "Plan B")
                    val firstLesson = LessonGrid.firstLesson(lessons, tomorrow)
                    val lessonNumber = firstLesson?.let {
                        LessonGrid.lessonNumber(it.startTime, tomorrow.dayOfWeek, slots)
                    }
                    val oldWakeTime = lessonNumber?.let { number ->
                        LessonGrid.migrateRules(settings.alarm.wakeTimeRules, slots)
                            .find { it.lessonNumber == number }
                            ?.wakeTime
                    }
                    val text = if (oldWakeTime != null) {
                        "Alt: ${formatTime(oldWakeTime)} → Neu: ${formatTime(newWakeTime)}"
                    } else {
                        "Neue Weckzeit: ${formatTime(newWakeTime)}"
                    }
                    sendNotification("Wecker angepasst", text)
                }

                if (planBResult.reason == io.qzz.xandll.morgo.domain.usecase.PlanBReason.CANCELLED) {
                    executeHACancellationAction(settings)
                }
            }

            if (leaveEnabled) {
                refreshLeaveLights(tomorrow, settings, planBResult)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private suspend fun refreshLeaveLights(
        date: LocalDate,
        settings: io.qzz.xandll.morgo.domain.model.AppSettings,
        planBResult: PlanBResult?
    ) {
        val fresh = calculateAlarmTimeUseCase.execute(date, settings).getOrNull()
        val dropped = planBResult?.needsPlanB == true && planBResult.departureConnection == null
        val departure = LeaveLightPlan.chosenDeparture(
            rings = fresh?.rings == true,
            connection = fresh?.trainConnection,
            planBConnection = planBResult?.departureConnection,
            planBDroppedTrain = dropped
        )
        if (departure != null) {
            leaveLights.schedule(date, departure, settings)
        } else {
            leaveLights.cancel()
        }
    }

    private suspend fun executeHACancellationAction(settings: io.qzz.xandll.morgo.domain.model.AppSettings) {
        // Placeholder für HA-Aktion bei Zugausfall
        // Kann später mit spezifischer Entity-ID erweitert werden
    }
    
    private fun formatTime(time: LocalTime): String {
        return String.format("%02d:%02d", time.hour, time.minute)
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) 
            as NotificationManager

        val notification = NotificationCompat.Builder(
            applicationContext,
            MorgoApplication.CHANNEL_TRAIN
        )
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
