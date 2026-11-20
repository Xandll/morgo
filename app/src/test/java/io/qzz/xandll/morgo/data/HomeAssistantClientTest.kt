package io.qzz.xandll.morgo.data

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.util.ApiCallException
import io.qzz.xandll.morgo.util.ConnectionErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class HomeAssistantClientTest {

    @Test
    fun `normalize strips slash api suffix and token newline`() {
        assertEquals(
            "https://home.example",
            HomeAssistantClient.normalizeBaseUrl(" https://home.example/ \n")
        )
        assertEquals(
            "https://home.example",
            HomeAssistantClient.normalizeBaseUrl("https://home.example/")
        )
        assertEquals(
            "https://home.example",
            HomeAssistantClient.normalizeBaseUrl("https://home.example/api")
        )
        assertEquals(
            "https://home.example",
            HomeAssistantClient.normalizeBaseUrl("https://home.example/api/")
        )
        assertEquals(
            "https://home.example",
            HomeAssistantClient.normalizeBaseUrl("https://home.example//")
        )
        assertEquals("http://192.0.2.20:8123", HomeAssistantClient.normalizeBaseUrl("http://192.0.2.20:8123/"))
        assertEquals("abc.def", HomeAssistantClient.normalizeToken("abc.def\n"))
        assertEquals("abc.def", HomeAssistantClient.normalizeToken(" abc.def \r\n"))
        assertFalse(HomeAssistantClient.normalizeToken("abc\n").contains("\n"))
    }

    @Test
    fun `checkConnection trims token and avoids double slash`() = runTest {
        val urls = mutableListOf<String>()
        val client = client(MockEngine { request ->
            urls += request.url.toString()
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            assertFalse(request.headers[HttpHeaders.Authorization]!!.contains("\n"))
            assertNull(request.headers[HttpHeaders.ContentType])
            assertEquals(HomeAssistantClient.USER_AGENT, request.headers[HttpHeaders.UserAgent])
            assertTrue(HomeAssistantClient.USER_AGENT.startsWith("Morgo/"))
            assertTrue(HomeAssistantClient.USER_AGENT.contains(BuildConfig.VERSION_NAME))
            when {
                request.url.encodedPath == "/api/" -> json("""{"message":"API running."}""")
                request.url.encodedPath == "/api/config" -> json(
                    """{"location_name":"Wohnung","version":"2024.11.1","components":["http"],"latitude":48.2,"unit_system":{"length":"km"}}"""
                )
                else -> respond("no", HttpStatusCode.NotFound)
            }
        })

        val result = client.checkConnection("https://home.example.io/", "secret-token\n")

        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        assertEquals("Wohnung", result.getOrNull()?.locationName)
        assertEquals("2024.11.1", result.getOrNull()?.version)
        assertEquals(
            listOf("https://home.example.io/api/", "https://home.example.io/api/config"),
            urls
        )
        assertTrue(urls.none { it.contains("//api") })
    }

    @Test
    fun `checkConnection accepts url that already ends with api`() = runTest {
        val urls = mutableListOf<String>()
        val client = client(MockEngine { request ->
            urls += request.url.encodedPath
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/" -> json("""{"message":"API running."}""")
                "/api/config" -> json("""{"location_name":"Haus","version":"2025.1.0"}""")
                else -> respond("no", HttpStatusCode.NotFound)
            }
        })

        val result = client.checkConnection("https://home.example.io/api/", " secret-token ")
        assertTrue(result.isSuccess)
        assertEquals(listOf("/api/", "/api/config"), urls)
    }

    @Test
    fun `401 is token invalid and does not leak the token`() = runTest {
        val client = client(MockEngine { request ->
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            assertEquals(1, request.headers.getAll(HttpHeaders.Authorization)?.size)
            respond(
                content = """{"message":"Invalid access token or password."}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })

        val result = client.checkConnection("https://home.example.io", "secret-token\r\n")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ApiCallException)
        assertEquals(401, (error as ApiCallException).statusCode)
        assertFalse(error.message.orEmpty().contains("secret-token"))
        val described = ConnectionErrors.describe(error, ConnectionErrors.Service.HOME_ASSISTANT)
        assertEquals("Token ungültig", described.message)
        assertFalse(described.technical.orEmpty().contains("secret-token"))
        assertFalse(described.technical.orEmpty().contains("Invalid access token"))
    }

    @Test
    fun `status codes map to the same german messages`() {
        assertEquals(
            "Falsche URL",
            ConnectionErrors.describe(ApiCallException(404, "HTTP 404"), ConnectionErrors.Service.HOME_ASSISTANT).message
        )
        assertEquals(
            "Von Cloudflare blockiert",
            ConnectionErrors.describe(ApiCallException(403, "HTTP 403"), ConnectionErrors.Service.HOME_ASSISTANT).message
        )
        assertEquals(
            "Von Cloudflare blockiert",
            ConnectionErrors.describe(ApiCallException(1010, "HTTP 1010"), ConnectionErrors.Service.HAFAS).message
        )
        assertEquals(
            "Nicht erreichbar",
            ConnectionErrors.describe(UnknownHostException("home.example.io"), ConnectionErrors.Service.WEBUNTIS).message
        )
        assertEquals(
            "Nicht erreichbar",
            ConnectionErrors.describe(java.net.SocketTimeoutException("timeout"), ConnectionErrors.Service.HAFAS).message
        )
        assertEquals(
            "SSL-Fehler",
            ConnectionErrors.describe(SSLHandshakeException("bad cert"), ConnectionErrors.Service.HOME_ASSISTANT).message
        )
    }

    @Test
    fun `states keep lights when one entity is broken and use friendly names`() = runTest {
        val body = """
            [
              {"entity_id":"light.schlafzimmer","state":"off","attributes":{"friendly_name":"Schlafzimmer","brightness":null,"supported_color_modes":["brightness"],"nested":{"a":1}},"context":{"id":"abc","parent_id":null}},
              {"entity_id":"sensor.temp","state":"21.5","attributes":{"friendly_name":"Temperatur","unit_of_measurement":"°C"}},
              {"state":null,"attributes":"kaputt"},
              {"entity_id":"scene.morgen","state":"2024-01-01T00:00:00+00:00","attributes":{"friendly_name":"Morgenlicht"}}
            ]
        """.trimIndent()
        val client = client(MockEngine { request ->
            assertEquals("/api/states", request.url.encodedPath)
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            json(body)
        })

        val result = client.getStates("https://home.example/api/", "secret-token\n")

        assertTrue(result.exceptionOrNull()?.message, result.isSuccess)
        val states = result.getOrThrow()
        assertEquals(listOf("light.schlafzimmer", "sensor.temp", "scene.morgen"), states.map { it.entityId })
        assertEquals("Schlafzimmer", states[0].friendlyName)
        assertEquals("Morgenlicht", states[2].friendlyName)
    }

    @Test
    fun `states failure is visible instead of an empty list`() = runTest {
        val client = client(MockEngine {
            respond("nope", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "text/plain"))
        })
        val result = client.getStates("https://home.example.io", "secret-token")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiCallException
        assertEquals(403, error.statusCode)
        assertEquals(
            "Von Cloudflare blockiert",
            ConnectionErrors.describe(error, ConnectionErrors.Service.HOME_ASSISTANT).message
        )
    }

    private fun client(engine: MockEngine): HomeAssistantClient {
        return HomeAssistantClient(HttpClient(engine) { expectSuccess = false })
    }

    private fun MockRequestHandleScope.json(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
