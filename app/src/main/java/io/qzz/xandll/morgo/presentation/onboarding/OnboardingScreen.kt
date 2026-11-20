package io.qzz.xandll.morgo.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.qzz.xandll.morgo.presentation.settings.AlarmSettings
import io.qzz.xandll.morgo.presentation.settings.ConnectionTestTarget
import io.qzz.xandll.morgo.presentation.settings.HomeAssistantSettings
import io.qzz.xandll.morgo.presentation.settings.SettingsViewModel
import io.qzz.xandll.morgo.presentation.settings.TrainSettings
import io.qzz.xandll.morgo.presentation.settings.WakeTimeRulesSection
import io.qzz.xandll.morgo.presentation.settings.WebUntisSettings
import io.qzz.xandll.morgo.presentation.ui.sunriseBrush

private val titles = listOf(
    "WebUntis" to "Schule und Zugang. Der QR-Code bleibt gespeichert.",
    "Zug" to "Von wo du fährst und wie lange du zu Fuß brauchst.",
    "Weckzeiten" to "Pro Stunde eine Zeit, oder den automatischen Vorlauf.",
    "Freigaben" to "Damit der Abend-Check und der Wecker pünktlich sind.",
    "Smarthome" to "Optional. Licht und Szenen zum Aufwachen."
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var page by remember { mutableIntStateOf(0) }
    val settings by viewModel.settings.collectAsState()
    val testBusy by viewModel.connectionTestBusy.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()
    val entities by viewModel.homeAssistantEntities.collectAsState()
    val lessonSlots by viewModel.lessonSlots.collectAsState()
    val lessonGridStatus by viewModel.lessonGridStatus.collectAsState()
    val lessonGridDetail by viewModel.lessonGridDetail.collectAsState()
    OnboardingFrame(
        page = page,
        title = titles[page].first,
        subtitle = titles[page].second,
        onSkip = { viewModel.completeOnboarding(); onFinished() },
        onNext = {
            if (page < titles.lastIndex) page += 1 else {
                viewModel.completeOnboarding()
                onFinished()
            }
        },
        last = page == titles.lastIndex
    ) {
        when (page) {
            0 -> WebUntisSettings(
                settings = settings.webUntis,
                onUpdate = { viewModel.updateSettings(settings.copy(webUntis = it)) },
                onTest = { viewModel.testWebUntisConnection() },
                onSearchSchools = { viewModel.searchSchools(it) },
                schoolSearchResults = viewModel.schoolSearchResults.collectAsState().value,
                onSelectSchool = { viewModel.selectSchool(it) },
                onClearSchoolSearch = { viewModel.clearSchoolSearch() },
                onParseQRCode = { viewModel.parseQRCode(it) },
                onParseServerUrl = { viewModel.parseServerUrl(it) },
                testing = testBusy == ConnectionTestTarget.WEBUNTIS,
                testResult = testResult,
                forceEditing = true
            )
            1 -> TrainSettings(
                settings = settings.train,
                onUpdate = { viewModel.updateSettings(settings.copy(train = it)) },
                onSearchStations = { viewModel.searchStations(it) },
                searchResults = viewModel.stationSearchResults.collectAsState().value,
                searchLoading = viewModel.stationSearchLoading.collectAsState().value,
                searchError = viewModel.stationSearchError.collectAsState().value,
                onTest = { viewModel.testHafasConnection() },
                testing = testBusy == ConnectionTestTarget.HAFAS
            )
            2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlarmSettings(settings.alarm) { viewModel.updateSettings(settings.copy(alarm = it)) }
                WakeTimeRulesSection(
                    rules = settings.alarm.wakeTimeRules,
                    slots = lessonSlots,
                    status = lessonGridStatus,
                    detail = lessonGridDetail,
                    onUpdateRule = { viewModel.setWakeTimeRule(it) }
                )
            }
            3 -> PermissionStep()
            else -> HomeAssistantSettings(
                settings = settings.homeAssistant,
                onUpdate = { viewModel.updateSettings(settings.copy(homeAssistant = it)) },
                onTest = { viewModel.testHomeAssistantConnection() },
                testing = testBusy == ConnectionTestTarget.HOME_ASSISTANT,
                availableEntities = entities,
                onLoadEntities = { viewModel.loadHomeAssistantEntities() },
                testResult = testResult,
                forceEditing = settings.homeAssistant.url.isBlank()
            )
        }
    }
}

@Composable
fun OnboardingFrame(
    page: Int,
    title: String,
    subtitle: String,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    last: Boolean,
    content: @Composable () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val dark = (0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue) < 0.5f
    val ink = if (dark) Color.White else Color(0xFF1E2350)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(sunriseBrush(dark)).statusBarsPadding().padding(24.dp)) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(titles.size) { index ->
                        Box(
                            Modifier
                                .size(if (index == page) 22.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (index == page) ink else ink.copy(alpha = 0.35f))
                        )
                    }
                }
                Text(title, style = MaterialTheme.typography.headlineLarge, color = ink, modifier = Modifier.padding(top = 18.dp))
                Text(subtitle, color = ink.copy(alpha = 0.8f), modifier = Modifier.padding(top = 6.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text("Überspringen") }
            Button(onClick = onNext, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (last) "Fertig" else "Weiter") }
        }
    }
}

@Composable
private fun PermissionStep() {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Benachrichtigungen, dann läuft der Abend-Check. Der Wecker darf exakt klingeln, das ist schon erlaubt.")
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Benachrichtigungen erlauben") }
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Akku-Einstellungen öffnen") }
    }
}
