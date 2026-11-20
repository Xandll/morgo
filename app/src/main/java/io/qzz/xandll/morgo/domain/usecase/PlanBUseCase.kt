package io.qzz.xandll.morgo.domain.usecase

import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.abs

class PlanBUseCase @Inject constructor(
    private val repository: MorgoRepository
) {

    /**
     * Erste nicht ausgefallene Stunde als Stundennummer auflösen und den
     * bevorzugten Zug dieser Stunde prüfen.
     */
    suspend fun evaluateForFirstLesson(
        date: LocalDate,
        lessons: List<Lesson>,
        slots: List<LessonSlot>,
        settings: AppSettings
    ): Result<PlanBResult> {
        val firstLesson = LessonGrid.firstLesson(lessons, date)
            ?: return Result.success(createNoPlanBResult("Kein Unterricht"))
        val rules = LessonGrid.migrateRules(settings.alarm.wakeTimeRules, slots)
        val lessonNumber = LessonGrid.lessonNumber(firstLesson.startTime, date.dayOfWeek, slots)
            ?: return Result.success(createNoPlanBResult("Stunde nicht im Timegrid"))
        val preferred = rules.find { it.lessonNumber == lessonNumber }?.preferredTrainDeparture
            ?: return Result.success(createNoPlanBResult("Kein bevorzugter Zug"))
        return evaluatePlanB(
            preferredTrainDeparture = preferred,
            lessonStartTime = firstLesson.startTime,
            date = date,
            settings = settings
        )
    }

    suspend fun evaluatePlanB(
        preferredTrainDeparture: LocalTime,
        lessonStartTime: LocalTime,
        date: LocalDate,
        settings: AppSettings
    ): Result<PlanBResult> {
        return try {
            val startStation = settings.train.startStation
                ?: return Result.success(createNoPlanBResult("Keine Startstation"))
            
            val destinationStation = settings.train.destinationStation
                ?: return Result.success(createNoPlanBResult("Keine Zielstation"))

            val searchTime = preferredTrainDeparture.minusMinutes(30)
            val searchResult = repository.searchConnections(
                fromStation = startStation,
                toStation = destinationStation,
                date = date,
                time = searchTime
            ).getOrNull()
            
            if (searchResult == null || !searchResult.hasData) {
                return Result.success(createNoPlanBResult("Keine Verbindungen"))
            }
            
            val connections = searchResult.connections
            val preferredConnection = connections
                .filter { isCloseToTime(it.departure.plannedTime, preferredTrainDeparture, 5) }
                .firstOrNull()

            if (preferredConnection == null) {
                return Result.success(createNoPlanBResult("Bevorzugter Zug nicht gefunden"))
            }

            val requiredArrivalTime = lessonStartTime.minusMinutes(settings.train.walkingTimeMinutes.toLong())

            when (preferredConnection.status) {
                ConnectionStatus.ON_TIME -> {
                    return Result.success(PlanBResult(
                        needsPlanB = false,
                        reason = PlanBReason.ON_TIME,
                        alternativeConnection = null,
                        newWakeTime = null,
                        message = "Zug fährt pünktlich"
                    ))
                }
                
                ConnectionStatus.DELAYED -> {
                    val actualArrival = preferredConnection.arrival.actualTime 
                        ?: preferredConnection.arrival.plannedTime
                    
                    if (actualArrival.isBefore(requiredArrivalTime) || actualArrival == requiredArrivalTime) {
                        val delayMinutes = calculateDelay(preferredConnection)
                        return Result.success(PlanBResult(
                            needsPlanB = false,
                            reason = PlanBReason.DELAYED_OK,
                            alternativeConnection = null,
                            newWakeTime = null,
                            message = "Zug hat $delayMinutes Min Verspätung, kommt aber noch rechtzeitig an"
                        ))
                    }
                    
                    return findAlternative(
                        connections = connections,
                        requiredArrivalTime = requiredArrivalTime,
                        settings = settings,
                        reason = PlanBReason.DELAYED_TOO_LATE,
                        originalConnection = preferredConnection
                    )
                }
                
                ConnectionStatus.CANCELLED -> {
                    return findAlternative(
                        connections = connections,
                        requiredArrivalTime = requiredArrivalTime,
                        settings = settings,
                        reason = PlanBReason.CANCELLED,
                        originalConnection = preferredConnection
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findAlternative(
        connections: List<TrainConnection>,
        requiredArrivalTime: LocalTime,
        settings: AppSettings,
        reason: PlanBReason,
        originalConnection: TrainConnection
    ): Result<PlanBResult> {
        val alternatives = connections
            .filter { it.status != ConnectionStatus.CANCELLED }
            .filter { 
                val arrival = it.arrival.actualTime ?: it.arrival.plannedTime
                arrival.isBefore(requiredArrivalTime) || arrival == requiredArrivalTime
            }

        val bestAlternative = alternatives.maxByOrNull { it.departure.plannedTime }

        if (bestAlternative == null) {
            val bestLateConnection = connections
                .filter { it.status != ConnectionStatus.CANCELLED }
                .minByOrNull { 
                    val arrival = it.arrival.actualTime ?: it.arrival.plannedTime
                    abs(arrival.toSecondOfDay() - requiredArrivalTime.toSecondOfDay())
                }

            if (bestLateConnection != null) {
                val arrival = bestLateConnection.arrival.actualTime ?: bestLateConnection.arrival.plannedTime
                val delayMinutes = java.time.Duration.between(requiredArrivalTime, arrival).toMinutes()
                
                return Result.success(PlanBResult(
                    needsPlanB = true,
                    reason = PlanBReason.NO_ALTERNATIVE,
                    alternativeConnection = bestLateConnection,
                    newWakeTime = null,
                    message = "Keine Alternative. Beste Option: ${delayMinutes.coerceAtLeast(0)} Min zu spät"
                ))
            }

            return Result.success(PlanBResult(
                needsPlanB = true,
                reason = PlanBReason.NO_ALTERNATIVE,
                alternativeConnection = null,
                newWakeTime = null,
                message = "Keine Alternative gefunden"
            ))
        }

        val leadTimeMinutes = settings.train.preparationTimeMinutes.toLong()
        var newWakeTime = bestAlternative.departure.plannedTime.minusMinutes(leadTimeMinutes)

        val earliestAllowed = settings.alarm.earliestWakeTime
        val maxEarlierMinutes = settings.alarm.maxEarlierMinutes.toLong()
        val originalWakeTime = originalConnection.departure.plannedTime.minusMinutes(leadTimeMinutes)
        val earliestByMaxEarlier = originalWakeTime.minusMinutes(maxEarlierMinutes)

        if (newWakeTime.isBefore(earliestAllowed)) {
            newWakeTime = earliestAllowed
        }
        if (newWakeTime.isBefore(earliestByMaxEarlier)) {
            newWakeTime = earliestByMaxEarlier
        }

        val reasonText = when (reason) {
            PlanBReason.CANCELLED -> "ausgefallen"
            PlanBReason.DELAYED_TOO_LATE -> "zu spät"
            else -> "nicht verfügbar"
        }

        return Result.success(PlanBResult(
            needsPlanB = true,
            reason = reason,
            alternativeConnection = bestAlternative,
            newWakeTime = newWakeTime,
            message = "Bevorzugter Zug $reasonText. Alternative: ${formatTime(bestAlternative.departure.plannedTime)} Uhr, neue Weckzeit ${formatTime(newWakeTime)} Uhr"
        ))
    }

    private fun createNoPlanBResult(message: String): PlanBResult {
        return PlanBResult(
            needsPlanB = false,
            reason = PlanBReason.ON_TIME,
            alternativeConnection = null,
            newWakeTime = null,
            message = message
        )
    }

    private fun isCloseToTime(time: LocalTime, target: LocalTime, toleranceMinutes: Int): Boolean {
        val diffSeconds = abs(time.toSecondOfDay() - target.toSecondOfDay())
        return diffSeconds <= toleranceMinutes * 60
    }

    private fun calculateDelay(connection: TrainConnection): Long {
        val planned = connection.departure.plannedTime
        val actual = connection.departure.actualTime ?: return 0
        return java.time.Duration.between(planned, actual).toMinutes()
    }

    private fun formatTime(time: LocalTime): String {
        return String.format("%02d:%02d", time.hour, time.minute)
    }
}

data class PlanBResult(
    val needsPlanB: Boolean,
    val reason: PlanBReason,
    val alternativeConnection: TrainConnection?,
    val newWakeTime: LocalTime?,
    val message: String
)

enum class PlanBReason {
    ON_TIME,
    DELAYED_OK,
    DELAYED_TOO_LATE,
    CANCELLED,
    NO_ALTERNATIVE
}
