package io.qzz.xandll.morgo.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.qzz.xandll.morgo.data.update.GithubReleaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseClientTest {
    @Test
    fun `latest reads a public release without a token`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://api.github.com/repos/Xandll/morgo/releases/latest", request.url.toString())
            assertTrue(request.headers[HttpHeaders.Authorization] == null)
            respond(
                content = """
                    {"tag_name":"v1.7.0","body":"Hinweis","assets":[
                      {"name":"morgo-release.apk","browser_download_url":"https://github.com/Xandll/morgo/releases/download/v1.7.0/morgo-release.apk"}
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = client(engine)
        val release = client.latest("Xandll", "morgo").getOrThrow()
        assertEquals("v1.7.0", release.tag)
        assertEquals(null, release.sha256Url)
    }

    @Test
    fun `missing release is a visible failure`() = runTest {
        val engine = MockEngine {
            respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val error = client(engine).latest("Xandll", "morgo").exceptionOrNull()
        assertEquals("Kein Release gefunden", error?.message)
    }

    private fun client(engine: MockEngine): GithubReleaseClient {
        val http = HttpClient(engine)
        val field = GithubReleaseClient::class.java.getDeclaredField("http")
        field.isAccessible = true
        val created = GithubReleaseClient()
        val old = field.get(created) as HttpClient
        field.set(created, http)
        old.close()
        return created
    }
}
