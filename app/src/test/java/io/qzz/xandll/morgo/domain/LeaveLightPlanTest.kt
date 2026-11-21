package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.ConnectionPoint
import io.qzz.xandll.morgo.domain.model.ConnectionStatus
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.LeaveLightPlan
import io.qzz.xandll.morgo.domain.model.LeaveOffAction
import io.qzz.xandll.morgo.domain.model.LeaveOffChoice
import io.qzz.xandll.morgo.domain.model.TrainConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LeaveLightPlanTest {

    private val date = LocalDate.of(2026, 11, 21)

    @Test
    fun leaveTimeIsDepartureMinusTheWalk() {
        val at = LeaveLightPlan.leaveAt(date, LocalTime.of(7, 20), walkingMinutes = 10, offsetMinutes = 0)
        assertEquals(LocalDateTime.of(2026, 11, 21, 7, 10), at)
        assertEquals("Licht aus um 07:10", LeaveLightPlan.hint(at.toLocalTime()))
    }

    @Test
    fun delayUsesTheUpdatedDeparture() {
        val train = train(planned = LocalTime.of(7, 20), actual = LocalTime.of(7, 28))
        assertEquals(LocalTime.of(7, 28), LeaveLightPlan.departureOf(train))
        val at = LeaveLightPlan.leaveAt(date, LeaveLightPlan.departureOf(train), walkingMinutes = 10, offsetMinutes = 0)
        assertEquals(LocalTime.of(7, 18), at.toLocalTime())
    }

    @Test
    fun offsetWaitsPastTheLeaveTime() {
        val at = LeaveLightPlan.leaveAt(date, LocalTime.of(7, 28), walkingMinutes = 10, offsetMinutes = 2)
        assertEquals(LocalTime.of(7, 20), at.toLocalTime())
    }

    @Test
    fun walkAcrossMidnightUsesThePreviousDay() {
        val at = LeaveLightPlan.leaveAt(date, LocalTime.of(0, 5), walkingMinutes = 10, offsetMinutes = 0)
        assertEquals(LocalDate.of(2026, 11, 20), at.toLocalDate())
        assertEquals(LocalTime.of(23, 55), at.toLocalTime())
    }

    @Test
    fun planBReplacesTheDepartureAndADroppedTrainTurnsNothingOff() {
        val original = train(planned = LocalTime.of(7, 20), actual = LocalTime.of(7, 35))
        val alternative = train(planned = LocalTime.of(7, 40), actual = null)
        assertEquals(
            LocalTime.of(7, 40),
            LeaveLightPlan.chosenDeparture(
                rings = true,
                connection = original,
                planBConnection = alternative,
                planBDroppedTrain = false
            )
        )
        assertNull(
            LeaveLightPlan.chosenDeparture(
                rings = true,
                connection = original,
                planBConnection = null,
                planBDroppedTrain = true
            )
        )
    }

    @Test
    fun noTrainAndNoSchoolDayDoNotSchedule() {
        val train = train(planned = LocalTime.of(7, 20), actual = null)
        assertNull(
            LeaveLightPlan.chosenDeparture(
                rings = true,
                connection = null,
                planBConnection = null,
                planBDroppedTrain = false
            )
        )
        assertNull(
            LeaveLightPlan.chosenDeparture(
                rings = false,
                connection = train,
                planBConnection = null,
                planBDroppedTrain = false
            )
        )
    }

    @Test
    fun stoppingTheAlarmDoesNotTurnTheLightsOff() {
        assertFalse(LeaveLightPlan.turnOffBecauseAlarmStopped(departureKnown = false))
        assertFalse(LeaveLightPlan.turnOffBecauseAlarmStopped(departureKnown = true))
    }

    @Test
    fun lightsTurnOffOnceScriptsRepeatAndScenesNeedAnOffEntity() {
        val actions = LeaveLightPlan.actions(
            listOf(
                "light.schlafzimmer",
                "switch.nachtlicht",
                "input_boolean.leselampe",
                "script.licht_1",
                "script.flur",
                "scene.morgen",
                "scene.abend"
            ),
            mapOf(
                "script.licht_1" to LeaveOffChoice(calls = 2),
                "script.flur" to LeaveOffChoice(calls = 4, offEntityId = "script.aus"),
                "scene.abend" to LeaveOffChoice(offEntityId = "scene.dunkel")
            )
        )
        assertEquals(
            listOf(
                LeaveOffAction("light.schlafzimmer", 1),
                LeaveOffAction("switch.nachtlicht", 1),
                LeaveOffAction("input_boolean.leselampe", 1),
                LeaveOffAction("script.licht_1", 2),
                LeaveOffAction("script.aus", 1),
                LeaveOffAction("scene.dunkel", 1)
            ),
            actions
        )
        assertEquals(HaServiceKind.TURN_OFF, LeaveLightPlan.service("light.schlafzimmer"))
        assertEquals(HaServiceKind.TURN_OFF, LeaveLightPlan.service("input_boolean.leselampe"))
        assertEquals(HaServiceKind.TURN_ON, LeaveLightPlan.service("script.licht_1"))
        assertEquals(HaServiceKind.TURN_ON, LeaveLightPlan.service("scene.dunkel"))
    }

    @Test
    fun choicesRoundTrip() {
        val stored = mapOf("script.licht_1" to LeaveOffChoice(calls = 3, offEntityId = ""))
        assertEquals(stored, LeaveLightPlan.decode(LeaveLightPlan.encode(stored)))
    }

    private fun train(planned: LocalTime, actual: LocalTime?): TrainConnection {
        return TrainConnection(
            departure = ConnectionPoint("Heimstadt", planned, actual, "1"),
            arrival = ConnectionPoint("Schulstadt", planned.plusMinutes(20), null, "2"),
            duration = 20,
            changes = 0,
            status = if (actual != null && actual.isAfter(planned)) ConnectionStatus.DELAYED else ConnectionStatus.ON_TIME
        )
    }
}
