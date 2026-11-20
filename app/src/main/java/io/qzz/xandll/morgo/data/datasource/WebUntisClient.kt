package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.qzz.xandll.morgo.data.model.*
import io.qzz.xandll.morgo.util.ErrorDetails
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * WebUntis JSON-RPC API Client
 * Dokumentation: https://webuntis.com/wp-content/uploads/2021/09/2021-09-01-WebUntis_JSON_RPC_API.pdf
 */
class WebUntisClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    private var sessionId: String? = null
    private var schoolNameCookie: String? = null

    companion object {
        private const val SCHOOL_SEARCH_URL = "https://mobile.webuntis.com/ms/schoolquery2"
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /**
         * WebUntis-Elementtypen für jsonrpc.do (SchoolUtils WebUntisElementType).
         * CLASS=1, TEACHER=2, SUBJECT=3, ROOM=4, STUDENT=5.
         * getUserData2017 liefert den Typ als Text ("STUDENT"), getTimetable erwartet die Zahl.
         */
        fun elementTypeId(raw: String?): Int = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "CLASS", "KLASSE" -> 1
            "TEACHER", "LEHRER" -> 2
            "SUBJECT", "FACH" -> 3
            "ROOM", "RAUM" -> 4
            "STUDENT", "SCHUELER", "SCHÜLER" -> 5
            else -> raw?.trim()?.toIntOrNull() ?: 5
        }

        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(lenientJson)
                json(lenientJson, contentType = ContentType("application", "json-rpc"))
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
            }
        }
        
        /**
         * Server-URL normalisieren
         * - Entfernt https://, http://
         * - Entfernt Pfade wie /WebUntis/...
         * - Entfernt trailing slash
         * - Extrahiert Server aus kompletter WebUntis-URL
         */
        fun normalizeServer(input: String): String {
            var normalized = input.trim()
            
            // https:// oder http:// entfernen
            normalized = normalized.replace(Regex("^https?://"), "")
            
            // Trailing slash entfernen
            normalized = normalized.trimEnd('/')
            
            // Pfade entfernen (alles nach dem ersten /)
            val pathIndex = normalized.indexOf('/')
            if (pathIndex > 0) {
                normalized = normalized.substring(0, pathIndex)
            }
            
            return normalized
        }
        
        /**
         * Extrahiert Server und School aus WebUntis-URL
         * z.B. https://mese.webuntis.com/WebUntis/?school=beispielschule
         */
        fun parseWebUntisUrl(url: String): Pair<String?, String?> {
            val trimmed = url.trim()
            
            // Server extrahieren
            val serverMatch = Regex("(?:https?://)?([^/]+)").find(trimmed)
            val server = serverMatch?.groupValues?.get(1)
            
            // School-Parameter extrahieren
            val schoolMatch = Regex("[?&]school=([^&]+)").find(trimmed)
            val school = schoolMatch?.groupValues?.get(1)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
            
            return Pair(server, school)
        }
        
        /**
         * Generiert TOTP (Time-based One-Time Password) aus Secret
         * Implementierung basiert auf RFC 6238
         */
        fun generateTOTP(secret: String, timeStep: Long = 30): String {
            val decodedSecret = decodeBase32(secret)
            val time = System.currentTimeMillis() / 1000 / timeStep
            val timeBytes = ByteArray(8)
            for (i in 7 downTo 0) {
                timeBytes[i] = (time shr (7 - i) * 8).toByte()
            }
            
            val hash = hmacSha1(decodedSecret, timeBytes)
            val offset = (hash.last().toInt() and 0x0F)
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                         ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                         ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                         (hash[offset + 3].toInt() and 0xFF)
            
            val otp = binary % 1000000
            return String.format("%06d", otp)
        }
        
        private fun decodeBase32(input: String): ByteArray {
            val cleanInput = input.uppercase().replace(Regex("[^A-Z2-7]"), "")
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val output = mutableListOf<Byte>()
            var buffer = 0
            var bitsLeft = 0
            
            for (char in cleanInput) {
                val value = alphabet.indexOf(char)
                if (value < 0) continue
                
                buffer = (buffer shl 5) or value
                bitsLeft += 5
                
                if (bitsLeft >= 8) {
                    output.add((buffer shr (bitsLeft - 8)).toByte())
                    bitsLeft -= 8
                }
            }
            
            return output.toByteArray()
        }
        
        private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
            val algorithm = "HmacSHA1"
            val mac = javax.crypto.Mac.getInstance(algorithm)
            val secretKey = javax.crypto.spec.SecretKeySpec(key, algorithm)
            mac.init(secretKey)
            return mac.doFinal(data)
        }
    }

    /**
     * Authentifizierung mit Passwort
     */
    suspend fun authenticate(
        server: String,
        schoolName: String,
        username: String,
        password: String
    ): Result<WebUntisAuthResult> {
        return try {
            val normalizedServer = normalizeServer(server.trim())
            val normalizedSchool = schoolName.trim()
            val normalizedUser = username.trim()
            val normalizedPassword = password.trim()
            
            val encodedSchool = java.net.URLEncoder.encode(normalizedSchool, "UTF-8")
            val url = "https://$normalizedServer/WebUntis/jsonrpc.do?school=$encodedSchool"
            
            val request = WebUntisRequest(
                id = "auth",
                method = "authenticate",
                params = buildJsonObject {
                    put("user", normalizedUser)
                    put("password", normalizedPassword)
                    put("client", "Morgo")
                }
            )

            val response: WebUntisResponse<WebUntisAuthResult> = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            if (response.error != null) {
                Result.failure(rpcError(request.method, response.error))
            } else if (response.result != null) {
                sessionId = response.result.sessionId
                rememberSchool(normalizedSchool)
                Result.success(response.result)
            } else {
                Result.failure(Exception("Keine Antwort von WebUntis"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "WebUntis-Anfrage fehlgeschlagen"))
        }
    }
    
    /**
     * Authentifizierung mit Secret/Key (TOTP)
     * Referenz: npm webuntis (SchoolUtils/WebUntis) - WebUntisSecretAuth._otpLogin
     * POST /WebUntis/jsonrpc_intern.do?m=getUserData2017&school=...&v=i2.2
     * params ist ARRAY mit einem Objekt: [{"auth":{"clientTime":<number>,"user":"...","otp":<number>}}]
     */
    suspend fun authenticateWithSecret(
        server: String,
        schoolName: String,
        username: String,
        secret: String
    ): Result<WebUntisAuthResult> {
        return try {
            val normalizedServer = normalizeServer(server.trim())
            val normalizedSchool = schoolName.trim()
            val normalizedUser = username.trim()
            
            // TOTP generieren (6-stellige Zahl)
            val otpString = generateTOTP(secret)
            val otpNumber = otpString.toInt()
            
            val clientTime = System.currentTimeMillis()
            val encodedSchool = java.net.URLEncoder.encode(normalizedSchool, "UTF-8")
            
            // URL wie in Referenz: m=getUserData2017&v=i2.2
            val url = "https://$normalizedServer/WebUntis/jsonrpc_intern.do?m=getUserData2017&school=$encodedSchool&v=i2.2"
            
            // Request-Body wie in Referenz: params als ARRAY
            val request = WebUntisRequest(
                id = "getUserData2017",
                method = "getUserData2017",
                params = buildJsonArray {
                    add(buildJsonObject {
                        putJsonObject("auth") {
                            put("clientTime", clientTime)
                            put("user", normalizedUser)
                            put("otp", otpNumber) // Als Zahl, nicht String
                        }
                    })
                }
            )

            val httpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val jsessionId = extractJsessionId(httpResponse.headers.getAll(HttpHeaders.SetCookie))
                ?: return Result.failure(Exception("WebUntis hat keine Sitzung zurückgegeben"))

            // Antwort ist oft >270 KB (masterData). Nur userData/Fehler lesen, Rest ignorieren.
            val root = parseObject(httpResponse.bodyAsText())
                ?: return Result.failure(Exception("WebUntis-Antwort ungültig"))

            root.errorOrNull()?.let { return Result.failure(rpcError(request.method, it)) }

            val userData = root["result"].asObject()?.get("userData").asObject()
                ?: return Result.failure(Exception("WebUntis-Antwort ohne userData"))

            val personId = userData["elemId"].asInt()
                ?: return Result.failure(Exception("WebUntis-Antwort ohne elemId"))
            val personType = userData["elemType"].asElementType()
            val displayName = userData["displayName"].asString()?.trim()?.takeIf { it.isNotEmpty() }

            sessionId = jsessionId
            rememberSchool(normalizedSchool)

            Result.success(
                WebUntisAuthResult(
                    sessionId = jsessionId,
                    personType = personType,
                    personId = personId,
                    klasseId = userData["klassenIds"].asArray()?.firstOrNull().asInt(),
                    displayName = displayName
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "WebUntis-Anfrage fehlgeschlagen"))
        }
    }
    
    /**
     * Schulen suchen über mobile.webuntis.com
     */
    suspend fun searchSchools(query: String): Result<List<WebUntisSchool>> {
        return try {
            val request = WebUntisRequest(
                id = "search",
                method = "searchSchool",
                params = buildJsonArray {
                    add(buildJsonObject {
                        put("search", query.trim())
                    })
                }
            )
            
            val response: WebUntisResponse<WebUntisSchoolSearchResult> = httpClient.post(SCHOOL_SEARCH_URL) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            
            if (response.error != null) {
                Result.failure(rpcError("searchSchool", response.error))
            } else if (response.result != null) {
                Result.success(response.result.schools)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "Schulsuche fehlgeschlagen"))
        }
    }

    /**
     * Stundenplan über jsonrpc.do mit Session-Cookie.
     * Gleiche Form wie SchoolUtils/WebUntis _timetableRequest:
     * params.options.element = {id, type} mit numerischem Elementtyp.
     */
    suspend fun getTimetable(
        server: String,
        schoolName: String,
        personType: Int,
        personId: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<WebUntisPeriod>> {
        if (sessionId == null) {
            return Result.failure(Exception("Nicht authentifiziert"))
        }

        return try {
            val request = WebUntisRequest(
                id = "Morgo",
                method = "getTimetable",
                params = buildJsonObject {
                    putJsonObject("options") {
                        put("id", System.currentTimeMillis())
                        putJsonObject("element") {
                            put("id", personId)
                            put("type", personType)
                        }
                        put("startDate", startDate.format(dateFormatter))
                        put("endDate", endDate.format(dateFormatter))
                        put("showLsText", true)
                        put("showStudentgroup", true)
                        put("showLsNumber", true)
                        put("showSubstText", true)
                        put("showInfo", true)
                        put("showBooking", true)
                        put("klasseFields", fieldList())
                        put("roomFields", fieldList())
                        put("subjectFields", fieldList())
                        put("teacherFields", fieldList())
                    }
                }
            )

            val root = postRpc(server, schoolName, request)
                ?: return Result.failure(Exception("WebUntis-Antwort ungültig"))
            root.errorOrNull()?.let { return Result.failure(rpcError(request.method, it)) }

            val periods = when (val result = root["result"]) {
                is JsonArray -> result
                is JsonObject -> result["periods"].asArray() ?: JsonArray(emptyList())
                else -> return Result.failure(Exception("Keine Stundenplan-Daten von WebUntis"))
            }
            Result.success(periods.mapNotNull { element ->
                runCatching { lenientJson.decodeFromJsonElement(WebUntisPeriod.serializer(), element) }.getOrNull()
            })
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "Stundenplan konnte nicht geladen werden"))
        }
    }

    /**
     * Timegrid abrufen (Stundenraster)
     */
    suspend fun getTimegridUnits(
        server: String,
        schoolName: String
    ): Result<List<WebUntisTimegrid>> {
        if (sessionId == null) {
            return Result.failure(Exception("Nicht authentifiziert"))
        }

        return try {
            val request = WebUntisRequest(
                id = "Morgo",
                method = "getTimegridUnits",
                params = buildJsonObject { }
            )

            val root = postRpc(server, schoolName, request)
                ?: return Result.failure(Exception("WebUntis-Antwort ungültig"))
            root.errorOrNull()?.let { return Result.failure(rpcError(request.method, it)) }

            val days = root["result"].asArray()
                ?: return Result.failure(Exception("Keine Stundenraster-Daten von WebUntis"))
            Result.success(days.mapNotNull { element ->
                runCatching { lenientJson.decodeFromJsonElement(WebUntisTimegrid.serializer(), element) }.getOrNull()
            })
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "Stundenraster konnte nicht geladen werden"))
        }
    }

    /**
     * Logout
     */
    fun clearSession() {
        sessionId = null
        schoolNameCookie = null
    }

    suspend fun getHolidays(server: String, schoolName: String): Result<List<io.qzz.xandll.morgo.domain.model.SchoolHoliday>> {
        if (sessionId == null) return Result.failure(Exception("Nicht authentifiziert"))
        return try {
            val request = WebUntisRequest(
                id = "Morgo",
                method = "getHolidays",
                params = buildJsonObject { }
            )
            val root = postRpc(server, schoolName, request)
                ?: return Result.failure(Exception("WebUntis-Antwort ungültig"))
            root.errorOrNull()?.let { return Result.failure(rpcError(request.method, it)) }
            val items = root["result"].asArray() ?: return Result.success(emptyList())
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            val holidays = items.mapNotNull { element ->
                val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val start = obj["startDate"].asInt() ?: return@mapNotNull null
                val end = obj["endDate"].asInt() ?: start
                val name = obj["longName"].asString()?.takeIf { it.isNotBlank() }
                    ?: obj["name"].asString()
                    ?: "Ferien"
                runCatching {
                    io.qzz.xandll.morgo.domain.model.SchoolHoliday(
                        name = name,
                        start = java.time.LocalDate.parse(start.toString().padStart(8, '0'), formatter),
                        end = java.time.LocalDate.parse(end.toString().padStart(8, '0'), formatter)
                    )
                }.getOrNull()
            }
            Result.success(holidays)
        } catch (e: Exception) {
            Result.failure(Exception(ErrorDetails.summarize(e) ?: "Ferien konnten nicht geladen werden"))
        }
    }

    suspend fun logout(server: String, schoolName: String) {
        if (sessionId == null) return

        try {
            val request = WebUntisRequest(
                id = "logout",
                method = "logout",
                params = buildJsonObject { }
            )
            httpClient.post(rpcUrl(server, schoolName)) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Cookie, sessionCookie())
                setBody(request)
            }
        } catch (e: Exception) {
            // Ignorieren
        } finally {
            sessionId = null
            schoolNameCookie = null
        }
    }

    fun close() {
        httpClient.close()
    }

    private fun rememberSchool(school: String) {
        val encoded = java.util.Base64.getEncoder().encodeToString(school.toByteArray(Charsets.UTF_8))
        schoolNameCookie = "_$encoded"
    }

    private fun sessionCookie(): String {
        val sid = urlEncode(sessionId.orEmpty())
        val school = schoolNameCookie?.let { urlEncode(it) }
        return if (school != null) "JSESSIONID=$sid; schoolname=$school" else "JSESSIONID=$sid"
    }

    private fun rpcUrl(server: String, schoolName: String): String {
        val host = normalizeServer(server)
        val school = urlEncode(schoolName.trim())
        return "https://$host/WebUntis/jsonrpc.do?school=$school"
    }

    private suspend fun postRpc(server: String, schoolName: String, request: WebUntisRequest): JsonObject? {
        val response = httpClient.post(rpcUrl(server, schoolName)) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Cookie, sessionCookie())
            setBody(request)
        }
        return parseObject(response.bodyAsText())
    }

    private fun parseObject(text: String): JsonObject? {
        return runCatching { lenientJson.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    private fun JsonObject.errorOrNull(): WebUntisError? {
        val error = this["error"].asObject() ?: return null
        if (error.isEmpty()) return null
        return WebUntisError(
            code = error["code"].asInt(),
            message = error["message"].asString()
        )
    }

    private fun rpcError(method: String, error: WebUntisError): Exception {
        val code = error.code?.toString() ?: "?"
        val message = error.message?.take(180) ?: "unbekannter Fehler"
        return Exception("WebUntis Error [$code] in $method: $message")
    }

    private fun extractJsessionId(setCookies: List<String>?): String? {
        if (setCookies.isNullOrEmpty()) return null
        val match = Regex("JSESSIONID=([^;\\s]+)").find(setCookies.joinToString(";")) ?: return null
        return match.groupValues[1].trim().trim('"')
    }

    private fun fieldList(): JsonArray = buildJsonArray {
        add("id")
        add("name")
        add("longname")
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

    private fun JsonElement?.asString(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull || primitive.isString.not() && primitive.contentOrNull == null) return null
        return primitive.contentOrNull
    }

    private fun JsonElement?.asElementType(): Int {
        val primitive = this as? JsonPrimitive ?: return 5
        if (primitive is JsonNull) return 5
        return primitive.intOrNull ?: elementTypeId(primitive.contentOrNull)
    }

    private fun JsonElement?.asInt(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        primitive.longOrNull?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.let { return it.toInt() }
        return primitive.contentOrNull?.toIntOrNull()
    }
}
