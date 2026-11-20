package io.qzz.xandll.morgo.data.repository

import android.content.SharedPreferences
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonStatus
import java.time.LocalDate
import java.time.LocalTime

/**
 * Letzter erfolgreich gelesener Stundenplan, pro Datum und Wochentag.
 */
class LessonCache(private val prefs: SharedPreferences) {

    fun save(date: LocalDate, lessons: List<Lesson>) {
        val encoded = LessonCacheCodec.encode(lessons.filter { it.date == date })
        prefs.edit()
            .putString(dateKey(date), encoded)
            .putString(weekdayKey(date), encoded)
            .apply()
    }

    fun read(date: LocalDate): List<Lesson>? {
        val exact = prefs.getString(dateKey(date), null)
        if (exact != null) return LessonCacheCodec.decode(exact).map { it.copy(date = date) }
        val weekday = prefs.getString(weekdayKey(date), null) ?: return null
        return LessonCacheCodec.decode(weekday).map { it.copy(date = date) }
    }

    private fun dateKey(date: LocalDate) = "lesson_cache_$date"
    private fun weekdayKey(date: LocalDate) = "lesson_cache_dow_${date.dayOfWeek}"
}

object LessonCacheCodec {
    fun encode(lessons: List<Lesson>): String {
        return lessons.joinToString("\n") { lesson ->
            listOf(
                lesson.startTime.toString(),
                lesson.endTime.toString(),
                lesson.subject.replace("|", "/"),
                lesson.teacher.replace("|", "/"),
                lesson.room.replace("|", "/"),
                lesson.status.name
            ).joinToString("|")
        }
    }

    fun decode(raw: String): List<Lesson> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 6) return@mapNotNull null
            val start = runCatching { LocalTime.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val end = runCatching { LocalTime.parse(parts[1]) }.getOrNull() ?: return@mapNotNull null
            val status = runCatching { LessonStatus.valueOf(parts[5]) }.getOrDefault(LessonStatus.REGULAR)
            Lesson(
                date = LocalDate.EPOCH,
                startTime = start,
                endTime = end,
                subject = parts[2],
                teacher = parts[3],
                room = parts[4],
                status = status
            )
        }.toList()
    }
}
