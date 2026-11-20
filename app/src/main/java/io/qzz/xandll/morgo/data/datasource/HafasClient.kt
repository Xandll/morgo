package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.qzz.xandll.morgo.data.model.*
import io.qzz.xandll.morgo.util.ErrorDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ÖBB HAFAS (Scotty) mgate.exe API Client
 * Endpunkt: https://fahrplan.oebb.at/bin/mgate.exe
 */
class HafasClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://fahrplan.oebb.at/bin/mgate.exe"
        private const val CLIENT_ID = "OEBB"
        private const val CLIENT_TYPE = "IPH"
        private const val CLIENT_NAME = "oebbIPH"
        private const val CLIENT_VERSION = "6030600"
        private const val API_VERSION = "1.41"
        private const val AUTH_AID = "OWDL4fE4ixNiPBBm" // öffentliche Kennung
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** HAFAS-Wildcard: ohne „?“ liefert LocMatch oft keine Treffer. */
        fun locMatchName(query: String): String {
            val trimmed = query.trim()
            return if (trimmed.endsWith("?")) trimmed else "$trimmed?"
        }

        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
            }
        }
    }

    /**
     * Stationen suchen
     */
    suspend fun searchStations(query: String): Result<List<HafasLocation>> {
        val req = buildJsonObject {
            putJsonObject("input") {
                put("field", "S")
                putJsonObject("loc") {
                    put("name", locMatchName(query))
                    put("type", "S")
                }
                put("maxLoc", 5)
            }
        }
        return post(envelope("LocMatch", req)).map { response ->
            response.svcResL?.firstOrNull()?.res?.match?.locL ?: emptyList()
        }
    }

    /**
     * Verbindungen suchen
     * @return Pair of (connections, common) to resolve indices
     */
    suspend fun searchConnections(
        fromStationId: String,
        toStationId: String,
        date: LocalDate,
        time: LocalTime
    ): Result<Pair<List<HafasConnection>, HafasCommon?>> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")
        val req = buildJsonObject {
            putJsonArray("depLocL") { addJsonObject { put("lid", fromStationId) } }
            putJsonArray("arrLocL") { addJsonObject { put("lid", toStationId) } }
            put("outDate", date.format(dateFormatter))
            put("outTime", time.format(timeFormatter))
            put("getPolyline", false)
            put("numF", 3)
        }
        return post(envelope("TripSearch", req)).map { response ->
            val serviceResponse = response.svcResL?.firstOrNull()
            val connections = serviceResponse?.res?.outConL ?: emptyList()
            val common = serviceResponse?.res?.common
            Pair(connections, common)
        }
    }

    private fun envelope(method: String, req: JsonObject): JsonObject = buildJsonObject {
        putJsonObject("auth") {
            put("type", "AID")
            put("aid", AUTH_AID)
        }
        putJsonObject("client") {
            put("id", CLIENT_ID)
            put("v", CLIENT_VERSION)
            put("type", CLIENT_TYPE)
            put("name", CLIENT_NAME)
        }
        put("ver", API_VERSION)
        put("lang", "de")
        putJsonArray("svcReqL") {
            addJsonObject {
                put("meth", method)
                put("req", req)
            }
        }
    }

    private suspend fun post(payload: JsonObject): Result<HafasResponse> {
        return try {
            val response = httpClient.post(BASE_URL) {
                setBody(TextContent(json.encodeToString(JsonObject.serializer(), payload), ContentType.Application.Json))
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HAFAS Error: HTTP ${response.status.value}"))
            }
            val parsed = json.decodeFromString(HafasResponse.serializer(), response.bodyAsText())
            val error = hafasError(parsed)
            if (error != null) {
                Result.failure(Exception("HAFAS Error: $error"))
            } else {
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "ÖBB-Anfrage fehlgeschlagen"))
        }
    }

    /** „OK“ ist der Erfolgsstatus, kein Fehler. */
    private fun hafasError(response: HafasResponse): String? {
        fun bad(code: String?) = code?.takeUnless { it.equals("OK", ignoreCase = true) }
        return bad(response.err) ?: bad(response.svcResL?.firstOrNull()?.err)
    }

    fun close() {
        httpClient.close()
    }
}

/**
 * Marker-Interface für Request-Daten
 */
interface HafasRequestData
