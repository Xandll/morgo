package io.qzz.xandll.morgo.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import io.qzz.xandll.morgo.data.datasource.RadioStation
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.ExtraAlarm
import io.qzz.xandll.morgo.domain.model.MusicAlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.domain.model.LessonSlot
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import io.qzz.xandll.morgo.presentation.settings.ExtraAlarmsCard
import io.qzz.xandll.morgo.presentation.settings.FollowUpCard
import io.qzz.xandll.morgo.presentation.settings.LessonGridStatus
import io.qzz.xandll.morgo.presentation.settings.WakeSoundCard
import io.qzz.xandll.morgo.presentation.settings.WakeTimeRulesSection
import java.time.DayOfWeek
import java.time.LocalTime
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import org.junit.Rule
import org.junit.Test

class WakeOptionsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 4600, softButtons = false),
        renderingMode = SessionParams.RenderingMode.SHRINK,
        showSystemUi = false
    )

    @Test fun followUpLight() = shot("nachwecker-light", false) { FollowUpSample(false) }
    @Test fun followUpDark() = shot("nachwecker-dark", true) { FollowUpSample(false) }
    @Test fun followUpMusicLight() = shot("nachwecker-musik-light", false) { FollowUpSample(true) }
    @Test fun followUpMusicDark() = shot("nachwecker-musik-dark", true) { FollowUpSample(true) }
    @Test fun musicLight() = shot("musik-wecker-light", false) { MusicSample() }
    @Test fun musicDark() = shot("musik-wecker-dark", true) { MusicSample() }
    @Test fun extraLight() = shot("extra-wecker-light", false) { ExtraSample() }
    @Test fun extraDark() = shot("extra-wecker-dark", true) { ExtraSample() }
    @Test fun wakeTableLight() = shot("weckzeit-tabelle-light", false) { WakeTableSample() }
    @Test fun wakeTableDark() = shot("weckzeit-tabelle-dark", true) { WakeTableSample() }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            MorgoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxWidth().padding(16.dp)) { content() }
            }
        }
    }

    @Composable
    private fun FollowUpSample(music: Boolean) {
        FollowUpCard(
            alarm = AlarmSettings(
                followUpEnabled = true,
                followUpMinutes = 5,
                wakeSound = if (music) WakeSound.MUSIC else WakeSound.RINGTONE
            ),
            onChange = {}
        )
    }

    @Composable
    private fun MusicSample() {
        WakeSoundCard(
            alarm = AlarmSettings(
                wakeSound = WakeSound.MUSIC,
                music = MusicAlarmSettings(
                    kind = MusicKind.STREAM,
                    streamTitle = "Hitradio Ö3",
                    streamUrl = "https://orf-live.ors-shoutcast.at/oe3-q2a",
                    rampSeconds = 30,
                    fallbackSeconds = 20,
                    volumePercent = 70
                )
            ),
            onChange = {},
            radioResults = listOf(
                RadioStation("Hitradio Ö3", "https://orf-live.ors-shoutcast.at/oe3-q2a", "Austria"),
                RadioStation("FM4", "https://orf-live.ors-shoutcast.at/fm4-q2a", "Austria")
            ),
            onSearchRadio = {},
            onPickApp = {},
            onTest = {}
        )
    }

    @Composable
    private fun WakeTableSample() {
        WakeTimeRulesSection(
            rules = listOf(WakeTimeRule(lessonNumber = 1, wakeTime = LocalTime.of(6, 15))),
            slots = listOf(
                LessonSlot(
                    number = 1,
                    startByDay = mapOf(
                        DayOfWeek.MONDAY to LocalTime.of(7, 45),
                        DayOfWeek.TUESDAY to LocalTime.of(8, 30),
                        DayOfWeek.WEDNESDAY to LocalTime.of(7, 45)
                    )
                ),
                LessonSlot(
                    number = 2,
                    startByDay = mapOf(
                        DayOfWeek.MONDAY to LocalTime.of(8, 35),
                        DayOfWeek.TUESDAY to LocalTime.of(8, 35)
                    )
                )
            ),
            status = LessonGridStatus.Ready,
            detail = null,
            onUpdateRule = {}
        )
    }

    @Composable
    private fun ExtraSample() {
        ExtraAlarmsCard(
            alarm = AlarmSettings(
                extraAlarms = listOf(ExtraAlarm("1", "Turnzeug einpacken", "Turnen", 15, true))
            ),
            subjects = listOf("Deutsch", "Turnen"),
            onChange = {},
            onLoadSubjects = {},
            expanded = true
        )
    }
}
