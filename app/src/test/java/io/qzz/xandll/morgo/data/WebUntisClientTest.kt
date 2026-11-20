package io.qzz.xandll.morgo.data

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WebUntisClientTest {

    @Test
    fun `authenticate returns success with valid credentials`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "jsonrpc": "2.0",
                        "id": "auth",
                        "result": {
                            "sessionId": "test-session-123",
                            "personType": 5,
                            "personId": 12345
                        }
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = WebUntisClient(httpClient)
        val result = client.authenticate(
            server = "test.webuntis.com",
            schoolName = "testschool",
            username = "testuser",
            password = "testpass"
        )

        assertTrue(result.isSuccess)
        assertEquals("test-session-123", result.getOrNull()?.sessionId)
        assertEquals(5, result.getOrNull()?.personType)
        assertEquals(12345, result.getOrNull()?.personId)
    }

    @Test
    fun `authenticate returns error on invalid credentials`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "jsonrpc": "2.0",
                        "id": "auth",
                        "error": {
                            "code": -8520,
                            "message": "Invalid username or password"
                        }
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = WebUntisClient(httpClient)
        val result = client.authenticate(
            server = "test.webuntis.com",
            schoolName = "testschool",
            username = "invaliduser",
            password = "invalidpass"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid username") == true)
    }

    @Test
    fun `authenticate handles json-rpc content type from real WebUntis server`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "jsonrpc": "2.0",
                        "id": "auth",
                        "result": {
                            "sessionId": "real-session-456",
                            "personType": 5,
                            "personId": 67890
                        }
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json-rpc;charset=UTF-8")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                val jsonConfig = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                }
                json(jsonConfig)
                json(jsonConfig, contentType = ContentType("application", "json-rpc"))
            }
        }

        val client = WebUntisClient(httpClient)
        val result = client.authenticate(
            server = "beispielschule.webuntis.com",
            schoolName = "beispielschule",
            username = "testuser",
            password = "testpass"
        )

        assertTrue(result.isSuccess)
        assertEquals("real-session-456", result.getOrNull()?.sessionId)
    }

    @Test
    fun `normalizeServer removes https and paths`() {
        assertEquals("test.webuntis.com", WebUntisClient.normalizeServer("https://test.webuntis.com"))
        assertEquals("test.webuntis.com", WebUntisClient.normalizeServer("test.webuntis.com/"))
        assertEquals("test.webuntis.com", WebUntisClient.normalizeServer("https://test.webuntis.com/WebUntis/"))
        assertEquals("beispielschule.webuntis.com", WebUntisClient.normalizeServer("beispielschule.webuntis.com"))
    }

    @Test
    fun `parseWebUntisUrl extracts server and school`() {
        val (server, school) = WebUntisClient.parseWebUntisUrl("https://mese.webuntis.com/WebUntis/?school=beispielschule")
        assertEquals("mese.webuntis.com", server)
        assertEquals("beispielschule", school)
        
        val (server2, school2) = WebUntisClient.parseWebUntisUrl("https://test.webuntis.com/WebUntis/index.do?school=Test+School")
        assertEquals("test.webuntis.com", server2)
        assertEquals("Test School", school2)
    }

    @Test
    fun `generateTOTP produces 6-digit code`() {
        val secret = "JBSWY3DPEHPK3PXP"
        val otp = WebUntisClient.generateTOTP(secret)
        
        assertTrue(otp.length == 6)
        assertTrue(otp.all { it.isDigit() })
    }

    @Test
    fun `authenticate encodes school name with spaces`() = runTest {
        val mockEngine = MockEngine { request ->
            assertTrue(request.url.toString().contains("school=Test+School") || 
                      request.url.toString().contains("school=Test%20School"))
            respond(
                content = ByteReadChannel("""
                    {
                        "jsonrpc": "2.0",
                        "id": "auth",
                        "result": {
                            "sessionId": "test-123",
                            "personType": 5,
                            "personId": 12345
                        }
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                val jsonConfig = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                json(jsonConfig)
                json(jsonConfig, contentType = ContentType("application", "json-rpc"))
            }
        }

        val client = WebUntisClient(httpClient)
        val result = client.authenticate(
            server = "test.webuntis.com",
            schoolName = "Test School",
            username = "user",
            password = "pass"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `elementTypeId maps WebUntis names to jsonrpc ids`() {
        assertEquals(1, WebUntisClient.elementTypeId("CLASS"))
        assertEquals(2, WebUntisClient.elementTypeId("TEACHER"))
        assertEquals(3, WebUntisClient.elementTypeId("SUBJECT"))
        assertEquals(4, WebUntisClient.elementTypeId("ROOM"))
        assertEquals(5, WebUntisClient.elementTypeId("STUDENT"))
        assertEquals(5, WebUntisClient.elementTypeId("student"))
        assertEquals(2, WebUntisClient.elementTypeId("2"))
    }

    @Test
    fun `authenticateWithSecret reads string elemType and ignores unknown fields`() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().contains("jsonrpc_intern.do"))
            assertTrue(request.url.parameters["m"] == "getUserData2017")
            assertEquals("i2.2", request.url.parameters["v"])
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            val params = body["params"]
            assertTrue(params is JsonArray)
            val auth = params!!.jsonArray.first().jsonObject["auth"]!!.jsonObject
            assertEquals("anna.beispiel", auth["user"]!!.jsonPrimitive.content)
            assertFalse(auth["otp"]!!.jsonPrimitive.isString)
            assertFalse(auth["clientTime"]!!.jsonPrimitive.isString)
            respond(
                content = ByteReadChannel(userData2017Response()),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json-rpc;charset=UTF-8"),
                    HttpHeaders.SetCookie to listOf("JSESSIONID=ABC123; Path=/WebUntis; HttpOnly")
                )
            )
        }

        val result = client(engine).authenticateWithSecret(
            server = "https://mese.webuntis.com/WebUntis/",
            schoolName = "Testschule",
            username = " anna.beispiel ",
            secret = "JBSWY3DPEHPK3PXP"
        )

        assertTrue(result.exceptionOrNull()?.message ?: "fehlgeschlagen", result.isSuccess)
        val auth = result.getOrThrow()
        assertEquals("ABC123", auth.sessionId)
        assertEquals(5, auth.personType)
        assertEquals(12004, auth.personId)
        assertEquals("Anna Beispiel", auth.displayName)
        assertEquals(42, auth.klasseId)
    }

    @Test
    fun `secret login then loads timetable and timegrid with session cookie`() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            val bodyText = request.bodyText()
            when {
                url.contains("jsonrpc_intern.do") -> respond(
                    content = ByteReadChannel(userData2017Response()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json-rpc;charset=UTF-8"),
                        HttpHeaders.SetCookie to listOf("JSESSIONID=ABC123; Path=/WebUntis; HttpOnly")
                    )
                )
                bodyText.contains("\"getTimetable\"") -> {
                    assertTrue(url.contains("/WebUntis/jsonrpc.do"))
                    assertTrue(url.contains("school=Testschule"))
                    val cookie = request.headers[HttpHeaders.Cookie].orEmpty()
                    assertTrue(cookie.contains("JSESSIONID=ABC123"))
                    val schoolCookie = "_" + java.util.Base64.getEncoder()
                        .encodeToString("Testschule".toByteArray())
                    val encodedSchool = java.net.URLEncoder.encode(schoolCookie, "UTF-8")
                    assertTrue(cookie.contains("schoolname=$encodedSchool"))
                    val options = Json.parseToJsonElement(bodyText)
                        .jsonObject["params"]!!.jsonObject["options"]!!.jsonObject
                    val element = options["element"]!!.jsonObject
                    assertEquals(12004, element["id"]!!.jsonPrimitive.int)
                    assertEquals(5, element["type"]!!.jsonPrimitive.int)
                    assertEquals("20261121", options["startDate"]!!.jsonPrimitive.content)
                    assertEquals("20261121", options["endDate"]!!.jsonPrimitive.content)
                    respond(
                        content = ByteReadChannel(timetableResponse()),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json-rpc;charset=UTF-8")
                    )
                }
                bodyText.contains("\"getTimegridUnits\"") -> {
                    val params = Json.parseToJsonElement(bodyText).jsonObject["params"]
                    assertTrue(params is JsonObject)
                    assertTrue(params!!.jsonObject.isEmpty())
                    assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("JSESSIONID=ABC123"))
                    respond(
                        content = ByteReadChannel(timegridResponse()),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond(
                    content = ByteReadChannel("""{"jsonrpc":"2.0","id":"x","error":{"code":-1,"message":"unexpected"}}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val webUntis = client(engine)
        val auth = webUntis.authenticateWithSecret(
            server = "mese.webuntis.com",
            schoolName = "Testschule",
            username = "anna.beispiel",
            secret = "JBSWY3DPEHPK3PXP"
        ).getOrThrow()

        val periods = webUntis.getTimetable(
            server = "mese.webuntis.com",
            schoolName = "Testschule",
            personType = auth.personType,
            personId = auth.personId,
            startDate = LocalDate.of(2026, 11, 21),
            endDate = LocalDate.of(2026, 11, 21)
        )
        assertTrue(periods.exceptionOrNull()?.message ?: "Stundenplan fehlgeschlagen", periods.isSuccess)
        val lesson = periods.getOrThrow().single()
        assertEquals(20261121, lesson.date)
        assertEquals(800, lesson.startTime)
        assertEquals("AM", lesson.subjects?.firstOrNull()?.name)
        assertEquals("5AHIF", lesson.klassen?.firstOrNull()?.name)

        val grid = webUntis.getTimegridUnits("mese.webuntis.com", "Testschule")
        assertTrue(grid.exceptionOrNull()?.message ?: "Stundenraster fehlgeschlagen", grid.isSuccess)
        assertEquals(800, grid.getOrThrow().first().timeUnits.first().startTime)
    }

    private fun client(engine: MockEngine): WebUntisClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                val jsonConfig = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    coerceInputValues = true
                }
                json(jsonConfig)
                json(jsonConfig, contentType = ContentType("application", "json-rpc"))
            }
        }
        return WebUntisClient(httpClient)
    }

    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is TextContent -> content.text
        is ByteArrayContent -> content.bytes().decodeToString()
        else -> error("Unerwarteter Request-Body: ${content::class.simpleName}")
    }

    private fun userData2017Response(): String = """
        {
          "jsonrpc": "2.0",
          "id": "getUserData2017",
          "result": {
            "masterData": {
              "timeStamp": 1700000000000,
              "klassen": [{"id": 42, "name": "5AHIF", "extra": {"unused": true}}],
              "ignoredBlob": [1, {"keep": "me"}]
            },
            "userData": {
              "elemType": "STUDENT",
              "elemId": 12004,
              "displayName": "Anna Beispiel",
              "schoolName": "Testschule",
              "departmentId": 3,
              "children": [],
              "klassenIds": [42],
              "rights": ["CLASSREGISTER", "R_MY_ABSENCES"]
            },
            "settings": {"showAbsenceReason": true, "customFlag": "ignored"},
            "messengerSettings": {"enabled": false}
          }
        }
    """.trimIndent()

    private fun timetableResponse(): String = """
        {
          "jsonrpc": "2.0",
          "id": 17,
          "result": [
            {
              "id": 991,
              "date": 20261121,
              "startTime": 800,
              "endTime": 850,
              "kl": [{"id": 42, "name": "5AHIF", "longname": "5. Klasse"}],
              "te": [{"id": 9, "name": "HUBER"}],
              "su": [{"id": 3, "name": "AM", "longname": "Angewandte Mathematik"}],
              "ro": [{"id": 4, "name": "B12"}],
              "code": null,
              "lstext": "Schularbeit",
              "activityType": "Unterricht",
              "statflags": "1",
              "studentGroup": "A"
            }
          ]
        }
    """.trimIndent()

    private fun timegridResponse(): String = """
        {
          "jsonrpc": "2.0",
          "id": "Morgo",
          "result": [
            {
              "day": 2,
              "timeUnits": [
                {"name": "1", "startTime": 800, "endTime": 845, "extra": true}
              ],
              "unknownDayField": "x"
            }
          ]
        }
    """.trimIndent()
}
