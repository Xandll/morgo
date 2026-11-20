package io.qzz.xandll.morgo.data.datasource

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.data.model.HomeAssistantInfo
import io.qzz.xandll.morgo.data.model.HomeAssistantServiceCall
import io.qzz.xandll.morgo.data.model.HomeAssistantState
import io.qzz.xandll.morgo.data.model.HomeAssistantStates
import io.qzz.xandll.morgo.util.ApiCallException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Protocol

/**
 * Home Assistant REST API.
 * GET /api/ ohne Content-Type, Token nur als "Authorization: Bearer …".
 * OkHttp spricht HTTP/2, das Cloudflare-Tunnel davor erwartet, und fällt auf HTTP/1.1 zurück.
 */
class HomeAssistantClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        val USER_AGENT: String = "Morgo/${BuildConfig.VERSION_NAME} (Android)"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            engine {
                config {
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                }
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }

        /**
         * Entfernt Leerzeichen, Zeilenumbrüche, doppelte Slashes und ein angehängtes /api.
         */
        fun normalizeBaseUrl(input: String): String {
            var url = input.replace(Regex("[\\u0000-\\u0020]+"), "")
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            url = url.replace(Regex("(?<!:)/{2,}"), "/")
            url = url.trimEnd('/')
            url = url.replace(Regex("(?i)/api$"), "")
            return url.trimEnd('/')
        }

        /**
         * Long-Lived-Tokens werden oft mit Zeilenumbruch eingefügt.
         * Ein Newline im Header lässt den Request scheitern.
         */
        fun normalizeToken(token: String): String {
            return token.replace(Regex("[\\u0000-\\u0020]+"), "")
        }

        fun apiUrl(baseUrl: String, path: String): String {
            val root = normalizeBaseUrl(baseUrl)
            val cleanPath = path.trim().trimStart('/')
            return "$root/$cleanPath"
        }
    }

    suspend fun checkConnection(baseUrl: String, token: String): Result<HomeAssistantInfo> {
        val cleanToken = normalizeToken(token)
        if (normalizeBaseUrl(baseUrl).isEmpty() || cleanToken.isEmpty()) {
            return Result.failure(Exception("URL oder Token fehlt"))
        }
        return try {
            val api = getJson(apiUrl(baseUrl, "api/"), cleanToken).getOrElse { return Result.failure(it) }
            val message = api.primitive("message")
            if (message?.contains("API running", ignoreCase = true) != true) {
                return Result.failure(ApiCallException(200, "Unerwartete API-Antwort"))
            }
            val config = getJson(apiUrl(baseUrl, "api/config"), cleanToken).getOrElse { return Result.failure(it) }
            Result.success(
                HomeAssistantInfo(
                    locationName = config.primitive("location_name").orEmpty(),
                    version = config.primitive("version").orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStates(baseUrl: String, token: String): Result<List<HomeAssistantState>> {
        return getStatesDecoded(apiUrl(baseUrl, "api/states"), normalizeToken(token))
    }

    suspend fun getState(baseUrl: String, token: String, entityId: String): Result<HomeAssistantState> {
        return getStatesDecoded(apiUrl(baseUrl, "api/states/$entityId"), normalizeToken(token))
            .mapCatching { states -> states.firstOrNull() ?: throw IllegalArgumentException("Entity fehlt") }
    }

    suspend fun callService(
        baseUrl: String,
        token: String,
        domain: String,
        service: String,
        serviceData: HomeAssistantServiceCall
    ): Result<List<HomeAssistantState>> {
        return try {
            val response = httpClient.post(apiUrl(baseUrl, "api/services/$domain/$service")) {
                authorized(normalizeToken(token))
                contentType(ContentType.Application.Json)
                setBody(serviceData)
            }
            readStates(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun turnOnLight(
        baseUrl: String,
        token: String,
        entityId: String,
        brightness: Int? = null
    ): Result<List<HomeAssistantState>> {
        return callService(
            baseUrl,
            token,
            "light",
            "turn_on",
            HomeAssistantServiceCall(entityId = entityId, brightness = brightness)
        )
    }

    suspend fun toggleLight(
        baseUrl: String,
        token: String,
        entityId: String
    ): Result<List<HomeAssistantState>> {
        return callService(baseUrl, token, "light", "toggle", HomeAssistantServiceCall(entityId = entityId))
    }

    suspend fun activateScene(
        baseUrl: String,
        token: String,
        entityId: String
    ): Result<List<HomeAssistantState>> {
        return callService(baseUrl, token, "scene", "turn_on", HomeAssistantServiceCall(entityId = entityId))
    }

    suspend fun executeScript(
        baseUrl: String,
        token: String,
        entityId: String
    ): Result<List<HomeAssistantState>> {
        return callService(baseUrl, token, "script", "turn_on", HomeAssistantServiceCall(entityId = entityId))
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun getJson(url: String, token: String): Result<JsonObject> {
        return try {
            val response = httpClient.get(url) { authorized(token) }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Result.failure(httpFailure(response, text))
            }
            val element = json.parseToJsonElement(text)
            val obj = element as? JsonObject
                ?: return Result.failure(ApiCallException(response.status.value, "Unerwartete API-Antwort"))
            Result.success(obj)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getStatesDecoded(url: String, token: String): Result<List<HomeAssistantState>> {
        return try {
            val response = httpClient.get(url) { authorized(token) }
            readStates(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun readStates(response: HttpResponse): Result<List<HomeAssistantState>> {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return Result.failure(httpFailure(response, text))
        }
        return runCatching { HomeAssistantStates.parse(text) }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(ApiCallException(response.status.value, "Home-Assistant-Antwort ungültig")) }
            )
    }

    private fun HttpRequestBuilder.authorized(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.UserAgent, USER_AGENT)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private fun httpFailure(response: HttpResponse, body: String): ApiCallException {
        val cloudflare = response.headers["cf-ray"] != null ||
            response.headers["server"]?.contains("cloudflare", ignoreCase = true) == true
        val technical = buildString {
            append("HTTP ")
            append(response.status.value)
            if (cloudflare && response.status.value >= 400) append(" (Cloudflare)")
        }
        val safeBody = body.replace(Regex("\\s+"), " ").trim()
        val withHint = if (safeBody.startsWith("{") || safeBody.startsWith("<") || safeBody.isEmpty()) {
            technical
        } else {
            "$technical: ${safeBody.take(80)}"
        }
        return ApiCallException(response.status.value, withHint)
    }

    private fun JsonObject.primitive(name: String): String? {
        return (this[name] as? JsonPrimitive)?.contentOrNull
    }
}
