package io.qzz.xandll.morgo.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Liest /api/states ohne den generierten Serializer.
 *
 * Der Verbindungstest liest /api und /api/config als loses JSON und funktioniert.
 * Die Lampenliste lief über @Serializable mit attributes: Map<String, JsonElement>.
 * R8 benennt JsonElement um. Die polymorphe Deserialisierung von attributes
 * scheitert dann nur im Release, die ganze Liste fällt weg, und der Fehler
 * wurde bisher nicht angezeigt. Ein kaputtes Entity darf die anderen nicht mitreißen.
 */
object HomeAssistantStates {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(text: String): List<HomeAssistantState> {
        val root = json.parseToJsonElement(text)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> listOf(root)
            else -> throw IllegalArgumentException("Home-Assistant-Antwort ungültig")
        }
        return items.mapNotNull { element -> parseOne(element) }
    }

    private fun parseOne(element: JsonElement): HomeAssistantState? {
        val obj = element as? JsonObject ?: return null
        val entityId = obj.string("entity_id")?.trim().orEmpty()
        if (entityId.isEmpty()) return null
        val attributes = obj["attributes"] as? JsonObject
        val friendly = attributes?.string("friendly_name")?.trim().orEmpty()
        return HomeAssistantState(
            entityId = entityId,
            state = obj.string("state").orEmpty(),
            friendlyName = friendly.ifBlank {
                entityId.substringAfter('.').replace('_', ' ')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        )
    }

    private fun JsonObject.string(name: String): String? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }
}
