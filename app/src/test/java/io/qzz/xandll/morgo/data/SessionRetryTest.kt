package io.qzz.xandll.morgo.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.qzz.xandll.morgo.data.datasource.HafasClient
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import io.qzz.xandll.morgo.data.model.WebUntisAuthResult
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.WebUntisSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionRetryTest {

    @Test
    fun `expired session logs in again and retries once`() = runTest {
        val webUntis = mockk<WebUntisClient>()
        val repository = MorgoRepository(
            webUntis,
            mockk<HafasClient>(relaxed = true),
            mockk<HomeAssistantClient>(relaxed = true)
        )
        val settings = WebUntisSettings(
            server = "beispiel.webuntis.com",
            schoolName = "Beispiel",
            username = "ada",
            password = "geheim"
        )
        val auth = WebUntisAuthResult(sessionId = "abc", personType = 5, personId = 7, displayName = "Ada")
        coEvery { webUntis.authenticate(any(), any(), any(), any()) } returns Result.success(auth)
        every { webUntis.clearSession() } returns Unit
        coEvery { webUntis.logout(any(), any()) } returns Unit
        coEvery {
            webUntis.getTimetable(any(), any(), any(), any(), any(), any())
        } returnsMany listOf(
            Result.failure(Exception("not authenticated")),
            Result.success(emptyList())
        )

        val result = repository.getTimetable(settings, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 23))

        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { webUntis.authenticate(any(), any(), any(), any()) }
    }
}
