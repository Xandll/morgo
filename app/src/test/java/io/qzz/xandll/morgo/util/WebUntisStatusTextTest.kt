package io.qzz.xandll.morgo.util

import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WebUntisStatusTextTest {

    @Test
    fun `success names the person and tomorrows first lesson`() {
        val today = LocalDate.of(2026, 11, 20)
        val text = WebUntisStatusText.success(
            displayName = "Anna Beispiel",
            lessons = listOf(
                lesson(today.plusDays(1), 8, 0, LessonStatus.CANCELLED),
                lesson(today.plusDays(1), 8, 55, LessonStatus.REGULAR)
            ),
            today = today
        )
        assertEquals("Verbunden als Anna Beispiel – erste Stunde morgen: 08:55", text)
    }

    @Test
    fun `success skips empty days until the next school day`() {
        val friday = LocalDate.of(2026, 11, 20)
        val text = WebUntisStatusText.success(
            displayName = "Anna Beispiel",
            lessons = listOf(lesson(LocalDate.of(2026, 11, 23), 7, 50, LessonStatus.REGULAR)),
            today = friday
        )
        assertTrue(text.startsWith("Verbunden als Anna Beispiel – erste Stunde am "))
        assertTrue(text.contains("23.11."))
        assertTrue(text.endsWith("07:50"))
        assertFalse(text.contains("morgen"))
    }

    @Test
    fun `error details drop json payload secrets and stay short`() {
        val raw = "Illegal input: Unexpected JSON token at offset 274266: Unexpected symbol 'S' " +
            "in numeric literal at path: \$.result.userData.elemType  JSON input: " +
            "{\"userData\":{\"elemType\":\"STUDENT\",\"elemId\":12004,\"displayName\":\"Anna Beispiel\"," +
            "\"secret\":\"SUPERSECRETVALUE\"}}"
        val summary = ErrorDetails.summarize(IllegalArgumentException(raw))
        assertTrue(summary != null)
        assertTrue(summary!!.length <= ErrorDetails.MAX_LENGTH)
        assertTrue(summary.startsWith("IllegalArgumentException:"))
        assertTrue(summary.contains("elemType"))
        assertFalse(summary.contains("Anna Beispiel"))
        assertFalse(summary.contains("SUPERSECRETVALUE"))
        assertFalse(summary.contains("12004"))
        assertFalse(summary.contains("STUDENT"))
    }

    private fun lesson(date: LocalDate, hour: Int, minute: Int, status: LessonStatus) = Lesson(
        date = date,
        startTime = LocalTime.of(hour, minute),
        endTime = LocalTime.of(hour, minute).plusMinutes(50),
        subject = "AM",
        teacher = "Huber",
        room = "B12",
        status = status
    )
}
