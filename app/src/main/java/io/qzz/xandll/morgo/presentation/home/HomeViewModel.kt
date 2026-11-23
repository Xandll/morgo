package io.qzz.xandll.morgo.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.MorningAlarmSync
import io.qzz.xandll.morgo.domain.model.ExtraAlarmPlan
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import io.qzz.xandll.morgo.domain.model.*
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import io.qzz.xandll.morgo.util.ErrorDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: MorgoRepository,
    private val calculateAlarmTimeUseCase: CalculateAlarmTimeUseCase,
    private val morningAlarmSync: MorningAlarmSync
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _homeAssistantEntities = MutableStateFlow<List<HomeAssistantEntity>>(emptyList())
    val homeAssistantEntities: StateFlow<List<HomeAssistantEntity>> = _homeAssistantEntities.asStateFlow()

    private val _showOverride = MutableStateFlow(false)
    val showOverride: StateFlow<Boolean> = _showOverride.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val settings = settingsRepository.loadSettings()
                val tomorrow = LocalDate.now().plusDays(1)
                val alarmPlan = calculateAlarmTimeUseCase.execute(tomorrow, settings).getOrThrow()
                _uiState.value = HomeUiState.Ready(
                    date = tomorrow,
                    plan = alarmPlan,
                    wakeActions = settings.homeAssistant.wakeUpActions,
                    dynamicColor = settings.useDynamicColor,
                    lightHint = lightHint(alarmPlan, settings.homeAssistant.preWake),
                    extraLines = extraLines(alarmPlan, settings.alarm),
                    leaveHint = leaveHint(tomorrow, alarmPlan, settings)
                )
                loadHomeAssistantEntities(settings.homeAssistant)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(
                    ErrorDetails.summarize(e) ?: "Der Morgen konnte nicht geladen werden."
                )
            }
        }
    }

    fun openOverride() {
        _showOverride.value = true
    }

    fun closeOverride() {
        _showOverride.value = false
    }

    fun applyOverride(mode: DayOverrideMode?, wakeTime: LocalTime?) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            val tomorrow = LocalDate.now().plusDays(1)
            val override = mode?.let { DayOverride(tomorrow, it, wakeTime) }
            val updated = settings.copy(alarm = settings.alarm.copy(dayOverride = override))
            settingsRepository.saveSettings(updated)
            val plan = calculateAlarmTimeUseCase.execute(tomorrow, updated).getOrNull()
            morningAlarmSync.apply(tomorrow, plan, updated)
            _showOverride.value = false
            loadData()
        }
    }

    private fun loadHomeAssistantEntities(settings: HomeAssistantSettings) {
        if (settings.url.isBlank() || settings.token.isBlank()) return
        viewModelScope.launch {
            repository.getHomeAssistantEntities(settings.url, settings.token)
                .onSuccess { entities ->
                    _homeAssistantEntities.value = entities.filter {
                        HomeAssistantTargets.isSelectable(it.entityId)
                    }
                }
        }
    }

    fun activateEntity(entityId: String) {
        viewModelScope.launch {
            val settings = settingsRepository.loadSettings()
            repository.callHomeAssistant(
                settings.homeAssistant.url,
                settings.homeAssistant.token,
                entityId,
                HaServiceKind.TURN_ON
            )
        }
    }

    private fun lightHint(plan: AlarmPlan?, light: PreWakeLightSettings): String? {
        if (plan == null || !PreWakeLightPlan.shouldArm(plan.rings, light)) return null
        return PreWakeLightPlan.hint(plan.alarmTime, light.leadMinutes)
    }

    private fun leaveHint(date: LocalDate, plan: AlarmPlan?, settings: AppSettings): String? {
        val light = settings.homeAssistant.preWake
        if (!light.leaveOffEnabled) return null
        val departure = LeaveLightPlan.chosenDeparture(
            rings = plan?.rings == true,
            connection = plan?.trainConnection,
            planBConnection = null,
            planBDroppedTrain = false
        ) ?: return null
        val at = LeaveLightPlan.leaveAt(
            date,
            departure,
            settings.train.walkingTimeMinutes,
            light.leaveOffOffsetMinutes
        )
        return LeaveLightPlan.hintAfterWake(at, plan?.alarmTime ?: return null)
    }

    private fun extraLines(plan: AlarmPlan?, alarm: AlarmSettings): List<String> {
        if (plan == null || !plan.rings) return emptyList()
        return ExtraAlarmPlan.lines(plan.alarmTime, plan.daySubjects, alarm.extraAlarms)
    }
}

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Ready(
        val date: LocalDate,
        val plan: AlarmPlan?,
        val wakeActions: List<String>,
        val dynamicColor: Boolean,
        val lightHint: String? = null,
        val extraLines: List<String> = emptyList(),
        val leaveHint: String? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
