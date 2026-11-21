package io.qzz.xandll.morgo.data

import android.content.SharedPreferences
import io.qzz.xandll.morgo.data.datasource.RadioBrowser
import io.qzz.xandll.morgo.domain.model.AlarmFace
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

    @Test
    fun `dynamic color reaches the settings flow immediately`() {
        val regular = MemoryPrefs()
        val repository = SettingsRepository.forPrefs(regular, MemoryPrefs())
        assertFalse(repository.settingsFlow.value.useDynamicColor)
        repository.saveSettings(repository.loadSettings().copy(useDynamicColor = true))
        assertTrue(repository.settingsFlow.value.useDynamicColor)
        assertTrue(SettingsRepository.forPrefs(regular, MemoryPrefs()).loadSettings().useDynamicColor)
    }

    @Test
    fun `stored radio browser default moves to the api host`() {
        val regular = MemoryPrefs()
        regular.edit().putString("radio_browser_host", "https://de1.radio-browser.info/").apply()
        val loaded = SettingsRepository.forPrefs(regular, MemoryPrefs()).loadSettings()
        assertEquals(RadioBrowser.DEFAULT_HOST, loaded.alarm.music.radioBrowserHost)
        assertEquals(RadioBrowser.DEFAULT_HOST, regular.getString("radio_browser_host", null))
    }

    @Test
    fun `plan b title is stored and a blank title falls back`() {
        val regular = MemoryPrefs()
        val repository = SettingsRepository.forPrefs(regular, MemoryPrefs())
        assertEquals(AlarmFace.DEFAULT_TITLE, repository.alarmTitle())
        repository.setAlarmTitle("Plan B")
        assertEquals("Plan B", SettingsRepository.forPrefs(regular, MemoryPrefs()).alarmTitle())
        repository.setAlarmTitle("  ")
        assertEquals(AlarmFace.DEFAULT_TITLE, repository.alarmTitle())
    }

    @Test
    fun `a custom radio host is kept`() {
        val regular = MemoryPrefs()
        regular.edit().putString("radio_browser_host", "https://radio.example").apply()
        val loaded = SettingsRepository.forPrefs(regular, MemoryPrefs()).loadSettings()
        assertEquals("https://radio.example", loaded.alarm.music.radioBrowserHost)
    }
}
