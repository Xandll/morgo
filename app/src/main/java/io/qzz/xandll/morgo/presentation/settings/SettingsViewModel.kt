package io.qzz.xandll.morgo.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.EveningCheckScheduler
import io.qzz.xandll.morgo.data.receiver.LeaveLightReceiver
import io.qzz.xandll.morgo.data.receiver.PreWakeLightReceiver
import io.qzz.xandll.morgo.data.service.LeaveLightScheduler
import io.qzz.xandll.morgo.data.service.PreWakeLightScheduler
import io.qzz.xandll.morgo.data.worker.AlarmRefresh
import io.qzz.xandll.morgo.domain.model.AppSettings
import io.qzz.xandll.morgo.domain.model.HomeAssistantTargets
import io.qzz.xandll.morgo.domain.model.LessonGrid
import io.qzz.xandll.morgo.domain.model.LessonSlot
import io.qzz.xandll.morgo.domain.model.Station
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.RadioBrowserClient
import io.qzz.xandll.morgo.data.datasource.RadioStation
import io.qzz.xandll.morgo.data.service.WakeSoundService
import io.qzz.xandll.morgo.data.service.WakeSoundStatus
import io.qzz.xandll.morgo.data.update.UpdateCheckResult
import io.qzz.xandll.morgo.data.update.UpdateCoordinator
import io.qzz.xandll.morgo.data.update.UpdateScheduler
import io.qzz.xandll.morgo.BuildConfig
import io.qzz.xandll.morgo.domain.model.LessonStatus
import io.qzz.xandll.morgo.util.ConnectionErrors
import io.qzz.xandll.morgo.util.ErrorDetails
import io.qzz.xandll.morgo.util.WebUntisStatusText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import javax.inject.Inject

