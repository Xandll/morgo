package io.qzz.xandll.morgo.domain.model

enum class HaServiceKind {
    TURN_ON,
    TURN_OFF,
    TOGGLE
}

data class HaCall(
    val domain: String,
    val service: String,
    val brightness: Int?
)

/**
 * Welche Home-Assistant-Einträge sich als Licht vor dem Wecken eignen,
 * und welcher Dienst sie ein- oder umschaltet.
 * Ein Skriptaufruf ist immer script.turn_on, eine Szene immer scene.turn_on.
 */
object HomeAssistantTargets {
    private val domains = setOf("light", "switch", "input_boolean", "script", "scene")

    fun domain(entityId: String): String = entityId.substringBefore('.').trim().lowercase()

    fun isSelectable(entityId: String): Boolean = domain(entityId) in domains

    fun type(entityId: String): EntityType {
        return when (domain(entityId)) {
            "light" -> EntityType.LIGHT
            "switch" -> EntityType.SWITCH
            "input_boolean" -> EntityType.INPUT_BOOLEAN
            "scene" -> EntityType.SCENE
            "script" -> EntityType.SCRIPT
            else -> EntityType.UNKNOWN
        }
    }

    fun domainLabel(entityId: String): String {
        return when (domain(entityId)) {
            "light" -> "Licht"
            "switch" -> "Schalter"
            "input_boolean" -> "Helfer"
            "script" -> "Skript"
            "scene" -> "Szene"
            else -> domain(entityId)
        }
    }

    fun call(entityId: String, kind: HaServiceKind, brightness: Int? = null): HaCall? {
        if (!isSelectable(entityId)) return null
        val domain = domain(entityId)
        return when (domain) {
            "light" -> HaCall(
                domain,
                serviceName(kind),
                if (kind == HaServiceKind.TURN_ON) brightness else null
            )
            "switch", "input_boolean" -> HaCall(domain, serviceName(kind), null)
            "script" -> HaCall("script", "turn_on", null)
            "scene" -> HaCall("scene", "turn_on", null)
            else -> null
        }
    }

    private fun serviceName(kind: HaServiceKind): String {
        return when (kind) {
            HaServiceKind.TURN_ON -> "turn_on"
            HaServiceKind.TURN_OFF -> "turn_off"
            HaServiceKind.TOGGLE -> "toggle"
        }
    }
}
