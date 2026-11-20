package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.data.model.WebUntisTimeUnit
import io.qzz.xandll.morgo.data.model.WebUntisTimegrid
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonGrid
import io.qzz.xandll.morgo.domain.model.LessonStatus
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class LessonGridTest {

    @Test
    fun `calendar days map monday to day 2 and keep weekday specific starts`() {
        val slots = LessonGrid.fromTimegrids(calendarGrid())

        assertEquals(2, slots.size)
        assertEquals(LocalTime.of(7, 45), slots[0].startByDay[DayOfWeek.MONDAY])
        assertEquals(LocalTime.of(8, 0), slots[0].startByDay[DayOfWeek.WEDNESDAY])
        assertEquals(LocalTime.of(8, 35), slots[1].startByDay[DayOfWeek.MONDAY])
        assertTrue(slots[0].startDiffersByDay())
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LessonGrid.weekdays(slots))
        assertEquals(LocalTime.of(7, 45), LessonGrid.standardStart(slots[0]))
        assertEquals(listOf(DayOfWeek.WEDNESDAY to LocalTime.of(8, 0)), LessonGrid.differingStarts(slots[0]))
        assertEquals(LocalTime.of(8, 35), LessonGrid.standardStart(slots[1]))
        assertEquals(listOf(DayOfWeek.WEDNESDAY to LocalTime.of(8, 50)), LessonGrid.differingStarts(slots[1]))
    }

    @Test
    fun `days 1 to 5 are monday first`() {
        val slots = LessonGrid.fromTimegrids(
            listOf(
                WebUntisTimegrid(
                    day = 1,
                    timeUnits = listOf(WebUntisTimeUnit("1", 800, 845))
                ),
                WebUntisTimegrid(
                    day = 5,
                    timeUnits = listOf(WebUntisTimeUnit("1", 810, 855))
                )
            )
        )

        assertEquals(LocalTime.of(8, 0), slots.single().startByDay[DayOfWeek.MONDAY])
        assertEquals(LocalTime.of(8, 10), slots.single().startByDay[DayOfWeek.FRIDAY])
    }

    @Test
    fun `lesson number uses the weekday grid and a ten minute tolerance`() {
        val slots = LessonGrid.fromTimegrids(calendarGrid())

        assertEquals(1, LessonGrid.lessonNumber(LocalTime.of(7, 45), DayOfWeek.MONDAY, slots))
        assertEquals(1, LessonGrid.lessonNumber(LocalTime.of(8, 0), DayOfWeek.WEDNESDAY, slots))
        assertEquals(1, LessonGrid.lessonNumber(LocalTime.of(8, 10), DayOfWeek.WEDNESDAY, slots))
        assertNull(LessonGrid.lessonNumber(LocalTime.of(8, 0), DayOfWeek.MONDAY, slots))
        assertEquals(2, LessonGrid.lessonNumber(LocalTime.of(8, 35), DayOfWeek.MONDAY, slots))
    }

    @Test
    fun `legacy clock rules migrate to the matching lesson number`() {
        val slots = LessonGrid.fromTimegrids(calendarGrid())
        val legacy = LessonGrid.decode("08:00|06:10|07:15|REX 5\n08:35|06:40||")

        val migrated = LessonGrid.migrateRules(legacy, slots)

        assertEquals(2, migrated.size)
        assertEquals(1, migrated[0].lessonNumber)
        assertEquals(LocalTime.of(6, 10), migrated[0].wakeTime)
        assertEquals(LocalTime.of(7, 15), migrated[0].preferredTrainDeparture)
        assertEquals("REX 5", migrated[0].preferredTrainLine)
        assertNull(migrated[0].legacyStartTime)
        assertEquals(2, migrated[1].lessonNumber)
        assertEquals(LocalTime.of(6, 40), migrated[1].wakeTime)
        assertNull(migrated[1].preferredTrainDeparture)
    }

    @Test
    fun `numbered rules round trip and keep an empty wake time`() {
        val rules = listOf(
            WakeTimeRule(
                lessonNumber = 2,
                wakeTime = null,
                preferredTrainDeparture = LocalTime.of(7, 20),
                preferredTrainLine = "S 3"
            )
        )

        val encoded = LessonGrid.encode(rules)
        assertEquals("2||07:20|S 3", encoded)
        assertEquals(rules, LessonGrid.decode(encoded))
    }

    @Test
    fun `first lesson skips cancelled periods`() {
        val date = LocalDate.of(2026, 11, 23)
        val first = LessonGrid.firstLesson(
            listOf(
                lesson(date, 8, 0, LessonStatus.CANCELLED),
                lesson(date, 9, 0, LessonStatus.REGULAR)
            ),
            date
        )

        assertEquals(LocalTime.of(9, 0), first?.startTime)
    }

    private fun calendarGrid(): List<WebUntisTimegrid> {
        return listOf(
            WebUntisTimegrid(
                day = 2,
                timeUnits = listOf(
                    WebUntisTimeUnit("1", 745, 830),
                    WebUntisTimeUnit("2", 835, 920)
                )
            ),
            WebUntisTimegrid(
                day = 4,
                timeUnits = listOf(
                    WebUntisTimeUnit("1", 800, 845),
                    WebUntisTimeUnit("2", 850, 935)
                )
            )
        )
    }

    private fun lesson(date: LocalDate, hour: Int, minute: Int, status: LessonStatus): Lesson {
        return Lesson(
            date = date,
            startTime = LocalTime.of(hour, minute),
            endTime = LocalTime.of(hour, minute).plusMinutes(50),
            subject = "Mathematik",
            teacher = "Müller",
            room = "101",
            status = status
        )
    }
}