enum class LessonGridStatus {
    Idle,
    Loading,
    Ready,
    Unavailable,
    Failed
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: MorgoRepository,
    private val eveningCheckScheduler: EveningCheckScheduler,
    private val preWakeLightScheduler: PreWakeLightScheduler,
    private val leaveLightScheduler: LeaveLightScheduler,
    private val radioBrowser: RadioBrowserClient,
    private val updateCoordinator: UpdateCoordinator,
    private val wakeSoundStatus: WakeSoundStatus
) : ViewModel() {

    private val _settings = MutableStateFlow(settingsRepository.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _stationSearchQuery = MutableStateFlow("")
    private val _stationSearchResults = MutableStateFlow<List<Station>>(emptyList())
    val stationSearchResults: StateFlow<List<Station>> = _stationSearchResults.asStateFlow()
    private val _stationSearchLoading = MutableStateFlow(false)
    val stationSearchLoading: StateFlow<Boolean> = _stationSearchLoading.asStateFlow()
    private val _stationSearchError = MutableStateFlow<String?>(null)
    val stationSearchError: StateFlow<String?> = _stationSearchError.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testConnectionResult: StateFlow<ConnectionTestResult?> = _testConnectionResult.asStateFlow()

    private val _connectionTestBusy = MutableStateFlow<ConnectionTestTarget?>(null)
    val connectionTestBusy: StateFlow<ConnectionTestTarget?> = _connectionTestBusy.asStateFlow()
    
    private val _homeAssistantEntities = MutableStateFlow<List<io.qzz.xandll.morgo.domain.model.HomeAssistantEntity>>(emptyList())
    val homeAssistantEntities: StateFlow<List<io.qzz.xandll.morgo.domain.model.HomeAssistantEntity>> = _homeAssistantEntities.asStateFlow()
    
    private val _lessonSlots = MutableStateFlow<List<LessonSlot>>(emptyList())
    val lessonSlots: StateFlow<List<LessonSlot>> = _lessonSlots.asStateFlow()

    private val _lessonGridStatus = MutableStateFlow(LessonGridStatus.Idle)
    val lessonGridStatus: StateFlow<LessonGridStatus> = _lessonGridStatus.asStateFlow()

    private val _lessonGridDetail = MutableStateFlow<String?>(null)
    val lessonGridDetail: StateFlow<String?> = _lessonGridDetail.asStateFlow()

    private var lessonGridJob: Job? = null
    
    private val _schoolSearchQuery = MutableStateFlow("")
    private val _schoolSearchResults = MutableStateFlow<List<io.qzz.xandll.morgo.data.model.WebUntisSchool>>(emptyList())
    val schoolSearchResults: StateFlow<List<io.qzz.xandll.morgo.data.model.WebUntisSchool>> = _schoolSearchResults.asStateFlow()
    
    init {
        // Debounced station search, ab 2 Zeichen
        viewModelScope.launch {
            _stationSearchQuery
                .debounce(500)
                .collectLatest { query ->
                    if (query.length < 2) {
                        _stationSearchResults.value = emptyList()
                        _stationSearchError.value = null
                        _stationSearchLoading.value = false
                        return@collectLatest
                    }
                    _stationSearchLoading.value = true
                    _stationSearchError.value = null
                    val result = repository.searchStations(query)
                    _stationSearchLoading.value = false
                    result.fold(
                        onSuccess = { stations ->
                            _stationSearchResults.value = stations
                            _stationSearchError.value = if (stations.isEmpty()) "Keine Station gefunden" else null
                        },
                        onFailure = { error ->
                            _stationSearchResults.value = emptyList()
                            val raw = error.message.orEmpty()
                            _stationSearchError.value = if (raw.startsWith("HAFAS Error:")) {
                                "ÖBB-Fehler: ${raw.substringAfter(':').trim().take(80)}"
                            } else {
                                ConnectionErrors.describe(error, ConnectionErrors.Service.HAFAS).message
                            }
                        }
                    )
                }
        }
        
        // Debounced school search
        viewModelScope.launch {
            _schoolSearchQuery
                .debounce(500)
                .filter { it.length >= 2 }
                .collect { query ->
                    val result = repository.searchWebUntisSchools(query)
                    result.onSuccess { schools ->
                        _schoolSearchResults.value = schools
                    }
                }
        }
    }

    val secureStorageBroken = settingsRepository.secureStorageBroken
    val secureStorageMessage: String? = settingsRepository.secureStorageMessage

    fun updateSettings(settings: AppSettings) {
        val previous = _settings.value
        _settings.value = settings
        settingsRepository.saveSettings(settings)
        eveningCheckScheduler.reschedule(settings)
        if (previous.update != settings.update) {
            UpdateScheduler.schedule(context, settings.update.enabled)
        }
        if (previous.alarm != settings.alarm || previous.homeAssistant.preWake != settings.homeAssistant.preWake) {
            preWakeLightScheduler.rescheduleStored(settings.homeAssistant.preWake)
            AlarmRefresh.enqueue(context)
        }
        if (previous.homeAssistant.preWake != settings.homeAssistant.preWake ||
            previous.train.walkingTimeMinutes != settings.train.walkingTimeMinutes
        ) {
            leaveLightScheduler.rearm(settings)
        }
    }

    private val _lightsLoading = MutableStateFlow(false)
    val lightsLoading: StateFlow<Boolean> = _lightsLoading.asStateFlow()

    private val _lightsMessage = MutableStateFlow<String?>(null)
    val lightsMessage: StateFlow<String?> = _lightsMessage.asStateFlow()

    private val _updateBusy = MutableStateFlow(false)
    val updateBusy: StateFlow<Boolean> = _updateBusy.asStateFlow()

    private val _updateNotice = MutableStateFlow<String?>(null)
    val updateNotice: StateFlow<String?> = _updateNotice.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateBusy.value = true
            _updateNotice.value = when (val result = updateCoordinator.check(manual = true)) {
                UpdateCheckResult.UpToDate -> "Morgo ${BuildConfig.VERSION_NAME} ist aktuell."
                is UpdateCheckResult.Failed -> result.message
                is UpdateCheckResult.Available -> null
            }
            _updateBusy.value = false
        }
    }

    fun clearUpdateNotice() {
        _updateNotice.value = null
    }

    fun testPreWakeLight() {
        context.sendBroadcast(Intent(context, PreWakeLightReceiver::class.java).apply {
            action = PreWakeLightReceiver.ACTION_RUN
            putExtra(PreWakeLightReceiver.EXTRA_STEP, 0)
            putExtra(PreWakeLightReceiver.EXTRA_TEST, true)
        })
    }

    fun testLeaveLight() {
        context.sendBroadcast(Intent(context, LeaveLightReceiver::class.java).apply {
            action = LeaveLightReceiver.ACTION_OFF
            putExtra(LeaveLightReceiver.EXTRA_TEST, true)
        })
    }

    private val _radioStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioStations: StateFlow<List<RadioStation>> = _radioStations.asStateFlow()

    private val _radioError = MutableStateFlow<String?>(null)
    val radioError: StateFlow<String?> = _radioError.asStateFlow()

    private val _subjects = MutableStateFlow<List<String>>(emptyList())
    val subjects: StateFlow<List<String>> = _subjects.asStateFlow()

    val wakeSoundFailure = wakeSoundStatus.failure

    fun testWakeSound() {
        wakeSoundStatus.clear()
        WakeSoundService.startTest(context)
    }

    fun clearWakeSoundFailure() {
        wakeSoundStatus.clear()
    }

    fun searchRadio(query: String) {
        viewModelScope.launch {
            val host = _settings.value.alarm.music.radioBrowserHost
            _radioError.value = null
            radioBrowser.search(query, host).fold(
                onSuccess = { _radioStations.value = it },
                onFailure = { error ->
                    _radioStations.value = emptyList()
                    _radioError.value = ConnectionErrors.describe(error, ConnectionErrors.Service.RADIO).message
                }
            )
        }
    }

    fun clearRadioError() {
        _radioError.value = null
    }

    fun loadSubjects() {
        viewModelScope.launch {
            val settings = _settings.value.webUntis
            val start = java.time.LocalDate.now()
            val lessons = repository.getTimetable(settings, start, start.plusDays(13)).getOrElse { emptyList() }
            _subjects.value = lessons
                .filter { it.status != LessonStatus.CANCELLED }
                .map { it.subject.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }

    fun completeOnboarding() {
        updateSettings(_settings.value.copy(onboardingComplete = true))
    }

    fun searchStations(query: String) {
        val trimmed = query.trim()
        _stationSearchQuery.value = trimmed
        _stationSearchError.value = null
        if (trimmed.length < 2) {
            _stationSearchResults.value = emptyList()
            _stationSearchLoading.value = false
        } else {
            _stationSearchLoading.value = true
            _stationSearchResults.value = emptyList()
        }
    }
    
    fun clearStationSearch() {
        _stationSearchQuery.value = ""
        _stationSearchResults.value = emptyList()
        _stationSearchError.value = null
        _stationSearchLoading.value = false
    }

    fun testWebUntisConnection() {
        launchTest(ConnectionTestTarget.WEBUNTIS) {
            val settings = _settings.value.webUntis
            val credentialMissing = if (settings.useSecret) {
                settings.secret.isBlank()
            } else {
                settings.password.isBlank()
            }
            if (settings.server.isBlank() || settings.schoolName.isBlank() ||
                settings.username.isBlank() || credentialMissing
            ) {
                return@launchTest ConnectionTestResult.Error(
                    message = "Bitte Server, Schule und Zugangsdaten ausfüllen",
                    technicalDetails = null
                )
            }
            val result = repository.testWebUntisConnection(settings)
            if (result.isSuccess) {
                val probe = result.getOrNull()
                ConnectionTestResult.Success(
                    WebUntisStatusText.success(
                        displayName = probe?.displayName,
                        lessons = probe?.lessons.orEmpty()
                    )
                )
            } else {
                failure(result.exceptionOrNull(), ConnectionErrors.Service.WEBUNTIS, ConnectionTestTarget.WEBUNTIS)
            }
        }
    }

    fun testHafasConnection() {
        launchTest(ConnectionTestTarget.HAFAS) {
            val result = repository.testHafasConnection()
            result.fold(
                onSuccess = { count ->
                    if (count == 0) {
                        ConnectionTestResult.Error(
                            message = "ÖBB-Fahrplan erreichbar, aber keine Station gefunden",
                            technicalDetails = null,
                            retryTarget = ConnectionTestTarget.HAFAS
                        )
                    } else {
                        ConnectionTestResult.Success("ÖBB-Fahrplan erreichbar ($count Stationen zu Wien)")
                    }
                },
                onFailure = {
                    failure(it, ConnectionErrors.Service.HAFAS, ConnectionTestTarget.HAFAS)
                }
            )
        }
    }

    fun testHomeAssistantConnection() {
        launchTest(ConnectionTestTarget.HOME_ASSISTANT) {
            val current = _settings.value
            val settings = current.homeAssistant
            if (settings.url.isBlank() || settings.token.isBlank()) {
                return@launchTest ConnectionTestResult.Error(
                    message = "Bitte URL und Token eingeben",
                    technicalDetails = null
                )
            }
            val cleaned = settings.copy(
                url = HomeAssistantClient.normalizeBaseUrl(settings.url),
                token = HomeAssistantClient.normalizeToken(settings.token)
            )
            if (cleaned != settings) {
                updateSettings(current.copy(homeAssistant = cleaned))
            }
            val result = repository.testHomeAssistantConnection(cleaned.url, cleaned.token)
            result.fold(
                onSuccess = { info ->
                    val name = info.locationName.ifBlank { "Home Assistant" }
                    val message = if (info.version.isBlank()) {
                        "Verbunden mit $name"
                    } else {
                        "Verbunden mit $name (Home Assistant ${info.version})"
                    }
                    ConnectionTestResult.Success(message)
                },
                onFailure = {
                    failure(it, ConnectionErrors.Service.HOME_ASSISTANT, ConnectionTestTarget.HOME_ASSISTANT)
                }
            )
        }
    }

    fun retryConnectionTest() {
        when ((_testConnectionResult.value as? ConnectionTestResult.Error)?.retryTarget) {
            ConnectionTestTarget.WEBUNTIS -> testWebUntisConnection()
            ConnectionTestTarget.HAFAS -> testHafasConnection()
            ConnectionTestTarget.HOME_ASSISTANT -> testHomeAssistantConnection()
            null -> Unit
        }
    }

    private fun launchTest(
        target: ConnectionTestTarget,
        block: suspend () -> ConnectionTestResult
    ) {
        viewModelScope.launch {
            _connectionTestBusy.value = target
            _testConnectionResult.value = null
            try {
                _testConnectionResult.value = block()
            } catch (e: Exception) {
                _testConnectionResult.value = failure(e, serviceFor(target), target)
            } finally {
                _connectionTestBusy.value = null
            }
        }
    }

    private fun failure(
        error: Throwable?,
        service: ConnectionErrors.Service,
        target: ConnectionTestTarget
    ): ConnectionTestResult.Error {
        val described = ConnectionErrors.describe(error, service)
        return ConnectionTestResult.Error(
            message = described.message,
            technicalDetails = described.technical,
            retryTarget = target
        )
    }

    private fun serviceFor(target: ConnectionTestTarget): ConnectionErrors.Service {
        return when (target) {
            ConnectionTestTarget.WEBUNTIS -> ConnectionErrors.Service.WEBUNTIS
            ConnectionTestTarget.HAFAS -> ConnectionErrors.Service.HAFAS
            ConnectionTestTarget.HOME_ASSISTANT -> ConnectionErrors.Service.HOME_ASSISTANT
        }
    }

    fun clearTestResult() {
        _testConnectionResult.value = null
    }
    
    fun loadHomeAssistantEntities(manual: Boolean = false) {
        viewModelScope.launch {
            val current = _settings.value
            val settings = current.homeAssistant
            val url = HomeAssistantClient.normalizeBaseUrl(settings.url)
            val token = HomeAssistantClient.normalizeToken(settings.token)
            if (url.isBlank() || token.isBlank()) {
                _lightsLoading.value = false
                if (manual) {
                    val message = "Bitte zuerst Adresse und Token speichern."
                    _lightsMessage.value = message
                    _testConnectionResult.value = ConnectionTestResult.Error(message, null)
                }
                return@launch
            }
            if (url != settings.url || token != settings.token) {
                updateSettings(current.copy(homeAssistant = settings.copy(url = url, token = token)))
            }
            _lightsLoading.value = true
            _lightsMessage.value = null
            val result = repository.getHomeAssistantEntities(url, token)
            result.fold(
                onSuccess = { entities ->
                    val usable = entities.filter {
                        HomeAssistantTargets.isSelectable(it.entityId)
                    }
                    _homeAssistantEntities.value = usable
                    if (usable.isEmpty()) {
                        val message = "Home Assistant hat keine passenden Einträge zurückgegeben."
                        _lightsMessage.value = message
                        _testConnectionResult.value = ConnectionTestResult.Error(
                            message,
                            "GET /api/states ohne light.*, switch.*, input_boolean.*, script.* oder scene.*"
                        )
                    } else {
                        _lightsMessage.value = null
                    }
                },
                onFailure = { error ->
                    val described = ConnectionErrors.describe(error, ConnectionErrors.Service.HOME_ASSISTANT)
                    _homeAssistantEntities.value = emptyList()
                    _lightsMessage.value = described.message
                    _testConnectionResult.value = ConnectionTestResult.Error(
                        message = described.message,
                        technicalDetails = described.technical
                    )
                }
            )
            _lightsLoading.value = false
        }
    }
    
    fun loadLessonGrid() {
        lessonGridJob?.cancel()
        val webUntis = _settings.value.webUntis
        val hasLogin = webUntis.server.isNotBlank() &&
            webUntis.schoolName.isNotBlank() &&
            webUntis.username.isNotBlank() &&
            (webUntis.password.isNotBlank() || webUntis.secret.isNotBlank())
        if (!hasLogin) {
            _lessonSlots.value = emptyList()
            _lessonGridDetail.value = null
            _lessonGridStatus.value = LessonGridStatus.Unavailable
            return
        }
        _lessonGridStatus.value = LessonGridStatus.Loading
        lessonGridJob = viewModelScope.launch {
            val result = repository.loadTimegrid(webUntis)
            result.onSuccess { grids ->
                val slots = LessonGrid.fromTimegrids(grids)
                _lessonSlots.value = slots
                _lessonGridDetail.value = null
                _lessonGridStatus.value = if (slots.isEmpty()) {
                    LessonGridStatus.Unavailable
                } else {
                    LessonGridStatus.Ready
                }
                val latest = _settings.value
                val migrated = LessonGrid.migrateRules(latest.alarm.wakeTimeRules, slots)
                if (migrated != latest.alarm.wakeTimeRules) {
                    updateSettings(latest.copy(alarm = latest.alarm.copy(wakeTimeRules = migrated)))
                }
            }.onFailure { error ->
                _lessonSlots.value = emptyList()
                _lessonGridDetail.value = ConnectionErrors.describe(
                    error,
                    ConnectionErrors.Service.WEBUNTIS
                ).message
                _lessonGridStatus.value = LessonGridStatus.Failed
            }
        }
    }

    fun setWakeTimeRule(rule: WakeTimeRule) {
        val current = _settings.value
        val kept = current.alarm.wakeTimeRules.filter { existing ->
            existing.lessonNumber != rule.lessonNumber
        }
        val meaningful = rule.lessonNumber > 0 && (
            rule.wakeTime != null ||
                rule.preferredTrainDeparture != null ||
                !rule.preferredTrainLine.isNullOrBlank()
            )
        val updated = if (meaningful) {
            kept + rule.copy(lessonNumber = rule.lessonNumber, legacyStartTime = null)
        } else {
            kept
        }
        updateSettings(current.copy(alarm = current.alarm.copy(wakeTimeRules = updated.sortedBy { it.lessonNumber })))
    }
    
    fun searchSchools(query: String) {
        _schoolSearchQuery.value = query
        if (query.length < 2) {
            _schoolSearchResults.value = emptyList()
        }
    }
    
    fun clearSchoolSearch() {
        _schoolSearchQuery.value = ""
        _schoolSearchResults.value = emptyList()
    }
    
    fun selectSchool(school: io.qzz.xandll.morgo.data.model.WebUntisSchool) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(
            webUntis = currentSettings.webUntis.copy(
                server = school.server,
                schoolName = school.loginName
            )
        ))
        clearSchoolSearch()
    }
    
    fun parseQRCode(qrCode: String) {
        // Format: untis://setschool?url=...&school=...&user=...&key=...
        try {
            val uri = android.net.Uri.parse(qrCode)
            val url = uri.getQueryParameter("url") ?: return
            val school = uri.getQueryParameter("school") ?: return
            val user = uri.getQueryParameter("user") ?: return
            val key = uri.getQueryParameter("key") ?: return
            
            val currentSettings = _settings.value
            updateSettings(currentSettings.copy(
                webUntis = currentSettings.webUntis.copy(
                    server = url,
                    schoolName = school,
                    username = user,
                    secret = key,
                    useSecret = true
                )
            ))
        } catch (e: Exception) {
            _testConnectionResult.value = ConnectionTestResult.Error(
                message = "QR-Code konnte nicht gelesen werden",
                technicalDetails = ErrorDetails.summarize(e)
            )
        }
    }
    
    fun parseServerUrl(serverInput: String) {
        // Wenn komplette WebUntis-URL eingegeben wurde, Server und School extrahieren
        if (serverInput.contains("WebUntis") || serverInput.contains("?school=")) {
            val (server, school) = io.qzz.xandll.morgo.data.datasource.WebUntisClient.parseWebUntisUrl(serverInput)
            if (server != null) {
                val currentSettings = _settings.value
                updateSettings(currentSettings.copy(
                    webUntis = currentSettings.webUntis.copy(
                        server = server,
                        schoolName = school ?: currentSettings.webUntis.schoolName
                    )
                ))
            }
        }
    }
}

enum class ConnectionTestTarget {
    WEBUNTIS,
    HAFAS,
    HOME_ASSISTANT
}

sealed class ConnectionTestResult {
    abstract val message: String

    data class Success(override val message: String) : ConnectionTestResult()
    data class Error(
        override val message: String,
        val technicalDetails: String?,
        val retryTarget: ConnectionTestTarget? = null
    ) : ConnectionTestResult()
}
