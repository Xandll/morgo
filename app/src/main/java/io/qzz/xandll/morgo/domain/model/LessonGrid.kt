package io.qzz.xandll.morgo.domain.model

import io.qzz.xandll.morgo.data.model.WebUntisTimeUnit
import io.qzz.xandll.morgo.data.model.WebUntisTimegrid
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Eine Unterrichtsstunde im Timegrid.
 * [number] ist der 1-basierte Index nach Sortierung der Timeunits nach Beginn.
 */
data class LessonSlot(
    val number: Int,
    val startByDay: Map<DayOfWeek, LocalTime>
) {
    fun startDiffersByDay(): Boolean = startByDay.values.distinct().size > 1
}

/**
 * Timegrid, Stundennummer und Migration alter uhrzeitbasierter Weckregeln.
 *
 * WebUntis zählt Tage wie java.util.Calendar (1 = Sonntag … 7 = Samstag).
 * Liefert eine Schule nur die Tage 1–5, gilt 1 = Montag.
 */
object LessonGrid {
    const val DEFAULT_PERIOD_COUNT = 10
    const val MATCH_TOLERANCE_MINUTES = 10

    fun fromTimegrids(grids: List<WebUntisTimegrid>): List<LessonSlot> {
        val observedDays = grids.mapNotNull { it.day }.toSet()
        val mondayFirst = usesMondayFirst(observedDays)
        val byDay = linkedMapOf<DayOfWeek, List<WebUntisTimeUnit>>()
        grids.forEach { grid ->
            val day = grid.day?.let { toDayOfWeek(it, mondayFirst) } ?: return@forEach
            val units = grid.timeUnits.mapNotNull { unit ->
                val start = hhmm(unit.startTime) ?: return@mapNotNull null
                if (unit.startTime == 0 && unit.endTime == 0) return@mapNotNull null
                unit to start
            }.sortedBy { it.second }
            if (units.isEmpty()) return@forEach
            val previous = byDay[day]
            if (previous == null || units.size > previous.size) {
                byDay[day] = units.map { it.first }
            }
        }
        val count = byDay.values.maxOfOrNull { it.size } ?: return emptyList()
        return (1..count).map { number ->
            val starts = byDay.mapNotNull { (day, units) ->
                val unit = units.getOrNull(number - 1) ?: return@mapNotNull null
                val start = hhmm(unit.startTime) ?: return@mapNotNull null
                day to start
            }.toMap()
            LessonSlot(number = number, startByDay = starts)
        }
    }

    /** Häufigster Beginn. Bei Gleichstand gewinnt die Zeit, die der Montag hat. */
    fun standardStart(slot: LessonSlot): LocalTime? {
        val entries = slot.startByDay.entries
        if (entries.isEmpty()) return null
        return entries
            .groupBy { it.value }
            .entries
            .maxWith(Comparator { left, right ->
                val byCount = left.value.size.compareTo(right.value.size)
                if (byCount != 0) return@Comparator byCount
                val leftMonday = if (left.value.any { entry -> entry.key == DayOfWeek.MONDAY }) 1 else 0
                val rightMonday = if (right.value.any { entry -> entry.key == DayOfWeek.MONDAY }) 1 else 0
                val byMonday = leftMonday.compareTo(rightMonday)
                if (byMonday != 0) return@Comparator byMonday
                left.key.compareTo(right.key)
            })
            .key
    }

    /** Tage, deren Beginn vom Standard abweicht, Montag zuerst. */
    fun differingStarts(slot: LessonSlot): List<Pair<DayOfWeek, LocalTime>> {
        val standard = standardStart(slot) ?: return emptyList()
        return slot.startByDay
            .filterValues { it != standard }
            .entries
            .sortedBy { it.key }
            .map { it.key to it.value }
    }

    fun weekdays(slots: List<LessonSlot>): List<DayOfWeek> {
        val present = slots.flatMap { it.startByDay.keys }.toSet()
        return DayOfWeek.values().filter { it in present }
    }

    fun firstLesson(lessons: List<Lesson>, date: LocalDate): Lesson? {
        return lessons
            .filter { it.date == date && it.status != LessonStatus.CANCELLED }
            .minByOrNull { it.startTime }
    }

    /**
     * Stundennummer der ersten nicht ausgefallenen Stunde an [day].
     * Exakter Beginn gewinnt, sonst die nächste Timeunit innerhalb von 10 Minuten.
     */
    fun lessonNumber(start: LocalTime, day: DayOfWeek, slots: List<LessonSlot>): Int? {
        val onDay = slots.mapNotNull { slot -> slot.startByDay[day]?.let { slot.number to it } }
        if (onDay.isEmpty()) return null
        onDay.find { it.second == start }?.let { return it.first }
        val nearest = onDay.minBy { minutesBetween(it.second, start) }
        return nearest.first.takeIf { minutesBetween(nearest.second, start) <= MATCH_TOLERANCE_MINUTES }
    }

