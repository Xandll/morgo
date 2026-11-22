package io.qzz.xandll.morgo.presentation

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.presentation.settings.FullScreenPermissionNote
import io.qzz.xandll.morgo.presentation.settings.PreWakeLightCard
import io.qzz.xandll.morgo.presentation.settings.WakeSoundCard
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w1080dp-h2400dp")
class AlarmHintTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `test card explains the heads-up and the watch`() {
        compose.setContent {
            MorgoTheme {
                WakeSoundCard(
                    alarm = AlarmSettings(
                        wakeSound = WakeSound.MUSIC,
                        music = MusicAlarmSettings(kind = MusicKind.STREAM, streamTitle = "Beispielradio")
                    ),
                    onChange = {},
                    radioResults = emptyList(),
                    onSearchRadio = {},
                    onPickApp = {},
                    onTest = {}
                )
            }
        }
        compose.onNodeWithText("Ein entsperrtes Handy in Benutzung zeigt, wie die Google Uhr, nur die Benachrichtigung oben.")
            .assertIsDisplayed()
        compose.onNodeWithText("Die Uhr hält Benachrichtigungen zurück, solange das Handy aktiv benutzt wird.")
            .assertIsDisplayed()
    }

    @Test
    fun `missing full screen permission offers the system setting`() {
        var opened = false
        compose.setContent {
            MorgoTheme {
                FullScreenPermissionNote(onAllow = { opened = true })
            }
        }
        compose.onNodeWithText("Vollbild-Benachrichtigungen erlauben").performClick()
        assertTrue(opened)
        compose.onNodeWithText("Vollbild-Benachrichtigungen sind aus. Ab Android 14 bleibt der Wecker bei gesperrtem Bildschirm dann eine normale Benachrichtigung und erscheint nicht groß darüber.")
            .assertIsDisplayed()
    }

    @Test
    fun `light test shows the step and cancels`() {
        var cancelled = false
        compose.setContent {
            MorgoTheme {
                PreWakeLightCard(
                    light = PreWakeLightSettings(
                        enabled = true,
                        entityIds = listOf("light.lampe"),
                        toggleCount = 4
                    ),
                    entities = listOf(HomeAssistantEntity("light.lampe", "Lampe", "off", io.qzz.xandll.morgo.domain.model.EntityType.LIGHT)),
                    onChange = {},
                    onLoad = {},
                    onTest = {},
                    testRunning = true,
                    testProgress = "Schritt 3/5",
                    testErrors = listOf("Schritt 2/5: light.lampe: Nicht erreichbar"),
                    onCancel = { cancelled = true }
                )
            }
        }
        compose.onNodeWithText("Schritt 3/5").assertIsDisplayed()
        compose.onNodeWithText("Schritt 2/5: light.lampe: Nicht erreichbar").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        assertTrue(cancelled)
    }
}
