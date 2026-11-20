package io.qzz.xandll.morgo.util

import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Erfolgstext für „Verbindung testen“.
 */
object WebUntisStatusText {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN)

    fun success(
        displayName: String?,
        lessons: List<Lesson>,
        today: LocalDate = LocalDate.now()
    ): String {
        val name = displayName?.trim()?.take(80).orEmpty()
        val who = if (name.isNotEmpty()) "Verbunden als $name" else "Verbunden"
        val upcoming = lessons
            .filter { !it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
        val anchor = upcoming.firstOrNull { it.status != LessonStatus.CANCELLED }
            ?: upcoming.firstOrNull()
            ?: return "$who – kein Unterricht in den nächsten Tagen"
        val onDay = upcoming.filter { it.date == anchor.date }
        val first = onDay.firstOrNull { it.status != LessonStatus.CANCELLED } ?: onDay.first()
        val time = first.startTime.format(timeFormat)
        val whenText = when (first.date) {
            today -> "heute"
            today.plusDays(1) -> "morgen"
            else -> "am ${first.date.format(dayFormat)}"
        }
        return "$who – erste Stunde $whenText: $time"
    }
}
