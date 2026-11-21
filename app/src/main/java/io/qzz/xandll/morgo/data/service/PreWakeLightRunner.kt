package io.qzz.xandll.morgo.data.service

import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class LightStepResult(
    val message: String,
    val nextDelay: Duration?
)

@Singleton
class PreWakeLightRunner @Inject constructor(
    private val repository: MorgoRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Ein Schritt. Der nächste Alarm wartet [LightStepResult.nextDelay],
     * damit kein Vordergrunddienst die ganze Sequenz offen halten muss.
     */
    suspend fun runStep(index: Int): LightStepResult {
        val home = settingsRepository.loadSettings().homeAssistant
        val light = home.preWake
        if (home.url.isBlank() || home.token.isBlank()) {
            return LightStepResult("Zuerst Home Assistant verbinden", null)
        }
        if (light.entityIds.isEmpty()) {
            return LightStepResult("Kein Licht ausgewählt", null)
        }
        val steps = PreWakeLightPlan.steps(
            light.toggleCount,
            PreWakeLightPlan.intervalSeconds(light.intervalAmount, light.intervalUnit)
        )
        val step = steps.getOrNull(index) ?: return LightStepResult("Licht ist fertig", null)
        val toggles = steps.count { !it.turnOn }
        val kind = if (step.turnOn) HaServiceKind.TURN_ON else HaServiceKind.TOGGLE
        light.entityIds.forEach { id ->
            repository.callHomeAssistant(
                home.url,
                home.token,
                id,
                kind,
                if (step.turnOn) light.brightness else null
            )
        }
        if (step.turnOn) {
            return LightStepResult("Wird ausgeführt", steps.getOrNull(index + 1)?.wait)
        }
        val toggleIndex = steps.take(index + 1).count { !it.turnOn }
        val next = steps.getOrNull(index + 1)?.wait
        val message = if (next == null) "Licht ist fertig" else "Stufe $toggleIndex von $toggles"
        return LightStepResult(message, next)
    }
}
