package io.qzz.xandll.morgo.data

import android.content.SharedPreferences
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.WebUntisSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `saved credentials survive a new repository`() {
        val regular = MemoryPrefs()
        val encrypted = MemoryPrefs()
        val original = AppSettings(
            webUntis = WebUntisSettings(
                server = "beispiel.webuntis.com",
                schoolName = "Beispiel",
                username = "ada",
                password = "geheim",
                useSecret = true,
                secret = "JBSWY3DPEHPK3PXP"
            )
        )
        SettingsRepository.forPrefs(regular, encrypted).saveSettings(original)
        val loaded = SettingsRepository.forPrefs(regular, encrypted).loadSettings()
        assertEquals(original.webUntis, loaded.webUntis)
        assertFalse(loaded.webUntis.secret.isBlank())
    }

    @Test
    fun `decryption failure keeps the stored file and reports it`() {
        val regular = MemoryPrefs()
        val broken = object : SharedPreferences by MemoryPrefs() {
            override fun getString(key: String?, defValue: String?): String? {
                throw SecurityException("AEADBadTagException")
            }
            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                throw SecurityException("AEADBadTagException")
            }
        }
        val repository = SettingsRepository.forPrefs(regular, broken)
        val loaded = repository.loadSettings()
        assertTrue(repository.secureStorageBroken.value)
        assertEquals("", loaded.webUntis.username)
        assertEquals("", loaded.webUntis.secret)
        assertFalse(loaded.webUntis.useSecret)
    }
}
