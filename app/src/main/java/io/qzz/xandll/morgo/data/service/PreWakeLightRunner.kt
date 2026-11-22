package io.qzz.xandll.morgo.data.service

import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import io.qzz.xandll.morgo.util.ConnectionErrors
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

data class LightStepResult(
    val message: String,
    val nextDelay: Duration?,
    val progress: String? = null,
    val errors: List<String> = emptyList()
)

data class LightSequenceUpdate(
    val progress: String,
    val errors: List<String>,
    val done: Boolean
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
        val kind = if (step.turnOn) HaServiceKind.TURN_ON else HaServiceKind.TOGGLE
        val errors = ArrayList<String>()
        light.entityIds.forEach { id ->
            val result = repository.callHomeAssistant(
                home.url,
                home.token,
                id,
                kind,
                if (step.turnOn) light.brightness else null
            )
            if (result.isFailure) {
                val text = ConnectionErrors.describe(
                    result.exceptionOrNull(),
                    ConnectionErrors.Service.HOME_ASSISTANT
                ).message
                errors += "$id: $text"
            }
        }
        val label = PreWakeLightPlan.progress(index, steps.size)
        val next = steps.getOrNull(index + 1)?.wait
        val message = if (next == null && errors.isEmpty()) "Licht ist fertig" else label
        return LightStepResult(message, next, label, errors)
    }

    /**
     * Dieselbe Schrittfolge wie der echte Wecker. Beim Test sind die Pausen kürzer.
     * [active] wird vor jedem weiteren Schritt geprüft, damit sich der Lauf abbrechen lässt.
     */
    suspend fun runSequence(
        test: Boolean,
        pause: suspend (Duration) -> Unit = { delay(it.toMillis()) },
        active: () -> Boolean = { true },
        onUpdate: (LightSequenceUpdate) -> Unit
    ) {
        var index = 0
        val errors = mutableListOf<String>()
        while (active()) {
            val step = runStep(index)
            val label = step.progress
            if (label != null) {
                step.errors.forEach { errors += "$label: $it" }
            }
            val done = step.nextDelay == null
            onUpdate(
                LightSequenceUpdate(
                    progress = label ?: step.message,
                    errors = errors.toList(),
                    done = done
                )
            )
            val wait = step.nextDelay ?: return
            val gap = if (test) PreWakeLightPlan.testDelay(wait) else wait
            if (!gap.isZero) pause(gap)
            if (!active()) return
            index++
        }
    }
}
