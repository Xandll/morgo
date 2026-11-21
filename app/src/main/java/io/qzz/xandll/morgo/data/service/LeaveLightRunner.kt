package io.qzz.xandll.morgo.data.service

import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.LeaveLightPlan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeaveLightRunner @Inject constructor(
    private val repository: MorgoRepository,
    private val settingsRepository: SettingsRepository
) {
    /** [test] führt die Aus-Aktionen auch aus, wenn der Schalter noch aus ist. */
    suspend fun turnOff(test: Boolean = false): String {
        val home = settingsRepository.loadSettings().homeAssistant
        val light = home.preWake
        if (!test && !light.leaveOffEnabled) return "Licht beim Losgehen ist aus"
        if (home.url.isBlank() || home.token.isBlank()) return "Zuerst Home Assistant verbinden"
        val actions = LeaveLightPlan.actions(light.entityIds, light.leaveOff)
        if (actions.isEmpty()) return "Nichts auszuschalten"
        actions.forEach { action ->
            val kind = LeaveLightPlan.service(action.entityId)
            repeat(action.times) {
                repository.callHomeAssistant(home.url, home.token, action.entityId, kind)
            }
        }
        return "Licht ist aus"
    }
}
