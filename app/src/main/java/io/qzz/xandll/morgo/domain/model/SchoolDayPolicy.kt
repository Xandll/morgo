package io.qzz.xandll.morgo.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class SchoolHoliday(
    val name: String,
    val start: LocalDate,
    val end: LocalDate
) {
    fun covers(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Wann an einem Tag kein Wecker gestellt wird.
 * Leerer Stundenplan an Samstag oder Sonntag ist Wochenende, sonst kein Unterricht.
 */
object SchoolDayPolicy {
    fun absenceReason(
        date: LocalDate,
        lessons: List<Lesson>,
        holidayName: String?
    ): String? {
        if (!holidayName.isNullOrBlank()) {
            val kind = if (holidayName.contains("feiertag", ignoreCase = true)) "Feiertag" else "Ferien"
            return "$kind: $holidayName"
        }
        val sameDay = lessons.filter { it.date == date }
        val active = sameDay.filter { it.status != LessonStatus.CANCELLED }
        if (active.isNotEmpty()) return null
        if (sameDay.isNotEmpty()) return "Alle Stunden entfallen"
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "Wochenende"
            else -> "Kein Unterricht"
        }
    }
}

object EveningMessage {
    fun title(plan: AlarmPlan): String {
        return if (plan.rings) {
            "Morgen läutet dein Wecker um ${format(plan.alarmTime)}"
        } else {
            "Morgen läutet kein Wecker"
        }
    }

    fun body(plan: AlarmPlan, extras: List<ExtraAlarm> = emptyList()): String {
        if (!plan.rings) {
            return if (plan.usedCache) {
                "${plan.reason}. Stundenplan aus dem Zwischenspeicher."
            } else {
                plan.reason
            }
        }
        val parts = mutableListOf<String>()
        val lesson = plan.firstLesson
        if (plan.lessonNumber != null && lesson != null) {
            parts += "${plan.lessonNumber}. Stunde ${format(lesson.startTime)}"
        } else if (lesson != null) {
            parts += "Erste Stunde ${format(lesson.startTime)}"
        }
        plan.trainConnection?.let { train ->
            parts += "Zug ${format(train.departure.plannedTime)} ab ${train.departure.station}"
        }
        var text = parts.joinToString(" · ").ifBlank { plan.reason }
        if (plan.usedCache) {
            text += ". WebUntis war nicht erreichbar, Stundenplan aus dem Zwischenspeicher."
        }
        if (plan.uncertain) {
            text += ". WebUntis war nicht erreichbar, der Wecker bleibt sicherheitshalber an."
        }
        val extraLines = ExtraAlarmPlan.lines(plan.alarmTime, plan.daySubjects, extras)
        if (extraLines.isNotEmpty()) {
            text += ". Extra: " + extraLines.joinToString(", ")
        }
        return text
    }

    private fun format(time: LocalTime): String = String.format("%02d:%02d", time.hour, time.minute)
}

object SessionErrors {
    fun isSession(error: Throwable?): Boolean {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        return text.contains("not authenticated", ignoreCase = true) ||
            text.contains("Nicht authentifiziert", ignoreCase = true) ||
            text.contains("-8520") ||
            text.contains("session", ignoreCase = true) && text.contains("expired", ignoreCase = true)
    }
}
