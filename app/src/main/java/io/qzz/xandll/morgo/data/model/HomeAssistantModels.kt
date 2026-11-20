package io.qzz.xandll.morgo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Home Assistant State
 */
@Serializable
data class HomeAssistantState(
    @SerialName("entity_id") val entityId: String = "",
    val state: String = "",
    val attributes: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("last_changed") val lastChanged: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    val friendlyName: String = ""
)

/**
 * Home Assistant Service Call Request
 */
@Serializable
data class HomeAssistantServiceCall(
    @SerialName("entity_id") val entityId: String? = null,
    val brightness: Int? = null,
    val rgb_color: List<Int>? = null,
    val temperature: Float? = null
)

/**
 * Home Assistant Error Response
 */
@Serializable
data class HomeAssistantError(
    val message: String? = null
)

/**
 * Kurzinfo aus GET /api/config. Der Rest der Config wird nicht modelliert.
 */
data class HomeAssistantInfo(
    val locationName: String,
    val version: String
)
