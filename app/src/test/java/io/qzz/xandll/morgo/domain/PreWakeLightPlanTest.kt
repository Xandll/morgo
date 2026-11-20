package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.LightIntervalUnit
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PreWakeLightPlanTest {

    @Test
    fun leadBeforeWakeIsTenMinutesEarlier() {
        val wake = LocalDate.of(2026, 11, 21).atTime(5, 30)
        val start = PreWakeLightPlan.start(wake, 10)
        assertEquals(LocalTime.of(5, 20), start.toLocalTime())
        assertEquals(LocalDate.of(2026, 11, 21), start.toLocalDate())
        assertEquals("Licht geht um 05:20 an", PreWakeLightPlan.hint(LocalTime.of(5, 30), 10))
    }

    @Test
    fun leadWrapsPastMidnight() {
        val wake = LocalDate.of(2026, 11, 21).atTime(0, 5)
        val start = PreWakeLightPlan.start(wake, 10)
        assertEquals(LocalDate.of(2026, 11, 20), start.toLocalDate())
        assertEquals(LocalTime.of(23, 55), start.toLocalTime())
        assertEquals("Licht geht um 23:55 an", PreWakeLightPlan.hint(LocalTime.of(0, 5), 10))
    }

    @Test
    fun triggerUsesTheLeadWhenItIsStillAhead() {
        val now = LocalDateTime.of(2026, 11, 20, 20, 0)
        val wake = LocalDateTime.of(2026, 11, 21, 5, 30)
        assertEquals(
            LocalDateTime.of(2026, 11, 21, 5, 20),
            PreWakeLightPlan.triggerAt(now, wake, 10)
        )
    }

    @Test
    fun triggerRunsSoonWhenTheLeadAlreadyPassed() {
        val now = LocalDateTime.of(2026, 11, 21, 5, 25)
        val wake = LocalDateTime.of(2026, 11, 21, 5, 30)
        assertEquals(now.plusSeconds(5), PreWakeLightPlan.triggerAt(now, wake, 10))
    }

    @Test
    fun noLightAfterTheWakeTime() {
        val now = LocalDateTime.of(2026, 11, 21, 5, 40)
        val wake = LocalDateTime.of(2026, 11, 21, 5, 30)
        assertNull(PreWakeLightPlan.triggerAt(now, wake, 10))
    }

    @Test
    fun noLightWhenTomorrowHasNoAlarm() {
        val light = PreWakeLightSettings(enabled = true, entityIds = listOf("light.schlafzimmer"))
        assertFalse(PreWakeLightPlan.shouldArm(rings = false, light))
        assertFalse(PreWakeLightPlan.shouldArm(rings = true, light.copy(enabled = false)))
        assertFalse(PreWakeLightPlan.shouldArm(rings = true, light.copy(entityIds = emptyList())))
        assertTrue(PreWakeLightPlan.shouldArm(rings = true, light))
    }

    @Test
    fun stepsTurnOnThenToggleAtTheInterval() {
        val steps = PreWakeLightPlan.steps(toggleCount = 2, intervalSeconds = 15)
        assertEquals(3, steps.size)
        assertTrue(steps[0].turnOn)
        assertEquals(0, steps[0].wait.seconds)
        assertFalse(steps[1].turnOn)
        assertEquals(15, steps[1].wait.seconds)
        assertFalse(steps[2].turnOn)
        assertEquals(15, steps[2].wait.seconds)
    }

    @Test
    fun zeroTogglesOnlyTurnsOn() {
        val steps = PreWakeLightPlan.steps(0, 20)
        assertEquals(1, steps.size)
        assertTrue(steps.single().turnOn)
    }

    @Test
    fun toggleCountIsCappedAtTen() {
        assertEquals(11, PreWakeLightPlan.steps(40, 5).size)
    }

    @Test
    fun intervalConvertsMinutes() {
        assertEquals(120, PreWakeLightPlan.intervalSeconds(2, LightIntervalUnit.MINUTES))
        assertEquals(20, PreWakeLightPlan.intervalSeconds(20, LightIntervalUnit.SECONDS))
    }
}