    /**
     * Ordnet eine alte Stundenbeginn-Uhrzeit der Stundennummer zu.
     * Trifft die Uhrzeit an mehreren Tagen zu, gewinnt die Nummer mit den meisten Treffern.
     */
    fun lessonNumberForStart(start: LocalTime, slots: List<LessonSlot>): Int? {
        if (slots.isEmpty()) return null
        val exact = slots.map { slot ->
            slot.number to slot.startByDay.values.count { it == start }
        }.filter { it.second > 0 }
        if (exact.isNotEmpty()) {
            val best = exact.maxOf { it.second }
            return exact.filter { it.second == best }.minOf { it.first }
        }
        val nearest = slots.mapNotNull { slot ->
            val diff = slot.startByDay.values.minOfOrNull { minutesBetween(it, start) } ?: return@mapNotNull null
            if (diff <= MATCH_TOLERANCE_MINUTES) slot.number to diff else null
        }
        if (nearest.isEmpty()) return null
        val bestDiff = nearest.minOf { it.second }
        return nearest.filter { it.second == bestDiff }.minOf { it.first }
    }

    fun migrateRules(rules: List<WakeTimeRule>, slots: List<LessonSlot>): List<WakeTimeRule> {
        if (rules.isEmpty()) return rules
        val mapped = rules.map { rule ->
            if (rule.lessonNumber > 0) {
                if (rule.legacyStartTime != null) rule.copy(legacyStartTime = null) else rule
            } else {
                val start = rule.legacyStartTime
                val number = if (start != null && slots.isNotEmpty()) lessonNumberForStart(start, slots) else null
                if (number == null) rule else rule.copy(lessonNumber = number, legacyStartTime = null)
            }
        }
        val (numbered, pending) = mapped.partition { it.lessonNumber > 0 }
        val merged = numbered
            .groupBy { it.lessonNumber }
            .toSortedMap()
            .map { (_, group) ->
                group.fold(group.first().copy(legacyStartTime = null)) { acc, rule ->
                    acc.copy(
                        wakeTime = rule.wakeTime ?: acc.wakeTime,
                        preferredTrainDeparture = rule.preferredTrainDeparture ?: acc.preferredTrainDeparture,
                        preferredTrainLine = rule.preferredTrainLine ?: acc.preferredTrainLine
                    )
                }
            }
        return merged + pending
    }

    fun encode(rules: List<WakeTimeRule>): String {
        return rules.joinToString("\n") { rule ->
            val wake = rule.wakeTime?.format() ?: ""
            val train = rule.preferredTrainDeparture?.format() ?: ""
            val line = rule.preferredTrainLine.orEmpty().replace("|", "/").replace("\n", " ")
            val legacy = rule.legacyStartTime?.format()
            if (rule.lessonNumber <= 0 && legacy != null) {
                "$legacy|$wake|$train|$line"
            } else {
                "${rule.lessonNumber}|$wake|$train|$line"
            }
        }
    }

    fun decode(raw: String): List<WakeTimeRule> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split('|')
            val first = parts[0]
            if (first.contains(':')) {
                val start = parseTime(first) ?: return@mapNotNull null
                WakeTimeRule(
                    lessonNumber = 0,
                    wakeTime = parts.getOrNull(1)?.let { parseTime(it) },
                    preferredTrainDeparture = parts.getOrNull(2)?.let { parseTime(it) },
                    preferredTrainLine = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    legacyStartTime = start
                )
            } else {
                val number = first.toIntOrNull() ?: return@mapNotNull null
                if (number < 0) return@mapNotNull null
                WakeTimeRule(
                    lessonNumber = number,
                    wakeTime = parts.getOrNull(1)?.let { parseTime(it) },
                    preferredTrainDeparture = parts.getOrNull(2)?.let { parseTime(it) },
                    preferredTrainLine = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    legacyStartTime = null
                )
            }
        }.toList()
    }

    fun dayLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mo"
        DayOfWeek.TUESDAY -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"
        DayOfWeek.FRIDAY -> "Fr"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Montag"
        DayOfWeek.TUESDAY -> "Dienstag"
        DayOfWeek.WEDNESDAY -> "Mittwoch"
        DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"
        DayOfWeek.SATURDAY -> "Samstag"
        DayOfWeek.SUNDAY -> "Sonntag"
    }

    internal fun usesMondayFirst(days: Set<Int>): Boolean {
        return 1 in days && days.all { it in 1..5 }
    }

    internal fun toDayOfWeek(day: Int, mondayFirst: Boolean): DayOfWeek? {
        return if (mondayFirst) {
            when (day) {
                1 -> DayOfWeek.MONDAY
                2 -> DayOfWeek.TUESDAY
                3 -> DayOfWeek.WEDNESDAY
                4 -> DayOfWeek.THURSDAY
                5 -> DayOfWeek.FRIDAY
                else -> null
            }
        } else {
            when (day) {
                1 -> DayOfWeek.SUNDAY
                2 -> DayOfWeek.MONDAY
                3 -> DayOfWeek.TUESDAY
                4 -> DayOfWeek.WEDNESDAY
                5 -> DayOfWeek.THURSDAY
                6 -> DayOfWeek.FRIDAY
                7 -> DayOfWeek.SATURDAY
                else -> null
            }
        }
    }

    private fun hhmm(value: Int): LocalTime? {
        val hour = value / 100
        val minute = value % 100
        if (hour !in 0..23 || minute !in 0..59) return null
        if (value == 0) return null
        return LocalTime.of(hour, minute)
    }

    private fun minutesBetween(a: LocalTime, b: LocalTime): Int {
        return abs(Duration.between(a, b).toMinutes()).toInt()
    }

    private fun LocalTime.format(): String = String.format("%02d:%02d", hour, minute)

    private fun parseTime(raw: String): LocalTime? {
        if (raw.isBlank()) return null
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}
