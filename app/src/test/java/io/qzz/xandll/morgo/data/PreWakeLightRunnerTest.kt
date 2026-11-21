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
import org.junit.Test

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

        assertEquals("Wird ausgeführt", started.message)
        assertEquals("Licht ist fertig", toggled.message)
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
}
