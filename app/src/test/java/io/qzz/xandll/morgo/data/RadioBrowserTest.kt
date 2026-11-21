package io.qzz.xandll.morgo.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.qzz.xandll.morgo.data.datasource.RadioBrowser
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.util.ConnectionErrors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class RadioBrowserTest {

    @Test
    fun `user agent and default host`() {
        assertEquals("Morgo/1.6.1", RadioBrowser.userAgent("1.6.1"))
        assertEquals(RadioBrowser.DEFAULT_HOST, MusicAlarmSettings().radioBrowserHost)
        assertEquals("https://de1.api.radio-browser.info", RadioBrowser.DEFAULT_HOST)
    }

    @Test
    fun `umlaut variants stay unique`() {
        assertEquals(listOf("O3"), RadioBrowser.variants("O3"))
        assertEquals(listOf("Ö3", "oe3"), RadioBrowser.variants(" Ö3 "))
        assertEquals(listOf("oe3", "ö3"), RadioBrowser.variants("oe3"))
        assertEquals(listOf("Ö3", "oe3"), RadioBrowser.variants("Ö3"))
        assertEquals(listOf("bae", "bä"), RadioBrowser.variants("bae"))
        assertTrue(RadioBrowser.variants("a").isEmpty())
    }

    @Test
    fun `old default host is migrated and a custom host stays`() {
        assertEquals(RadioBrowser.DEFAULT_HOST, RadioBrowser.migrateHost("https://de1.radio-browser.info/"))
        assertEquals(RadioBrowser.DEFAULT_HOST, RadioBrowser.migrateHost("http://de1.radio-browser.info"))
        assertEquals(RadioBrowser.DEFAULT_HOST, RadioBrowser.migrateHost("de1.radio-browser.info"))
        assertEquals(RadioBrowser.DEFAULT_HOST, RadioBrowser.migrateHost(null))
        assertEquals("https://radio.example", RadioBrowser.migrateHost("https://radio.example/"))
    }

    @Test
    fun `stations are read without a generated serializer`() {
        val stations = RadioBrowser.parseStations(
            """
            [
              {"name":"ORF Hitradio Ö3","url_resolved":"https://example.test/oe3","country":"AT","stationuuid":"1","extra":{"x":1}},
              {"name":"ohne stream","url_resolved":"","country":"AT"},
              {"name":"Fallback","url":"https://example.test/fallback","country":"DE"}
            ]
            """.trimIndent()
        )
        assertEquals(listOf("ORF Hitradio Ö3", "Fallback"), stations.map { it.name })
        assertEquals("https://example.test/fallback", stations[1].streamUrl)
    }

    @Test
    fun `oe3 also asks for o-umlaut and keeps the first hit`() = runTest {
        val names = mutableListOf<String>()
        val hosts = mutableListOf<String>()
        val http = client { request ->
            hosts += request.url.host
            names += request.url.parameters["name"].orEmpty()
            assertEquals("Morgo/1.6.1", request.headers[HttpHeaders.UserAgent])
            assertEquals("/json/stations/search", request.url.encodedPath)
            assertEquals("true", request.url.parameters["hidebroken"])
            assertEquals("clickcount", request.url.parameters["order"])
            assertEquals("true", request.url.parameters["reverse"])
            when (request.url.parameters["name"]) {
                "oe3" -> json("[]")
                "ö3" -> json(
                    """[{"name":"ORF Hitradio Ö3","url_resolved":"https://example.test/oe3","country":"AT"}]"""
                )
                else -> respond("nein", HttpStatusCode.NotFound)
            }
        }

        val result = RadioBrowser.search(http, "oe3", RadioBrowser.DEFAULT_HOST, RadioBrowser.userAgent("1.6.1"))

        assertEquals(listOf("oe3", "ö3"), names)
        assertEquals(listOf("de1.api.radio-browser.info"), hosts.distinct())
        val stations = result.getOrThrow()
        assertEquals(listOf("ORF Hitradio Ö3"), stations.map { it.name })
        assertEquals("AT", stations.single().country)
    }

    @Test
    fun `a broken host falls through to the server list`() = runTest {
        val hosts = mutableListOf<String>()
        val http = client { request ->
            hosts += request.url.host
            assertEquals("Morgo/1.6.1", request.headers[HttpHeaders.UserAgent])
            when (request.url.host) {
                "de1.api.radio-browser.info",
                "de2.api.radio-browser.info",
                "at1.api.radio-browser.info" -> throw SSLHandshakeException("handshake")
                "fi1.api.radio-browser.info" -> respond("down", HttpStatusCode.BadGateway)
                "all.api.radio-browser.info" -> json(
                    """[{"name":"nl1.api.radio-browser.info"},{"name":"de1.api.radio-browser.info"}]"""
                )
                "nl1.api.radio-browser.info" -> json(
                    """[{"name":"ORF Hitradio Ö3","url_resolved":"https://example.test/oe3","country":"AT"}]"""
                )
                else -> respond("nein", HttpStatusCode.NotFound)
            }
        }

        val result = RadioBrowser.search(
            http,
            "O3",
            "https://de1.radio-browser.info",
            RadioBrowser.userAgent("1.6.1")
        )

        assertEquals("ORF Hitradio Ö3", result.getOrThrow().single().name)
        assertEquals(
            listOf(
                "de1.api.radio-browser.info",
                "de2.api.radio-browser.info",
                "fi1.api.radio-browser.info",
                "at1.api.radio-browser.info",
                "all.api.radio-browser.info",
                "nl1.api.radio-browser.info"
            ),
            hosts
        )
    }

    @Test
    fun `an empty answer is not an error and a dead directory is`() = runTest {
        val http = client { request ->
            if (request.url.host == "de1.api.radio-browser.info") json("[]")
            else throw SSLHandshakeException("handshake")
        }
        val empty = RadioBrowser.search(http, "O3", RadioBrowser.DEFAULT_HOST, RadioBrowser.userAgent("1.6.1"))
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())

        val dead = client { throw SSLHandshakeException("handshake") }
        val failed = RadioBrowser.search(dead, "O3", RadioBrowser.DEFAULT_HOST, RadioBrowser.userAgent("1.6.1"))
        assertTrue(failed.isFailure)
        val message = ConnectionErrors.describe(failed.exceptionOrNull(), ConnectionErrors.Service.RADIO).message
        assertEquals("Radiosuche fehlgeschlagen. Der Server nimmt die Verbindung nicht an.", message)
    }

    @Test
    fun `a short query does not touch the network`() = runTest {
        var called = false
        val http = client {
            called = true
            json("[]")
        }
        val result = RadioBrowser.search(http, "a", RadioBrowser.DEFAULT_HOST, RadioBrowser.userAgent("1.6.1"))
        assertTrue(result.getOrThrow().isEmpty())
        assertFalse(called)
    }

    private fun client(
        handler: io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData
    ): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) { expectSuccess = false }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.json(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
}
