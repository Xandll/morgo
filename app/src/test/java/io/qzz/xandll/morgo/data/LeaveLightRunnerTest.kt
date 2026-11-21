package io.qzz.xandll.morgo.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.qzz.xandll.morgo.data.datasource.HafasClient
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.LeaveLightRunner
import io.qzz.xandll.morgo.domain.model.LeaveOffChoice
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaveLightRunnerTest {

    @Test
    fun `turn off is idempotent and a script is called the configured times`() = runTest {
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
        val settingsRepository = SettingsRepository.forPrefs(MemoryPrefs(), MemoryPrefs())
        val current = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            current.copy(
                homeAssistant = current.homeAssistant.copy(
                    url = "https://home.example",
                    token = "token",
                    preWake = PreWakeLightSettings(
                        enabled = true,
                        entityIds = listOf("light.schlafzimmer", "script.licht_1", "scene.morgen"),
                        leaveOffEnabled = true,
                        leaveOffOffsetMinutes = 2,
                        leaveOff = mapOf("script.licht_1" to LeaveOffChoice(calls = 2))
                    )
                )
            )
        )
        val runner = LeaveLightRunner(
            MorgoRepository(WebUntisClient(), HafasClient(), home),
            settingsRepository
        )

        assertEquals("Licht ist aus", runner.turnOff())
        assertEquals(
            listOf(
                "/api/services/light/turn_off",
                "/api/services/script/turn_on",
                "/api/services/script/turn_on"
            ),
            calls
        )
    }
}
