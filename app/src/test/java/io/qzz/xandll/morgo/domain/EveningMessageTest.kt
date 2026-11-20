package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.AlarmPlan
import io.qzz.xandll.morgo.domain.model.ConnectionPoint
import io.qzz.xandll.morgo.domain.model.ConnectionStatus
import io.qzz.xandll.morgo.domain.model.EveningMessage
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonStatus
import io.qzz.xandll.morgo.domain.model.TrainConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EveningMessageTest {

    @Test
    fun `ringing morning names lesson and train`() {
        val plan = AlarmPlan(
            alarmTime = LocalTime.of(5, 30),
            reason = "Regulärer Schultag",
            firstLesson = Lesson(
                date = LocalDate.of(2026, 11, 23),
                startTime = LocalTime.of(7, 45),
                endTime = LocalTime.of(8, 30),
                subject = "Deutsch",
                teacher = "A",
                room = "1",
                status = LessonStatus.REGULAR
            ),
            trainConnection = TrainConnection(
                departure = ConnectionPoint("Heimstadt", LocalTime.of(6, 12), null, "1"),
                arrival = ConnectionPoint("Schulstadt", LocalTime.of(6, 30), null, "2"),
                duration = 18,
                changes = 0,
                status = ConnectionStatus.ON_TIME
            ),
            lessonNumber = 1
        )
        assertEquals("Morgen läutet dein Wecker um 05:30", EveningMessage.title(plan))
        assertEquals("1. Stunde 07:45 · Zug 06:12 ab Heimstadt", EveningMessage.body(plan))
    }

    @Test
    fun `quiet morning names the reason`() {
        val plan = AlarmPlan(
            alarmTime = LocalTime.of(7, 0),
            reason = "Wochenende",
            firstLesson = null,
            trainConnection = null,
            rings = false
        )
        assertEquals("Morgen läutet kein Wecker", EveningMessage.title(plan))
        assertTrue(EveningMessage.body(plan).contains("Wochenende"))
    }
}
