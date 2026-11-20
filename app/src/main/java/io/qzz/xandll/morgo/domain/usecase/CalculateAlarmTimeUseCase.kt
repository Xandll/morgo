package io.qzz.xandll.morgo.domain.usecase

import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.*
import java.time.LocalDate
import java.time.LocalTime

/**
 * UseCase zur Berechnung der optimalen Weckzeit
 *
 * Logik:
 * 1. Erste Stunde des Tages aus Stundenplan ermitteln (außer ausgefallen)
 * 2. Stundennummer über das Timegrid des Wochentags
 * 3. Gesetzte Weckzeit dieser Stunde verwenden
 * 4. Sonst Zugverbindung, Weckzeit = Abfahrt minus Vorbereitung
 *    bzw. Stunde minus Wegzeit minus Vorbereitung
 */
class CalculateAlarmTimeUseCase(
    private val repository: MorgoRepository
) {

    /**
     * Berechnet den Weck-Plan für einen Tag
     */
    suspend fun execute(
        date: LocalDate,
        settings: AppSettings
    ): Result<AlarmPlan?> {
        return try {
            val override = settings.alarm.overrideFor(date)
            if (!settings.alarm.enabled && override?.mode != DayOverrideMode.FORCE_ON && override?.mode != DayOverrideMode.CUSTOM) {
                return Result.success(null)
            }
            if (override?.mode == DayOverrideMode.OFF) {
                return Result.success(
                    AlarmPlan(
                        alarmTime = settings.alarm.defaultWakeTime,
                        reason = "Für morgen ausgeschaltet",
                        firstLesson = null,
                        trainConnection = null,
                        rings = false
                    )
                )
            }

            val holiday = repository.getHolidays(settings.webUntis).getOrNull()
                ?.firstOrNull { it.covers(date) }
            val timetable = repository.getTimetable(
                settings = settings.webUntis,
                startDate = date,
                endDate = date
            )
            val usedCache: Boolean
            val uncertain: Boolean
            val lessons: List<Lesson>
            if (timetable.isSuccess) {
                lessons = timetable.getOrThrow()
                repository.saveLessonCache(date, lessons)
                usedCache = false
                uncertain = false
            } else {
                val cached = repository.readLessonCache(date)
                if (cached != null) {
                    lessons = cached
                    usedCache = true
                    uncertain = false
                } else {
                    lessons = emptyList()
                    usedCache = false
                    uncertain = true
                }
            }

            val absence = SchoolDayPolicy.absenceReason(date, lessons, holiday?.name)
            if (absence != null && override?.mode != DayOverrideMode.FORCE_ON && override?.mode != DayOverrideMode.CUSTOM) {
                if (uncertain) {
                    return Result.success(
                        AlarmPlan(
                            alarmTime = settings.alarm.defaultWakeTime,
                            reason = "WebUntis nicht erreichbar",
                            firstLesson = null,
                            trainConnection = null,
                            rings = true,
                            uncertain = true
                        )
                    )
                }
                return Result.success(
                    AlarmPlan(
                        alarmTime = settings.alarm.defaultWakeTime,
                        reason = absence,
                        firstLesson = null,
                        trainConnection = null,
                        rings = false,
                        usedCache = usedCache
                    )
                )
            }

            if (override?.mode == DayOverrideMode.CUSTOM && override.wakeTime != null) {
                val first = LessonGrid.firstLesson(lessons, date)
                return Result.success(
                    AlarmPlan(
                        alarmTime = override.wakeTime,
                        reason = "Nur für morgen",
                        firstLesson = first,
                        trainConnection = null,
                        rings = true,
                        usedCache = usedCache,
                        lessonNumber = lessonNumberOf(first, date, settings),
                        daySubjects = ExtraAlarmPlan.activeSubjects(lessons, date)
                    )
                )
            }

            if (override?.mode == DayOverrideMode.FORCE_ON && absence != null) {
                return Result.success(
                    AlarmPlan(
                        alarmTime = settings.alarm.defaultWakeTime,
                        reason = "Trotzdem eingeschaltet",
                        firstLesson = null,
                        trainConnection = null,
                        rings = true,
                        usedCache = usedCache
                    )
                )
            }

            val firstLesson = LessonGrid.firstLesson(lessons, date)
            if (firstLesson == null) {
                return Result.success(
                    AlarmPlan(
                        alarmTime = settings.alarm.defaultWakeTime,
                        reason = absence ?: "Kein Unterricht",
                        firstLesson = null,
                        trainConnection = null,
                        rings = false,
                        usedCache = usedCache
                    )
                )
            }

            val slots = loadSlots(settings)
            val rules = LessonGrid.migrateRules(settings.alarm.wakeTimeRules, slots)
            val lessonNumber = LessonGrid.lessonNumber(firstLesson.startTime, date.dayOfWeek, slots)
            val explicitWake = lessonNumber?.let { number ->
                rules.find { it.lessonNumber == number }?.wakeTime
            }

            val requiredArrivalTime = firstLesson.startTime
                .minusMinutes(settings.train.walkingTimeMinutes.toLong())

            val trainConnection = findSuitableConnection(
                date = date,
                requiredArrivalTime = requiredArrivalTime,
                settings = settings
            )

            val preparation = settings.train.preparationTimeMinutes.toLong()
            val standardAlarm = if (trainConnection != null) {
                trainConnection.departure.plannedTime.minusMinutes(preparation)
            } else {
                firstLesson.startTime
                    .minusMinutes(settings.train.walkingTimeMinutes.toLong())
                    .minusMinutes(preparation)
            }
            val standardReason = if (trainConnection != null) {
                "Regulärer Schultag"
            } else {
                "Keine passende Zugverbindung gefunden"
            }

            Result.success(
                AlarmPlan(
                    alarmTime = explicitWake ?: standardAlarm,
                    reason = if (explicitWake != null) {
                        "Weckzeit für die ${lessonNumber}. Stunde"
                    } else {
                        standardReason
                    },
                    firstLesson = firstLesson,
                    trainConnection = trainConnection,
                    rings = true,
                    usedCache = usedCache,
                    uncertain = uncertain,
                    lessonNumber = lessonNumber,
                    daySubjects = ExtraAlarmPlan.activeSubjects(lessons, date)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun lessonNumberOf(lesson: Lesson?, date: LocalDate, settings: AppSettings): Int? {
        if (lesson == null) return null
        val slots = loadSlots(settings)
        return LessonGrid.lessonNumber(lesson.startTime, date.dayOfWeek, slots)
    }

    private suspend fun loadSlots(settings: AppSettings): List<LessonSlot> {
        val webUntis = settings.webUntis
        if (webUntis.server.isBlank() || webUntis.schoolName.isBlank()) return emptyList()
        val grids = repository.loadTimegrid(webUntis).getOrNull().orEmpty()
        return LessonGrid.fromTimegrids(grids)
    }

    /**
     * Findet eine passende Zugverbindung
     */
    private suspend fun findSuitableConnection(
        date: LocalDate,
        requiredArrivalTime: LocalTime,
        settings: AppSettings
    ): TrainConnection? {
        val startStation = settings.train.startStation ?: return null
        val destinationStation = settings.train.destinationStation ?: return null

        val searchTime = requiredArrivalTime.minusHours(2)

        val searchResult = repository.searchConnections(
            fromStation = startStation,
            toStation = destinationStation,
            date = date,
            time = searchTime
        ).getOrNull() ?: return null

        return searchResult.connections
            .filter { it.status != ConnectionStatus.CANCELLED }
            .filter { 
                val arrivalTime = it.arrival.actualTime ?: it.arrival.plannedTime
                arrivalTime.isBefore(requiredArrivalTime) || arrivalTime == requiredArrivalTime
            }
            .maxByOrNull { 
                it.arrival.actualTime ?: it.arrival.plannedTime
            }
    }
}
