package io.qzz.xandll.morgo.data.service

import io.qzz.xandll.morgo.domain.model.AlarmPlan
import io.qzz.xandll.morgo.domain.model.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Hauptwecker, Licht, Nachwecker und Extra-Wecker bleiben an derselben Uhrzeit. */
@Singleton
class MorningAlarmSync @Inject constructor(
    private val alarms: AlarmSchedulerService,
    private val lights: PreWakeLightScheduler,
    private val secondary: SecondaryAlarmScheduler
) {
    fun apply(date: LocalDate, plan: AlarmPlan?, settings: AppSettings) {
        if (plan?.rings == true) {
            move(date, plan.alarmTime, settings, plan.daySubjects)
        } else {
            alarms.cancelAlarm()
            lights.cancel()
            secondary.cancelAll()
        }
    }

    fun move(date: LocalDate, wake: LocalTime, settings: AppSettings, subjects: List<String>) {
        alarms.scheduleAlarm(date, wake)
        lights.schedule(date, wake, settings.homeAssistant.preWake)
        secondary.schedule(date, wake, settings.alarm, subjects)
    }
}
