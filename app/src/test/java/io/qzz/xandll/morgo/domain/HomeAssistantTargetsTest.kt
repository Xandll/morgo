package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.EntityType
import io.qzz.xandll.morgo.domain.model.HaCall
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.HomeAssistantTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantTargetsTest {

    @Test
    fun `lights switches and helpers use their own services`() {
        assertEquals(
            HaCall("light", "turn_on", 80),
            HomeAssistantTargets.call("light.schlafzimmer", HaServiceKind.TURN_ON, 80)
        )
        assertEquals(
            HaCall("light", "toggle", null),
            HomeAssistantTargets.call("light.schlafzimmer", HaServiceKind.TOGGLE, 80)
        )
        assertEquals(
            HaCall("light", "turn_off", null),
            HomeAssistantTargets.call("light.schlafzimmer", HaServiceKind.TURN_OFF, 80)
        )
        assertEquals(
            HaCall("switch", "toggle", null),
            HomeAssistantTargets.call("switch.nachtlicht", HaServiceKind.TOGGLE, 80)
        )
        assertEquals(
            HaCall("input_boolean", "turn_off", null),
            HomeAssistantTargets.call("input_boolean.leselampe", HaServiceKind.TURN_OFF, 40)
        )
    }

    @Test
    fun `every script and scene step is a single turn on`() {
        assertEquals(
            HaCall("script", "turn_on", null),
            HomeAssistantTargets.call("script.licht_1", HaServiceKind.TOGGLE, 200)
        )
        assertEquals(
            HaCall("script", "turn_on", null),
            HomeAssistantTargets.call("script.licht_1", HaServiceKind.TURN_ON, 200)
        )
        assertEquals(
            HaCall("scene", "turn_on", null),
            HomeAssistantTargets.call("scene.morgen", HaServiceKind.TOGGLE)
        )
        assertEquals("Skript", HomeAssistantTargets.domainLabel("script.licht_1"))
        assertEquals(EntityType.SCRIPT, HomeAssistantTargets.type("script.licht_1"))
        assertEquals(EntityType.INPUT_BOOLEAN, HomeAssistantTargets.type("input_boolean.leselampe"))
    }

    @Test
    fun `sensors are not offered`() {
        assertNull(HomeAssistantTargets.call("sensor.temp", HaServiceKind.TURN_ON))
        assertFalse(HomeAssistantTargets.isSelectable("sensor.temp"))
        assertTrue(HomeAssistantTargets.isSelectable("script.licht_1"))
    }
}
