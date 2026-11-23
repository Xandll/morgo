package io.qzz.xandll.morgo.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Wie ein Eintrag aus „Licht vor dem Wecken“ beim Losgehen ausgeht.
 * [calls] gilt für Skripte ohne eigene Aus-Entity. [offEntityId] ersetzt den Aufruf.
 */
data class LeaveOffChoice(
    val calls: Int = 1,
    val offEntityId: String = ""
) {
    fun normalized(): LeaveOffChoice {
        return copy(
            calls = calls.coerceIn(1, 10),
            offEntityId = offEntityId.trim()
        )
    }
}

data class LeaveOffAction(
    val entityId: String,
    val times: Int
)

/**
 * Licht aus zum Losgehen: Abfahrt minus Fußweg, plus Versatz.
 * Die Abfahrt ist die aktualisierte Uhrzeit (Verspätung oder Plan B).
 */
object LeaveLightPlan {

    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun departureOf(connection: TrainConnection): LocalTime {
        return connection.departure.actualTime ?: connection.departure.plannedTime
    }

    /**
     * Welcher Zug zählt. Plan B ersetzt den bisherigen Zug.
     * Fällt er weg und es gibt keinen Ersatz, ist kein Zug bekannt.
     */
    fun chosenDeparture(
        rings: Boolean,
        connection: TrainConnection?,
        planBConnection: TrainConnection?,
        planBDroppedTrain: Boolean
    ): LocalTime? {
        if (!rings || planBDroppedTrain) return null
        val chosen = planBConnection ?: connection
        if (chosen == null || chosen.status == ConnectionStatus.CANCELLED) return null
        return departureOf(chosen)
    }

    /** Abfahrt minus Fußweg, danach der Versatz. Ein positiver Versatz ist später. */
    fun leaveAt(
        date: LocalDate,
        departure: LocalTime,
        walkingMinutes: Int,
        offsetMinutes: Int
    ): LocalDateTime {
        return LocalDateTime.of(date, departure)
            .minusMinutes(walkingMinutes.coerceAtLeast(0).toLong())
            .plusMinutes(offsetMinutes.toLong())
    }

    fun triggerAt(now: LocalDateTime, leave: LocalDateTime): LocalDateTime? {
        return if (leave.isAfter(now)) leave else null
    }

    fun hint(leave: LocalTime): String = "Licht aus um ${leave.format(clock)}"

    fun hintAfterWake(leave: LocalDateTime, wake: LocalTime): String? =
        if (leave.toLocalTime().isAfter(wake)) hint(leave.toLocalTime()) else null

    /**
     * Stopp am Wecker schaltet das Licht nicht aus.
     * Ohne bekannte Abfahrt gibt es auch keinen Ausschalt-Alarm.
     */
    fun turnOffBecauseAlarmStopped(departureKnown: Boolean): Boolean = false

    fun actions(entityIds: List<String>, choices: Map<String, LeaveOffChoice>): List<LeaveOffAction> {
        return entityIds.mapNotNull { id ->
            val choice = (choices[id] ?: LeaveOffChoice()).normalized()
            when (HomeAssistantTargets.domain(id)) {
                "light", "switch", "input_boolean" -> LeaveOffAction(id, 1)
                "scene" -> offEntityAction(choice)
                "script" -> {
                    val off = offEntityAction(choice)
                    off ?: LeaveOffAction(id, choice.calls)
                }
                else -> null
            }
        }
    }

    /** Skripte und Szenen kennen nur turn_on. Alles andere geht mit turn_off aus. */
    fun service(entityId: String): HaServiceKind {
        return when (HomeAssistantTargets.domain(entityId)) {
            "script", "scene" -> HaServiceKind.TURN_ON
            else -> HaServiceKind.TURN_OFF
        }
    }

    fun encode(choices: Map<String, LeaveOffChoice>): String {
        return choices.entries.joinToString("\n") { (id, choice) ->
            val normalized = choice.normalized()
            "$id\t${normalized.calls}\t${normalized.offEntityId}"
        }
    }

    fun decode(raw: String?): Map<String, LeaveOffChoice> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.trim().orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            id to LeaveOffChoice(
                calls = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                offEntityId = parts.getOrNull(2).orEmpty()
            ).normalized()
        }.toMap()
    }

    private fun offEntityAction(choice: LeaveOffChoice): LeaveOffAction? {
        val off = choice.offEntityId
        if (off.isBlank() || !HomeAssistantTargets.isSelectable(off)) return null
        return LeaveOffAction(off, 1)
    }
}
