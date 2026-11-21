package io.qzz.xandll.morgo.presentation.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import io.qzz.xandll.morgo.data.service.WakeMusic
import io.qzz.xandll.morgo.data.service.WakeSoundService
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.HomeAssistantSettings
import io.qzz.xandll.morgo.domain.model.HomeAssistantTargets
import io.qzz.xandll.morgo.domain.model.LessonGrid
import io.qzz.xandll.morgo.domain.model.LessonSlot
import io.qzz.xandll.morgo.domain.model.Station
import io.qzz.xandll.morgo.domain.model.TrainSettings
import io.qzz.xandll.morgo.domain.model.WakeTable
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.domain.model.WebUntisSettings
import io.qzz.xandll.morgo.presentation.ui.IconBadge
import io.qzz.xandll.morgo.presentation.ui.InfoLine
import io.qzz.xandll.morgo.presentation.ui.SoftCard
import io.qzz.xandll.morgo.presentation.ui.StatusPill
import io.qzz.xandll.morgo.presentation.ui.StepperRow
import io.qzz.xandll.morgo.presentation.ui.TimeRow
import java.time.DayOfWeek
import java.time.LocalTime

private val tabs = listOf("Wecker", "Zug", "Smarthome", "Konten")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val testResult by viewModel.testConnectionResult.collectAsState()
    val testBusy by viewModel.connectionTestBusy.collectAsState()
    val entities by viewModel.homeAssistantEntities.collectAsState()
    val lessonSlots by viewModel.lessonSlots.collectAsState()
    val lessonGridStatus by viewModel.lessonGridStatus.collectAsState()
    val lessonGridDetail by viewModel.lessonGridDetail.collectAsState()
    val webUntis = settings.webUntis
    var tab by remember { mutableIntStateOf(initialTab) }
    val secureBroken by viewModel.secureStorageBroken.collectAsState()
    val lightsLoading by viewModel.lightsLoading.collectAsState()
    val lightsMessage by viewModel.lightsMessage.collectAsState()
    val updateBusy by viewModel.updateBusy.collectAsState()
    val updateNotice by viewModel.updateNotice.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fullScreenMissing by remember {
        mutableStateOf(io.qzz.xandll.morgo.data.service.FullScreenIntents.needsPermission(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fullScreenMissing = io.qzz.xandll.morgo.data.service.FullScreenIntents.needsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pickApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val component = result.data?.component ?: return@rememberLauncherForActivityResult
        val alarm = settings.alarm
        viewModel.updateSettings(
            settings.copy(
                alarm = alarm.copy(
                    music = alarm.music.copy(
                        appPackage = component.packageName,
                        appLabel = component.packageName.substringAfterLast('.')
                    )
                )
            )
        )
    }

    LaunchedEffect(tab) {
        if (tab == 2) viewModel.loadHomeAssistantEntities()
    }

    LaunchedEffect(
        webUntis.server,
        webUntis.schoolName,
        webUntis.username,
        webUntis.useSecret,
        webUntis.password.isNotBlank() || webUntis.secret.isNotBlank()
    ) {
        viewModel.loadLessonGrid()
    }

    SettingsBody(
        settings = settings,
        tab = tab,
        onTab = { tab = it },
        onBack = onNavigateBack,
        secureMessage = if (secureBroken) {
            viewModel.secureStorageMessage
                ?: "Zugangsdaten konnten nicht gelesen werden. Die gespeicherten Daten wurden nicht gelöscht."
        } else {
            null
        },
        onAlarm = { viewModel.updateSettings(settings.copy(alarm = it)) },
        onTrain = { viewModel.updateSettings(settings.copy(train = it)) },
        onHome = { viewModel.updateSettings(settings.copy(homeAssistant = it)) },
        onWebUntis = { viewModel.updateSettings(settings.copy(webUntis = it)) },
        onApp = { viewModel.updateSettings(it) },
        lessonSlots = lessonSlots,
        lessonStatus = lessonGridStatus,
        lessonDetail = lessonGridDetail,
        onWakeRule = { viewModel.setWakeTimeRule(it) },
        schoolResults = viewModel.schoolSearchResults.collectAsState().value,
        onSearchSchools = { viewModel.searchSchools(it) },
        onSelectSchool = { viewModel.selectSchool(it) },
        onClearSchools = { viewModel.clearSchoolSearch() },
        onParseQr = { viewModel.parseQRCode(it) },
        onParseServer = { viewModel.parseServerUrl(it) },
        onTestUntis = { viewModel.testWebUntisConnection() },
        testingUntis = testBusy == ConnectionTestTarget.WEBUNTIS,
        stationResults = viewModel.stationSearchResults.collectAsState().value,
        stationLoading = viewModel.stationSearchLoading.collectAsState().value,
        stationError = viewModel.stationSearchError.collectAsState().value,
        onSearchStations = { viewModel.searchStations(it) },
        onTestTrain = { viewModel.testHafasConnection() },
        testingTrain = testBusy == ConnectionTestTarget.HAFAS,
        entities = entities,
        onLoadEntities = { viewModel.loadHomeAssistantEntities(manual = true) },
        lightsLoading = lightsLoading,
        lightsMessage = lightsMessage,
        onTestHome = { viewModel.testHomeAssistantConnection() },
        testingHome = testBusy == ConnectionTestTarget.HOME_ASSISTANT,
        testResult = testResult,
        onTestLight = { viewModel.testPreWakeLight() },
        onTestLeaveLight = { viewModel.testLeaveLight() },
        radioResults = viewModel.radioStations.collectAsState().value,
        onSearchRadio = { viewModel.searchRadio(it) },
        onPickApp = { pickApp.launch(WakeMusic.pickIntent(settings.alarm.music.searchQuery)) },
        onTestSound = {
            val alarm = settings.alarm
            if (alarm.wakeSound == WakeSound.MUSIC && alarm.music.kind != MusicKind.STREAM) {
                if (!WakeMusic.open(context, alarm.music)) {
                    context.startService(
                        Intent(context, WakeSoundService::class.java).setAction(WakeSoundService.ACTION_MUSIC_FAILED)
                    )
                }
            }
            viewModel.testWakeSound()
        },
        subjects = viewModel.subjects.collectAsState().value,
        onLoadSubjects = { viewModel.loadSubjects() },
        onCheckUpdates = { viewModel.checkForUpdates() },
        updateBusy = updateBusy,
        fullScreenMissing = fullScreenMissing,
        onAllowFullScreen = {
            context.startActivity(io.qzz.xandll.morgo.data.service.FullScreenIntents.settingsIntent(context))
        }
    )

    viewModel.radioError.collectAsState().value?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearRadioError() },
            title = { Text("Radiosuche") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearRadioError() }) { Text("OK") }
            }
        )
    }

    updateNotice?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearUpdateNotice() },
            title = { Text("Updates") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearUpdateNotice() }) { Text("OK") }
            }
        )
    }

    testResult?.let { result ->
        ConnectionTestDialog(
            result = result,
            onDismiss = { viewModel.clearTestResult() },
            onRetry = if (result is ConnectionTestResult.Error && result.retryTarget != null) {
                { viewModel.retryConnectionTest() }
            } else {
                null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBody(
    settings: AppSettings,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    secureMessage: String?,
    onAlarm: (AlarmSettings) -> Unit,
    onTrain: (TrainSettings) -> Unit,
    onHome: (HomeAssistantSettings) -> Unit,
    onWebUntis: (WebUntisSettings) -> Unit,
    onApp: (AppSettings) -> Unit,
    lessonSlots: List<LessonSlot>,
    lessonStatus: LessonGridStatus,
    lessonDetail: String?,
    onWakeRule: (WakeTimeRule) -> Unit,
    schoolResults: List<io.qzz.xandll.morgo.data.model.WebUntisSchool>,
    onSearchSchools: (String) -> Unit,
    onSelectSchool: (io.qzz.xandll.morgo.data.model.WebUntisSchool) -> Unit,
    onClearSchools: () -> Unit,
    onParseQr: (String) -> Unit,
    onParseServer: (String) -> Unit,
    onTestUntis: () -> Unit,
    testingUntis: Boolean,
    stationResults: List<Station>,
    stationLoading: Boolean,
    stationError: String?,
    onSearchStations: (String) -> Unit,
    onTestTrain: () -> Unit,
    testingTrain: Boolean,
    entities: List<HomeAssistantEntity>,
    onLoadEntities: () -> Unit,
    onTestHome: () -> Unit,
    testingHome: Boolean,
    testResult: ConnectionTestResult?,
    forceUntisEditor: Boolean = false,
    forceHomeEditor: Boolean = false,
    showTopBar: Boolean = true,
    onTestLight: () -> Unit = {},
    radioResults: List<io.qzz.xandll.morgo.data.datasource.RadioStation> = emptyList(),
    onSearchRadio: (String) -> Unit = {},
    onPickApp: () -> Unit = {},
    onTestSound: () -> Unit = {},
    subjects: List<String> = emptyList(),
    onLoadSubjects: () -> Unit = {},
    lightsLoading: Boolean = false,
    lightsMessage: String? = null,
    onCheckUpdates: () -> Unit = {},
    updateBusy: Boolean = false,
    fullScreenMissing: Boolean = false,
    onAllowFullScreen: () -> Unit = {},
    onTestLeaveLight: () -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = tab.coerceIn(0, tabs.lastIndex)) { tabs.size }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != tab) onTab(pagerState.currentPage)
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.scrollToPage(tab)
    }
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Einstellungen") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, divider = {}) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { onTab(index) },
                        text = { Text(title) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (secureMessage != null && page == 3) {
                        item {
                            SoftCard {
                                Text(secureMessage, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        when (page) {
                            0 -> AlarmTab(
                                settings.alarm, onAlarm, lessonSlots, lessonStatus, lessonDetail, onWakeRule,
                                radioResults, onSearchRadio, onPickApp, onTestSound, subjects, onLoadSubjects
                            )
                            1 -> TrainSettings(settings.train, onTrain, onSearchStations, stationResults, stationLoading, stationError, onTestTrain, testingTrain)
                            2 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                HomeAssistantSettings(settings.homeAssistant, onHome, onTestHome, testingHome, entities, onLoadEntities, testResult, forceHomeEditor)
                                PreWakeLightCard(
                                    light = settings.homeAssistant.preWake,
                                    entities = entities,
                                    onChange = { onHome(settings.homeAssistant.copy(preWake = it)) },
                                    onLoad = onLoadEntities,
                                    onTest = onTestLight,
                                    loading = lightsLoading,
                                    statusMessage = lightsMessage
                                )
                                LeaveLightOffCard(
                                    light = settings.homeAssistant.preWake,
                                    entities = entities,
                                    onChange = { onHome(settings.homeAssistant.copy(preWake = it)) },
                                    onTest = onTestLeaveLight
                                )
                            }
                            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                WebUntisSettings(
                                    settings.webUntis, onWebUntis, onTestUntis, onSearchSchools, schoolResults,
                                    onSelectSchool, onClearSchools, onParseQr, onParseServer, testingUntis, testResult, forceUntisEditor
                                )
                                AppearanceCard(settings, onApp, onCheckUpdates, updateBusy, fullScreenMissing, onAllowFullScreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmTab(
    settings: AlarmSettings,
    onUpdate: (AlarmSettings) -> Unit,
    slots: List<LessonSlot>,
    status: LessonGridStatus,
    detail: String?,
    onWakeRule: (WakeTimeRule) -> Unit,
    radioResults: List<io.qzz.xandll.morgo.data.datasource.RadioStation>,
    onSearchRadio: (String) -> Unit,
    onPickApp: () -> Unit,
    onTestSound: () -> Unit,
    subjects: List<String>,
    onLoadSubjects: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AlarmSettings(settings, onUpdate)
        WakeSoundCard(settings, onUpdate, radioResults, onSearchRadio, onPickApp, onTestSound)
        FollowUpCard(settings, onUpdate)
        WakeTimeRulesSection(settings.wakeTimeRules, slots, status, detail, onWakeRule)
        ExtraAlarmsCard(settings, subjects, onUpdate, onLoadSubjects)
    }
}

@Composable
private fun AppearanceCard(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onCheckUpdates: () -> Unit,
    updateBusy: Boolean,
    fullScreenMissing: Boolean = false,
    onAllowFullScreen: () -> Unit = {}
) {
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Morgo ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Updates kommen von einem öffentlichen GitHub-Release. Dafür braucht es keinen Token.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                settings.update.owner,
                { onUpdate(settings.copy(update = settings.update.copy(owner = it.trim()))) },
                label = { Text("GitHub-Konto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                settings.update.repo,
                { onUpdate(settings.copy(update = settings.update.copy(repo = it.trim()))) },
                label = { Text("Repository") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Nach Updates suchen")
                Switch(
                    checked = settings.update.enabled,
                    onCheckedChange = { onUpdate(settings.copy(update = settings.update.copy(enabled = it))) }
                )
            }
            Button(
                onClick = onCheckUpdates,
                enabled = !updateBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (updateBusy) "Suche …" else "Nach Updates suchen")
            }
            Text(
                "Die Installation braucht einmal die Freigabe „Unbekannte Apps“, nur für Morgo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Darstellung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Systemfarben auf Android 12+")
                Switch(checked = settings.useDynamicColor, onCheckedChange = { onUpdate(settings.copy(useDynamicColor = it)) })
            }
            Text(
                "Standard bleibt das Sonnenaufgang-Thema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fullScreenMissing) {
                Text(
                    "Der Wecker darf über dem Sperrbildschirm erscheinen. Ohne diese Freigabe gibt es nur eine Benachrichtigung.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAllowFullScreen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Sperrbildschirm-Wecker erlauben")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettings(settings: AlarmSettings, onUpdate: (AlarmSettings) -> Unit) {
    var picking by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Alarm, "Wecker")
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Wecker", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (settings.enabled) "Stellt sich nach dem Stundenplan" else "Ausgeschaltet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(checked = settings.enabled, onCheckedChange = { onUpdate(settings.copy(enabled = it)) })
            }
            Surface(
                onClick = { picking = "evening" },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Abend-Check", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.eveningCheckEnabled) "Erinnerung am Vorabend" else "Aus",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(formatTime2(settings.eveningCheckTime), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.eveningCheckEnabled,
                        onCheckedChange = { onUpdate(settings.copy(eveningCheckEnabled = it)) }
                    )
                }
            }
            TextButton(onClick = { more = !more }) { Text(if (more) "Weniger" else "Weitere Weckzeiten") }
            if (more) {
                TimeRow(Icons.Outlined.Schedule, "Standard-Weckzeit", "Wenn der Plan unsicher ist", formatTime2(settings.defaultWakeTime)) { picking = "default" }
                TimeRow(Icons.Outlined.WbSunny, "Früheste Zeit", "Plan B weckt nicht davor", formatTime2(settings.earliestWakeTime)) { picking = "earliest" }
                StepperRow("Schlummern", "${settings.snoozeMinutes} Min.", {
                    if (settings.snoozeMinutes > 1) onUpdate(settings.copy(snoozeMinutes = settings.snoozeMinutes - 1))
                }, {
                    if (settings.snoozeMinutes < 30) onUpdate(settings.copy(snoozeMinutes = settings.snoozeMinutes + 1))
                })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Plan B")
                    Switch(checked = settings.planBEnabled, onCheckedChange = { onUpdate(settings.copy(planBEnabled = it)) })
                }
                if (settings.planBEnabled) {
                    StepperRow("Höchstens früher", "${settings.maxEarlierMinutes} Min.", {
                        if (settings.maxEarlierMinutes >= 5) onUpdate(settings.copy(maxEarlierMinutes = settings.maxEarlierMinutes - 5))
                    }, {
                        if (settings.maxEarlierMinutes <= 115) onUpdate(settings.copy(maxEarlierMinutes = settings.maxEarlierMinutes + 5))
                    })
                }
            }
        }
    }
    picking?.let { which ->
        val initial = when (which) {
            "evening" -> settings.eveningCheckTime
            "earliest" -> settings.earliestWakeTime
            else -> settings.defaultWakeTime
        }
        WakeTimePickerDialog(
            title = "Uhrzeit",
            initial = initial,
            allowClear = false,
            onDismiss = { picking = null },
            onConfirm = { time ->
                if (time != null) {
                    onUpdate(when (which) {
                        "evening" -> settings.copy(eveningCheckTime = time)
                        "earliest" -> settings.copy(earliestWakeTime = time)
                        else -> settings.copy(defaultWakeTime = time)
                    })
                }
                picking = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeTimeRulesSection(
    rules: List<WakeTimeRule>,
    slots: List<LessonSlot>,
    status: LessonGridStatus,
    detail: String?,
    onUpdateRule: (WakeTimeRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var wakeEdit by remember { mutableStateOf<Int?>(null) }
    var trainEdit by remember { mutableStateOf<Int?>(null) }
    val periodCount = if (slots.isEmpty()) LessonGrid.DEFAULT_PERIOD_COUNT else slots.maxOf { it.number }
    val visible = WakeTable.visibleNumbers(rules, periodCount)
    val timesDiffer = slots.any { it.startDiffersByDay() }

    SoftCard(modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weckzeiten", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leere Weckzeit nutzt den Vorlauf vor Zug oder Stunde. Die nächste Stunde erscheint, sobald diese ausgefüllt sind.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                when (status) {
                    LessonGridStatus.Loading -> "Timegrid wird geladen …"
                    LessonGridStatus.Failed -> detail ?: "Timegrid konnte nicht geladen werden."
                    LessonGridStatus.Ready -> if (timesDiffer) "Beginnzeiten unterscheiden sich je Wochentag." else "Beginn laut Stundenplan, nur zur Ansicht."
                    else -> "Mit WebUntis verbinden, um Uhrzeiten anzuzeigen."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (status == LessonGridStatus.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            visible.forEach { number ->
                val slot = slots.find { it.number == number }
                val rule = rules.find { it.lessonNumber == number }
                val start = slot?.let { LessonGrid.standardStart(it) }
                val differing = slot?.let { LessonGrid.differingStarts(it) }.orEmpty()
                val beginn = when {
                    slots.isEmpty() -> "Beginn nach Verbindung"
                    start == null -> "kein Beginn"
                    else -> "Beginn ${formatTime2(start)}"
                }
                val filled = rule?.wakeTime != null
                SurfaceRow(number, beginn, differing, rule, filled, { wakeEdit = number }, { trainEdit = number })
            }
        }
    }

    wakeEdit?.let { number ->
        val current = rules.find { it.lessonNumber == number }
        WakeTimePickerDialog("Weckzeit · $number. Stunde", current?.wakeTime, true, { wakeEdit = null }) { time ->
            onUpdateRule((current ?: WakeTimeRule(lessonNumber = number)).copy(wakeTime = time, legacyStartTime = null))
            wakeEdit = null
        }
    }
    trainEdit?.let { number ->
        val current = rules.find { it.lessonNumber == number }
        PreferredTrainDialog("Zug · $number. Stunde", current?.preferredTrainDeparture, current?.preferredTrainLine.orEmpty(), { trainEdit = null }) { time, line ->
            onUpdateRule((current ?: WakeTimeRule(lessonNumber = number)).copy(preferredTrainDeparture = time, preferredTrainLine = line, legacyStartTime = null))
            trainEdit = null
        }
    }
}

@Composable
private fun SurfaceRow(
    number: Int,
    beginn: String,
    differing: List<Pair<DayOfWeek, java.time.LocalTime>>,
    rule: WakeTimeRule?,
    filled: Boolean,
    onWake: () -> Unit,
    onTrain: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$number. Stunde", style = MaterialTheme.typography.titleMedium)
                    Text(beginn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (differing.isNotEmpty()) {
                        Text(
                            differing.joinToString(" · ") { (day, time) ->
                                "${LessonGrid.dayLabel(day)} ${formatTime2(time)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onWake, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        rule?.wakeTime?.let { formatTime2(it) } ?: "Standard",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onTrain, modifier = Modifier.heightIn(min = 40.dp)) {
                Text(if (trainLabel(rule) == "–") "Zug optional" else "Zug ${trainLabel(rule)}")
            }
        }
    }
}

@Composable
fun TrainSettings(
    settings: TrainSettings,
    onUpdate: (TrainSettings) -> Unit,
    onSearchStations: (String) -> Unit,
    searchResults: List<Station>,
    searchLoading: Boolean,
    searchError: String?,
    onTest: () -> Unit,
    testing: Boolean
) {
    var startQuery by remember { mutableStateOf(settings.startStation?.name ?: "") }
    var destQuery by remember { mutableStateOf(settings.destinationStation?.name ?: "") }
    var startOpen by remember { mutableStateOf(false) }
    var destOpen by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<String?>(null) }
    val ready = settings.startStation != null && settings.destinationStation != null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Train, "Zug")
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Strecke", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (ready) "${settings.startStation?.name} → ${settings.destinationStation?.name}" else "Noch keine Strecke",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    StatusPill(if (ready) "verbunden" else "offen", ready)
                }
            }
        }
        SoftCard {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StationSearchField("Start", startQuery, startOpen && searchResults.isNotEmpty(), {
                    startQuery = it
                    active = "start"
                    destOpen = false
                    startOpen = it.trim().length >= 2
                    onSearchStations(it)
                }, { startOpen = false }, searchResults, {
                    onUpdate(settings.copy(startStation = it))
                    startQuery = it.name
                    startOpen = false
                }, {
                    onUpdate(settings.copy(startStation = null))
                    startQuery = ""
                    startOpen = false
                    onSearchStations("")
                }, settings.startStation != null || startQuery.isNotEmpty(), active == "start", searchLoading, searchError)
                StationSearchField("Ziel", destQuery, destOpen && searchResults.isNotEmpty(), {
                    destQuery = it
                    active = "dest"
                    startOpen = false
                    destOpen = it.trim().length >= 2
                    onSearchStations(it)
                }, { destOpen = false }, searchResults, {
                    onUpdate(settings.copy(destinationStation = it))
                    destQuery = it.name
                    destOpen = false
                }, {
                    onUpdate(settings.copy(destinationStation = null))
                    destQuery = ""
                    destOpen = false
                    onSearchStations("")
                }, settings.destinationStation != null || destQuery.isNotEmpty(), active == "dest", searchLoading, searchError)
                HorizontalDivider()
                StepperRow("Fußweg zur Schule", "${settings.walkingTimeMinutes} Min.", {
                    if (settings.walkingTimeMinutes > 0) onUpdate(settings.copy(walkingTimeMinutes = settings.walkingTimeMinutes - 1))
                }, {
                    if (settings.walkingTimeMinutes < 90) onUpdate(settings.copy(walkingTimeMinutes = settings.walkingTimeMinutes + 1))
                })
                StepperRow("Vorbereitung", "${settings.preparationTimeMinutes} Min.", {
                    if (settings.preparationTimeMinutes > 0) onUpdate(settings.copy(preparationTimeMinutes = settings.preparationTimeMinutes - 5))
                }, {
                    if (settings.preparationTimeMinutes < 120) onUpdate(settings.copy(preparationTimeMinutes = settings.preparationTimeMinutes + 5))
                })
                ConnectionTestButton(testing, onTest, Modifier.fillMaxWidth(), "ÖBB prüfen")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebUntisSettings(
    settings: WebUntisSettings,
    onUpdate: (WebUntisSettings) -> Unit,
    onTest: () -> Unit,
    onSearchSchools: (String) -> Unit,
    schoolSearchResults: List<io.qzz.xandll.morgo.data.model.WebUntisSchool>,
    onSelectSchool: (io.qzz.xandll.morgo.data.model.WebUntisSchool) -> Unit,
    onClearSchoolSearch: () -> Unit,
    onParseQRCode: (String) -> Unit,
    onParseServerUrl: (String) -> Unit,
    testing: Boolean,
    testResult: ConnectionTestResult? = null,
    forceEditing: Boolean = false
) {
    val ready = settings.isReady()
    var editing by remember { mutableStateOf(forceEditing || !ready) }
    var finishRequested by remember { mutableStateOf(false) }
    LaunchedEffect(testResult) {
        if (finishRequested && testResult is ConnectionTestResult.Success) {
            editing = false
            finishRequested = false
        }
    }
    if (!editing && ready) {
        AccountSummary(
            icon = Icons.Outlined.Person,
            title = "WebUntis",
            lines = listOf(
                "Schule" to settings.schoolName.ifBlank { settings.server },
                "Benutzer" to settings.username,
                "Login" to if (settings.useSecret) "QR-Code gespeichert" else "Passwort"
            ),
            status = "Verbunden",
            positive = true,
            onEdit = { editing = true }
        )
        return
    }
    if (!editing && !ready) {
        EmptyAccount(Icons.Outlined.Person, "WebUntis", "Schule und Zugang einmal eintragen.") { editing = true }
        return
    }
    UntisForm(settings, onUpdate, onSearchSchools, schoolSearchResults, onSelectSchool, onClearSchoolSearch, onParseQRCode, onParseServerUrl, testing, ready, {
        finishRequested = true
        onTest()
    }) { if (ready) editing = false }
}

@Composable
private fun EmptyAccount(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onSetup: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, title)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Einrichten") }
        }
    }
}

@Composable
private fun AccountSummary(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    lines: List<Pair<String, String>>,
    status: String,
    positive: Boolean,
    onEdit: () -> Unit
) {
    SoftCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, title)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                StatusPill(status, positive)
            }
            Spacer(Modifier.height(8.dp))
            lines.forEach { (label, value) -> InfoLine(label, value) }
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Bearbeiten") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntisForm(
    settings: WebUntisSettings,
    onUpdate: (WebUntisSettings) -> Unit,
    onSearchSchools: (String) -> Unit,
    schoolSearchResults: List<io.qzz.xandll.morgo.data.model.WebUntisSchool>,
    onSelectSchool: (io.qzz.xandll.morgo.data.model.WebUntisSchool) -> Unit,
    onClearSchoolSearch: () -> Unit,
    onParseQRCode: (String) -> Unit,
    onParseServerUrl: (String) -> Unit,
    testing: Boolean,
    canCancel: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit
) {
    var schoolQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(settings.server.isNotBlank()) }
    var showQR by remember { mutableStateOf(false) }
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("WebUntis einrichten", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !settings.useSecret, onClick = { onUpdate(settings.copy(useSecret = false)) }, label = { Text("Passwort") })
                FilterChip(selected = settings.useSecret, onClick = { onUpdate(settings.copy(useSecret = true)) }, label = { Text("QR-Code") })
            }
            ExposedDropdownMenuBox(expanded = expanded && schoolSearchResults.isNotEmpty(), onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = schoolQuery,
                    onValueChange = {
                        schoolQuery = it
                        onSearchSchools(it)
                        expanded = it.length >= 2
                    },
                    label = { Text("Schule suchen") },
                    placeholder = { Text("z. B. Beispielschule") },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded && schoolSearchResults.isNotEmpty(), onDismissRequest = { expanded = false }) {
                    schoolSearchResults.forEach { school ->
                        DropdownMenuItem(
                            text = { Column { Text(school.displayName); Text(school.server, style = MaterialTheme.typography.bodySmall) } },
                            onClick = {
                                onSelectSchool(school)
                                schoolQuery = school.displayName
                                expanded = false
                                onClearSchoolSearch()
                            }
                        )
                    }
                }
            }
            OutlinedTextField(settings.username, { onUpdate(settings.copy(username = it)) }, label = { Text("Benutzername") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (settings.useSecret) {
                Button(onClick = { showQR = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                    Text("  QR-Code scannen")
                }
                if (settings.secret.isNotBlank()) Text("QR-Code gespeichert", color = MaterialTheme.colorScheme.primary)
                Text("In WebUntis unter Profil, Freigaben, Untis Mobile.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(
                    settings.password,
                    { onUpdate(settings.copy(password = it)) },
                    label = { Text("Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Server ausblenden" else "Server von Hand") }
            if (advanced) {
                OutlinedTextField(settings.server, {
                    onUpdate(settings.copy(server = it))
                    onParseServerUrl(it)
                }, label = { Text("Server") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(settings.schoolName, { onUpdate(settings.copy(schoolName = it)) }, label = { Text("Interner Schulname") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Button(onClick = onFinish, enabled = !testing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  Prüfe …")
                } else {
                    Text("Fertig")
                }
            }
            if (canCancel) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Abbrechen") }
        }
    }
    if (showQR) {
        io.qzz.xandll.morgo.presentation.components.QRCodeScannerDialog(
            onDismiss = { showQR = false },
            onQRCodeScanned = { onParseQRCode(it); showQR = false }
        )
    }
}

@Composable
fun HomeAssistantSettings(
    settings: HomeAssistantSettings,
    onUpdate: (HomeAssistantSettings) -> Unit,
    onTest: () -> Unit,
    testing: Boolean,
    availableEntities: List<HomeAssistantEntity>,
    onLoadEntities: () -> Unit,
    testResult: ConnectionTestResult? = null,
    forceEditing: Boolean = false
) {
    val ready = settings.url.isNotBlank() && settings.token.isNotBlank()
    var editing by remember { mutableStateOf(forceEditing || !ready) }
    var finishRequested by remember { mutableStateOf(false) }
    LaunchedEffect(testResult) {
        if (finishRequested && testResult is ConnectionTestResult.Success) {
            editing = false
            finishRequested = false
        }
    }
    if (!editing && ready) {
        AccountSummary(
            Icons.Outlined.Home,
            "Home Assistant",
            listOf(
                "Adresse" to settings.url,
                "Aktionen" to if (settings.wakeUpActions.isEmpty()) "keine" else "${settings.wakeUpActions.size} beim Wecken",
                "Licht" to settings.preWake.summaryLine()
            ),
            "Verbunden",
            true
        ) { editing = true }
        return
    }
    if (!editing) {
        EmptyAccount(Icons.Outlined.Home, "Home Assistant", "Optional. Licht und Szenen zum Aufwachen.") { editing = true }
        return
    }
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Home Assistant einrichten", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(settings.url, { onUpdate(settings.copy(url = it)) }, label = { Text("Adresse") }, placeholder = { Text("https://home.example") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(settings.token, { onUpdate(settings.copy(token = it)) }, label = { Text("Token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = onLoadEntities) { Text("Geräte laden") }
            availableEntities.forEach { entity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.wakeUpActions.contains(entity.entityId),
                        onCheckedChange = { checked ->
                            val next = if (checked) settings.wakeUpActions + entity.entityId else settings.wakeUpActions - entity.entityId
                            onUpdate(settings.copy(wakeUpActions = next))
                        }
                    )
                    Column {
                        Text(entity.name.ifBlank { entity.entityId })
                        Text(
                            "${HomeAssistantTargets.domainLabel(entity.entityId)} · ${entity.entityId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(onClick = { finishRequested = true; onTest() }, enabled = !testing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (testing) "Prüfe …" else "Fertig")
            }
            if (ready) TextButton(onClick = { editing = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Abbrechen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationSearchField(
    label: String,
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    results: List<Station>,
    onSelect: (Station) -> Unit,
    onClear: () -> Unit,
    showClear: Boolean,
    showStatus: Boolean,
    loading: Boolean,
    error: String?
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!it) onDismiss() }) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            placeholder = { Text("Mindestens 2 Zeichen") },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            isError = showStatus && !loading && error != null,
            trailingIcon = {
                when {
                    showStatus && loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    showClear -> IconButton(onClick = onClear) { Text("×", style = MaterialTheme.typography.titleLarge) }
                }
            },
            supportingText = {
                if (showStatus && loading) Text("Suche …") else if (showStatus && error != null) Text(error)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            results.forEach { station ->
                DropdownMenuItem(text = { Text(station.name) }, onClick = { onSelect(station) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeTimePickerDialog(
    title: String,
    initial: LocalTime?,
    allowClear: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime?) -> Unit
) {
    val state = rememberTimePickerState(initial?.hour ?: 6, initial?.minute ?: 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Übernehmen") } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onConfirm(null) }) { Text("Standard") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredTrainDialog(
    title: String,
    initialTime: LocalTime?,
    initialLine: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime?, String?) -> Unit
) {
    val state = rememberTimePickerState(initialTime?.hour ?: 7, initialTime?.minute ?: 15, is24Hour = true)
    var line by remember { mutableStateOf(initialLine) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(line, { line = it }, label = { Text("Linie, z. B. REX") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute), line.takeIf { it.isNotBlank() }) }) { Text("Übernehmen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null, null) }) { Text("Leeren") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}

private fun trainLabel(rule: WakeTimeRule?): String {
    if (rule == null) return "–"
    val time = rule.preferredTrainDeparture?.let { formatTime2(it) }
    val line = rule.preferredTrainLine?.takeIf { it.isNotBlank() }
    return listOfNotNull(time, line).joinToString(" ").ifBlank { "–" }
}

private fun formatTime2(time: LocalTime): String = String.format("%02d:%02d", time.hour, time.minute)

private fun WebUntisSettings.isReady(): Boolean {
    val secretOk = if (useSecret) secret.isNotBlank() else password.isNotBlank()
    return server.isNotBlank() && schoolName.isNotBlank() && username.isNotBlank() && secretOk
}

@Composable
fun ConnectionTestButton(testing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Verbindung testen") {
    Button(onClick = onClick, enabled = !testing, modifier = modifier.heightIn(min = 48.dp)) {
        if (testing) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Text("  Teste …")
        } else {
            Text(label)
        }
    }
}

@Composable
fun ConnectionTestDialog(result: ConnectionTestResult, onDismiss: () -> Unit, onRetry: (() -> Unit)?) {
    var showDetails by remember { mutableStateOf(false) }
    val isError = result is ConnectionTestResult.Error
    val details = (result as? ConnectionTestResult.Error)?.technicalDetails
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isError) "Verbindung fehlgeschlagen" else "Verbindung erfolgreich") },
        text = {
            Column {
                Text(result.message)
                if (!details.isNullOrBlank()) {
                    TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Details verbergen" else "Technische Details") }
                    if (showDetails) Text(details, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = if (onRetry != null) {
            { TextButton(onClick = onRetry) { Text("Erneut versuchen") } }
        } else null
    )
}
