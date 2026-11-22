package io.qzz.xandll.morgo.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.qzz.xandll.morgo.data.datasource.HafasClient
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.PreWakeLightRunner
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PreWakeLightRunnerTest {

    @Test
    fun `each script step is one call and a light toggles`() = runTest {
        val calls = mutableListOf<String>()
        val home = HomeAssistantClient(HttpClient(MockEngine { request ->
            calls += request.url.encodedPath
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        })
        val regular = MemoryPrefs()
        val settingsRepository = SettingsRepository.forPrefs(regular, MemoryPrefs())
        val current = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            current.copy(
                homeAssistant = current.homeAssistant.copy(
                    url = "https://home.example",
                    token = "token",
                    preWake = PreWakeLightSettings(
                        enabled = true,
                        entityIds = listOf("script.licht_1", "light.schlafzimmer"),
                        toggleCount = 1,
                        brightness = 80
                    )
                )
            )
        )
        val runner = PreWakeLightRunner(
            MorgoRepository(WebUntisClient(), HafasClient(), home),
            settingsRepository
        )

        val started = runner.runStep(0)
        val toggled = runner.runStep(1)

        assertEquals("Schritt 1/2", started.progress)
        assertEquals("Schritt 1/2", started.message)
        assertTrue(started.errors.isEmpty())
        assertEquals("Schritt 2/2", toggled.progress)
        assertEquals("Licht ist fertig", toggled.message)
        assertNull(toggled.nextDelay)
        assertEquals(
            listOf(
                "/api/services/script/turn_on",
                "/api/services/light/turn_on",
                "/api/services/script/turn_on",
                "/api/services/light/toggle"
            ),
            calls
        )
    }

    @Test
    fun `test runs every step and keeps going after one failure`() = runTest {
        val calls = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        val home = HomeAssistantClient(HttpClient(MockEngine { request ->
            calls += request.url.encodedPath
            val body = (request.body as? io.ktor.http.content.OutgoingContent.ByteArrayContent)
                ?.bytes()
                ?.decodeToString()
                .orEmpty()
            bodies += body
            if (body.contains("light.lampe") && request.url.encodedPath.endsWith("/turn_on")) {
                respond("no", HttpStatusCode.InternalServerError)
            } else {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        })
        val settingsRepository = SettingsRepository.forPrefs(MemoryPrefs(), MemoryPrefs())
        val current = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            current.copy(
                homeAssistant = current.homeAssistant.copy(
                    url = "https://home.example",
                    token = "token",
                    preWake = PreWakeLightSettings(
                        enabled = true,
                        entityIds = listOf("script.morgen", "light.lampe"),
                        toggleCount = 2,
                        intervalAmount = 30,
                        brightness = 80
                    )
                )
            )
        )
        val runner = PreWakeLightRunner(
            MorgoRepository(WebUntisClient(), HafasClient(), home),
            settingsRepository
        )
        val pauses = mutableListOf<Duration>()
        val updates = mutableListOf<io.qzz.xandll.morgo.data.service.LightSequenceUpdate>()
        var allow = true

        runner.runSequence(
            test = true,
            pause = { pauses += it },
            active = { allow },
            onUpdate = { updates += it }
        )

        assertEquals(
            listOf(
                "/api/services/script/turn_on",
                "/api/services/light/turn_on",
                "/api/services/script/turn_on",
                "/api/services/light/toggle",
                "/api/services/script/turn_on",
                "/api/services/light/toggle"
            ),
            calls
        )
        assertTrue(bodies[1].contains("\"brightness\":80"))
        assertTrue(bodies[1].contains("light.lampe"))
        assertTrue(bodies.none { it.contains("script.morgen") && it.contains("brightness") })
        assertEquals(listOf(Duration.ofSeconds(1), Duration.ofSeconds(1)), pauses)
        assertEquals(listOf("Schritt 1/3", "Schritt 2/3", "Schritt 3/3"), updates.map { it.progress })
        assertEquals(listOf("Schritt 1/3: light.lampe: Verbindung fehlgeschlagen"), updates[0].errors)
        assertEquals(updates[0].errors, updates[2].errors)
        assertTrue(updates[2].done)
        assertTrue(updates[2].errors.single().startsWith("Schritt 1/3:"))

        allow = true
        pauses.clear()
        var steps = 0
        runner.runSequence(
            test = true,
            pause = {
                pauses += it
                allow = false
            },
            active = { allow },
            onUpdate = { steps++ }
        )
        assertEquals(1, steps)
        assertEquals(listOf(Duration.ofSeconds(1)), pauses)
    }
}
