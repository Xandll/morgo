package io.qzz.xandll.morgo.data.service

import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.model.AlarmPlan
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.ConnectionStatus
import io.qzz.xandll.morgo.domain.model.LeaveLightPlan
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Hauptwecker, Licht, Nachwecker und Extra-Wecker bleiben an derselben Uhrzeit. */
@Singleton
class MorningAlarmSync @Inject constructor(
    private val alarms: AlarmSchedulerService,
    private val lights: PreWakeLightScheduler,
    private val secondary: SecondaryAlarmScheduler,
    private val leaveLights: LeaveLightScheduler,
    private val settingsRepository: SettingsRepository
) {
    fun apply(date: LocalDate, plan: AlarmPlan?, settings: AppSettings) {
        if (plan?.rings == true) {
            move(date, plan.alarmTime, settings, plan.daySubjects, AlarmFace.DEFAULT_TITLE)
            val train = plan.trainConnection?.takeIf { it.status != ConnectionStatus.CANCELLED }
            val departure = train?.let { LeaveLightPlan.departureOf(it) }
            if (departure != null) {
                leaveLights.schedule(date, departure, settings)
            } else {
                leaveLights.cancel()
            }
        } else {
            settingsRepository.setAlarmTitle(AlarmFace.DEFAULT_TITLE)
            alarms.cancelAlarm()
            lights.cancel()
            secondary.cancelAll()
            leaveLights.cancel()
        }
    }

    fun move(
        date: LocalDate,
        wake: LocalTime,
        settings: AppSettings,
        subjects: List<String>,
        title: String = AlarmFace.DEFAULT_TITLE
    ) {
        settingsRepository.setAlarmTitle(title)
        alarms.scheduleAlarm(date, wake)
        lights.schedule(date, wake, settings.homeAssistant.preWake)
        secondary.schedule(date, wake, settings.alarm, subjects)
    }
}
