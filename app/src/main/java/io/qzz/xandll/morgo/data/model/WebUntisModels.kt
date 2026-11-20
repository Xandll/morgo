package io.qzz.xandll.morgo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * WebUntis JSON-RPC Request.
 * params ist je nach Methode ein Objekt oder ein Array.
 */
@Serializable
data class WebUntisRequest(
    val id: String,
    val method: String,
    val params: JsonElement,
    val jsonrpc: String = "2.0"
)

/**
 * WebUntis JSON-RPC Response.
 * id kann Zahl oder String sein, unbekannte Felder werden ignoriert.
 */
@Serializable
data class WebUntisResponse<T>(
    val id: JsonElement? = null,
    val jsonrpc: String? = null,
    val result: T? = null,
    val error: WebUntisError? = null
)

@Serializable
data class WebUntisError(
    val code: Int? = null,
    val message: String? = null
)

/**
 * Session nach Passwort-Login bzw. aus Secret-Login abgeleitet.
 * displayName kommt nur beim Secret-Login aus userData.
 */
@Serializable
data class WebUntisAuthResult(
    val sessionId: String = "",
    val personType: Int = 5,
    val personId: Int = 0,
    val klasseId: Int? = null,
    val displayName: String? = null
)

/**
 * Stundenplan-Eintrag. Alle Felder optional, unbekannte Felder ignorieren.
 */
@Serializable
data class WebUntisPeriod(
    val id: Long? = null,
    val date: Int? = null,
    val startTime: Int? = null,
    val endTime: Int? = null,
    @SerialName("kl") val klassen: List<WebUntisClass>? = null,
    @SerialName("te") val teachers: List<WebUntisTeacher>? = null,
    @SerialName("su") val subjects: List<WebUntisSubject>? = null,
    @SerialName("ro") val rooms: List<WebUntisRoom>? = null,
    val code: String? = null,
    val lstext: String? = null,
    val substText: String? = null
)

@Serializable
data class WebUntisClass(
    val id: Int? = null,
    val name: String? = null,
    val longname: String? = null
)

@Serializable
data class WebUntisTeacher(
    val id: Int? = null,
    val name: String? = null,
    val longname: String? = null
)

@Serializable
data class WebUntisSubject(
    val id: Int? = null,
    val name: String? = null,
    val longname: String? = null
)

@Serializable
data class WebUntisRoom(
    val id: Int? = null,
    val name: String? = null,
    val longname: String? = null
)

/**
 * Timegrid (Stundenraster). id fehlt in der jsonrpc.do-Antwort oft.
 */
@Serializable
data class WebUntisTimegrid(
    val id: Int? = null,
    val day: Int? = null,
    val timeUnits: List<WebUntisTimeUnit> = emptyList()
)

@Serializable
data class WebUntisTimeUnit(
    val name: String? = null,
    val startTime: Int = 0,
    val endTime: Int = 0
)

/**
 * Schulsuche
 */
@Serializable
data class WebUntisSchoolSearchResult(
    val schools: List<WebUntisSchool> = emptyList()
)

@Serializable
data class WebUntisSchool(
    val loginName: String = "",
    val displayName: String = "",
    val server: String = "",
    val address: String? = null,
    val schoolId: Int? = null
)
