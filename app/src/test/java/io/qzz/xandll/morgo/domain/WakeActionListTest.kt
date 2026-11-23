package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.EntityType
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.WakeActionList
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeActionListTest {

    @Test
    fun `wake actions are listed once by name and fall back to the entity id`() {
        val actions = listOf("light.beispiel_1", "light.beispiel_2", "LIGHT.beispiel_1 ")
        val entities = listOf(
            HomeAssistantEntity("light.beispiel_1", "Beispiel Licht 1", "off", EntityType.LIGHT),
            HomeAssistantEntity("light.beispiel_2", "Beispiel Licht 2", "off", EntityType.LIGHT),
            HomeAssistantEntity("light.anderes", "Anderes Licht", "off", EntityType.LIGHT)
        )

        assertEquals(
            listOf("Beispiel Licht 1", "Beispiel Licht 2"),
            WakeActionList.resolve(actions, entities).map { it.name }
        )
        assertEquals(
            listOf("light.beispiel_1", "light.beispiel_2"),
            WakeActionList.resolve(actions, emptyList()).map { it.name }
        )
    }
}
