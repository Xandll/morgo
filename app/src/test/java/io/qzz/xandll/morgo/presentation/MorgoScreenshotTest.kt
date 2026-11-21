package io.qzz.xandll.morgo.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.qzz.xandll.morgo.domain.model.AlarmPlan
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.ConnectionPoint
import io.qzz.xandll.morgo.domain.model.ConnectionStatus
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.HomeAssistantSettings
import io.qzz.xandll.morgo.domain.model.LightIntervalUnit
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import io.qzz.xandll.morgo.domain.model.EntityType
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.LessonSlot
import io.qzz.xandll.morgo.domain.model.LessonStatus
import io.qzz.xandll.morgo.domain.model.Station
import io.qzz.xandll.morgo.domain.model.TrainConnection
import io.qzz.xandll.morgo.domain.model.TrainSettings
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import io.qzz.xandll.morgo.domain.model.WebUntisSettings
import io.qzz.xandll.morgo.presentation.alarm.AlarmScreen
import io.qzz.xandll.morgo.presentation.home.HomeUiState
import io.qzz.xandll.morgo.presentation.home.MorningDashboard
import io.qzz.xandll.morgo.presentation.home.TomorrowEditor
import io.qzz.xandll.morgo.presentation.onboarding.OnboardingFrame
import io.qzz.xandll.morgo.presentation.settings.LessonGridStatus
import io.qzz.xandll.morgo.presentation.settings.PreWakeLightCard
import io.qzz.xandll.morgo.presentation.settings.SettingsBody
import io.qzz.xandll.morgo.presentation.settings.WebUntisSettings
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class MorgoScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(softButtons = false),
        showSystemUi = false
    )

    @Test fun dashboardLight() = shot("dashboard-light", false) { Dashboard(ringing = true, delayed = false) }
    @Test fun dashboardDark() = shot("dashboard-dark", true) { Dashboard(ringing = true, delayed = true) }
    @Test fun dashboardQuietLight() = shot("dashboard-quiet-light", false) { Dashboard(ringing = false, delayed = false) }
    @Test fun dashboardQuietDark() = shot("dashboard-quiet-dark", true) { Dashboard(ringing = false, delayed = false) }
    @Test fun dashboardLoadingLight() = shot("dashboard-loading-light", false) { Loading() }
    @Test fun dashboardLoadingDark() = shot("dashboard-loading-dark", true) { Loading() }

    @Test fun settingsAlarmLight() = shot("settings-wecker-light", false) { Settings(0) }
    @Test fun settingsAlarmDark() = shot("settings-wecker-dark", true) { Settings(0) }
    @Test fun settingsTrainLight() = shot("settings-zug-light", false) { Settings(1) }
    @Test fun settingsTrainDark() = shot("settings-zug-dark", true) { Settings(1) }
    @Test fun settingsHomeLight() = shot("settings-smarthome-light", false) { Settings(2) }
    @Test fun preWakeLightSettings() = shot("licht-vor-dem-wecken-light", false) { LightSettings() }
    @Test fun preWakeLightSettingsDark() = shot("licht-vor-dem-wecken-dark", true) { LightSettings() }
    @Test fun settingsHomeDark() = shot("settings-smarthome-dark", true) { Settings(2) }
    @Test fun settingsHomeEditLight() = shot("settings-smarthome-edit-light", false) { Settings(2, homeEdit = true) }
    @Test fun settingsHomeEditDark() = shot("settings-smarthome-edit-dark", true) { Settings(2, homeEdit = true) }
    @Test fun settingsAccountsLight() = shot("settings-konten-light", false) { Settings(3) }
    @Test fun settingsAccountsDark() = shot("settings-konten-dark", true) { Settings(3) }
    @Test fun settingsAccountsEditLight() = shot("settings-konten-edit-light", false) { Settings(3, untisEdit = true) }
    @Test fun settingsAccountsEditDark() = shot("settings-konten-edit-dark", true) { Settings(3, untisEdit = true) }

    @Test fun wakeTableLight() = shot("weckzeiten-light", false) { Settings(0, extraRules = true) }
    @Test fun wakeTableDark() = shot("weckzeiten-dark", true) { Settings(0, extraRules = true) }

    @Test fun eveningLight() = shot("abend-dialog-light", false) { TomorrowEditor({}, {}, {}, {}) }
    @Test fun eveningDark() = shot("abend-dialog-dark", true) { TomorrowEditor({}, {}, {}, {}) }

    @Test fun onboardingLight() = shot("onboarding-light", false) { Welcome() }
    @Test fun onboardingDark() = shot("onboarding-dark", true) { Welcome() }
    @Test fun onboardingPermissionsLight() = shot("onboarding-freigaben-light", false) { Permissions() }

    @Test fun alarmLight() = shot("wecker-screen-light", false) {
        AlarmScreen(5, {}, {}, headline = "Weckruf", time = LocalTime.of(10, 35))
    }
    @Test fun alarmDark() = shot("wecker-screen-dark", true) {
        AlarmScreen(5, {}, {}, headline = "Plan B", note = "Zug zu spät", time = LocalTime.of(5, 40))
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val tall = name.startsWith("abend")
        paparazzi.snapshot(name = name) {
            MorgoTheme(darkTheme = dark) {
                if (name.startsWith("licht")) {
                    Surface(Modifier.fillMaxWidth().padding(16.dp)) { content() }
                } else {
                    Surface(Modifier.fillMaxWidth().height(if (tall) 920.dp else 844.dp)) { content() }
                }
            }
        }
    }

    @Composable
    private fun Dashboard(ringing: Boolean, delayed: Boolean) {
        val date = LocalDate.now().plusDays(1)
        val lesson = Lesson(date, LocalTime.of(7, 45), LocalTime.of(8, 30), "Deutsch", "Aigner", "12", LessonStatus.REGULAR)
        val train = TrainConnection(
            departure = ConnectionPoint("Heimstadt", LocalTime.of(6, 12), if (delayed) LocalTime.of(6, 19) else null, "2"),
            arrival = ConnectionPoint("Schulstadt", LocalTime.of(6, 31), null, "3"),
            duration = 19,
            changes = 0,
            status = if (delayed) ConnectionStatus.DELAYED else ConnectionStatus.ON_TIME
        )
        val plan = if (ringing) {
            AlarmPlan(LocalTime.of(5, 30), "1. Stunde 07:45", lesson, train, rings = true, lessonNumber = 1)
        } else {
            AlarmPlan(LocalTime.of(7, 0), "Wochenende", null, null, rings = false)
        }
        MorningDashboard(
            state = HomeUiState.Ready(
                date,
                plan,
                listOf("light.schlafzimmer"),
                false,
                lightHint = if (ringing) "Licht geht um 05:20 an" else null,
                extraLines = if (ringing) listOf("05:45 Turnzeug") else emptyList()
            ),
            entities = listOf(HomeAssistantEntity("light.schlafzimmer", "Schlafzimmer", "off", EntityType.LIGHT)),
            onSettings = {},
            onRefresh = {},
            onChangeTomorrow = {},
            onTurnOff = {},
            onActivate = {}
        )
    }

    @Composable
    private fun Loading() {
        MorningDashboard(HomeUiState.Loading, emptyList(), {}, {}, {}, {}, {})
    }

    @Composable
    private fun Settings(tab: Int, untisEdit: Boolean = false, homeEdit: Boolean = false, extraRules: Boolean = false) {
        val slots = listOf(
            LessonSlot(1, mapOf(DayOfWeek.MONDAY to LocalTime.of(7, 45))),
            LessonSlot(2, mapOf(DayOfWeek.MONDAY to LocalTime.of(8, 35))),
            LessonSlot(3, mapOf(DayOfWeek.MONDAY to LocalTime.of(9, 30)))
        )
        val rules = if (extraRules) {
            listOf(
                WakeTimeRule(1, LocalTime.of(6, 15), LocalTime.of(6, 50), "S3"),
                WakeTimeRule(2, LocalTime.of(7, 5))
            )
        } else {
            listOf(WakeTimeRule(1, LocalTime.of(6, 15)))
        }
        Box(Modifier.fillMaxWidth()) {
            SettingsBody(
                settings = sampleSettings(rules),
                tab = tab,
                onTab = {},
                onBack = {},
                secureMessage = null,
                onAlarm = {},
                onTrain = {},
                onHome = {},
                onWebUntis = {},
                onApp = {},
                lessonSlots = slots,
                lessonStatus = LessonGridStatus.Ready,
                lessonDetail = null,
                onWakeRule = {},
                schoolResults = emptyList(),
                onSearchSchools = {},
                onSelectSchool = {},
                onClearSchools = {},
                onParseQr = {},
                onParseServer = {},
                onTestUntis = {},
                testingUntis = false,
                stationResults = emptyList(),
                stationLoading = false,
                stationError = null,
                onSearchStations = {},
                onTestTrain = {},
                testingTrain = false,
                entities = listOf(
                    HomeAssistantEntity("light.schlafzimmer", "Schlafzimmer", "off", EntityType.LIGHT),
                    HomeAssistantEntity("switch.nachtlicht", "Nachtlicht", "off", EntityType.SWITCH),
                    HomeAssistantEntity("script.licht_1", "Licht 1", "off", EntityType.SCRIPT),
                    HomeAssistantEntity("scene.morgen", "Morgenlicht", "off", EntityType.SCENE)
                ),
                onLoadEntities = {},
                onTestHome = {},
                testingHome = false,
                testResult = null,
                forceUntisEditor = untisEdit,
                forceHomeEditor = homeEdit
            )
        }
    }

    @Composable
    private fun LightSettings() {
        PreWakeLightCard(
            light = PreWakeLightSettings(
                enabled = true,
                entityIds = listOf("script.licht_1"),
                leadMinutes = 10,
                toggleCount = 4,
                intervalAmount = 20,
                intervalUnit = LightIntervalUnit.SECONDS,
                brightness = null
            ),
            entities = listOf(
                HomeAssistantEntity("script.licht_1", "Licht 1", "off", EntityType.SCRIPT),
                HomeAssistantEntity("light.schlafzimmer", "Schlafzimmer", "off", EntityType.LIGHT),
                HomeAssistantEntity("input_boolean.leselampe", "Leselampe", "off", EntityType.INPUT_BOOLEAN),
                HomeAssistantEntity("scene.morgen", "Morgenlicht", "off", EntityType.SCENE)
            ),
            initialQuery = "",
            onChange = {},
            onLoad = {},
            onTest = {}
        )
    }

    @Composable
    private fun Welcome() {
        OnboardingFrame(0, "WebUntis", "Schule und Zugang. Der QR-Code bleibt gespeichert.", {}, {}, false) {
            WebUntisSettings(
                settings = WebUntisSettings(),
                onUpdate = {},
                onTest = {},
                onSearchSchools = {},
                schoolSearchResults = emptyList(),
                onSelectSchool = {},
                onClearSchoolSearch = {},
                onParseQRCode = {},
                onParseServerUrl = {},
                testing = false,
                forceEditing = true
            )
        }
    }

    @Composable
    private fun Permissions() {
        OnboardingFrame(3, "Freigaben", "Damit der Abend-Check und der Wecker pünktlich sind.", {}, {}, false) {
            androidx.compose.material3.Text("Benachrichtigungen und Akku-Einstellungen.", modifier = Modifier.padding(8.dp))
        }
    }

    private fun sampleSettings(rules: List<WakeTimeRule>) = AppSettings(
        webUntis = WebUntisSettings("beispiel.webuntis.com", "Beispielschule", "schueler", "••••••••", useSecret = true, secret = "JBSWY3DPEHPK3PXP"),
        train = TrainSettings(Station("1", "Heimstadt"), Station("2", "Schulstadt Hbf"), 12, 30),
        homeAssistant = HomeAssistantSettings(
            "https://home.example",
            "token",
            listOf("scene.morgen"),
            PreWakeLightSettings(
                enabled = true,
                entityIds = listOf("light.schlafzimmer"),
                leadMinutes = 10,
                toggleCount = 4,
                intervalAmount = 20,
                intervalUnit = LightIntervalUnit.SECONDS,
                brightness = 204
            )
        ),
        alarm = AlarmSettings(
            enabled = true,
            wakeTimeRules = rules,
            eveningCheckEnabled = true,
            eveningCheckTime = LocalTime.of(20, 0)
        )
    )
}
