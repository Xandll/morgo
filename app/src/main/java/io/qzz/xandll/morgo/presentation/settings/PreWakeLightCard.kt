package io.qzz.xandll.morgo.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.HomeAssistantTargets
import io.qzz.xandll.morgo.domain.model.LightIntervalUnit
import io.qzz.xandll.morgo.domain.model.PreWakeLightPlan
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import io.qzz.xandll.morgo.presentation.ui.IconBadge
import io.qzz.xandll.morgo.presentation.ui.SoftCard
import io.qzz.xandll.morgo.presentation.ui.StepperRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreWakeLightCard(
    light: PreWakeLightSettings,
    entities: List<HomeAssistantEntity>,
    onChange: (PreWakeLightSettings) -> Unit,
    onLoad: () -> Unit,
    onTest: () -> Unit,
    loading: Boolean = false,
    statusMessage: String? = null,
    initialQuery: String = "",
    testRunning: Boolean = false,
    testProgress: String? = null,
    testErrors: List<String> = emptyList(),
    onCancel: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val choices = entities.filter { HomeAssistantTargets.isSelectable(it.entityId) }
    val knownIds = choices.map { it.entityId }.toSet()
    val extras = light.entityIds.filter { it !in knownIds }.map { id ->
        HomeAssistantEntity(
            id,
            id.substringAfter('.').replace('_', ' '),
            "",
            HomeAssistantTargets.type(id)
        )
    }
    val shown = (choices + extras).filter { entity ->
        val label = HomeAssistantTargets.domainLabel(entity.entityId)
        query.isBlank() ||
            entity.name.contains(query, ignoreCase = true) ||
            entity.entityId.contains(query, ignoreCase = true) ||
            label.contains(query, ignoreCase = true)
    }
    val millis = light.delayMillis()
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.WbSunny, "Licht")
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Licht vor dem Wecken", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (light.isActive()) light.summaryLine() else "Noch nicht aktiv",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = light.enabled,
                    onCheckedChange = { onChange(light.copy(enabled = it)) }
                )
            }
            Text(
                "Lampen, Schalter und Helfer werden umgeschaltet. Bei Skripten und Szenen ist jeder Schritt ein Aufruf.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StepperRow(
                title = "Vorlauf",
                valueLabel = "${light.leadMinutes} min",
                onMinus = { onChange(light.copy(leadMinutes = (light.leadMinutes - 1).coerceAtLeast(0))) },
                onPlus = { onChange(light.copy(leadMinutes = (light.leadMinutes + 1).coerceAtMost(120))) }
            )
            StepperRow(
                title = "Toggles",
                valueLabel = light.toggleCount.coerceIn(0, 10).toString(),
                onMinus = { onChange(light.copy(toggleCount = (light.toggleCount - 1).coerceAtLeast(0))) },
                onPlus = { onChange(light.copy(toggleCount = (light.toggleCount + 1).coerceAtMost(10))) }
            )
            StepperRow(
                title = "Abstand",
                valueLabel = PreWakeLightPlan.formatInterval(millis, light.intervalUnit),
                onMinus = {
                    val next = if (light.intervalUnit == LightIntervalUnit.MINUTES) {
                        PreWakeLightPlan.stepMinutes(millis, -1)
                    } else {
                        PreWakeLightPlan.stepSeconds(millis, -1)
                    }
                    onChange(light.copy(intervalMillis = next))
                },
                onPlus = {
                    val next = if (light.intervalUnit == LightIntervalUnit.MINUTES) {
                        PreWakeLightPlan.stepMinutes(millis, 1)
                    } else {
                        PreWakeLightPlan.stepSeconds(millis, 1)
                    }
                    onChange(light.copy(intervalMillis = next))
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = light.intervalUnit == LightIntervalUnit.SECONDS,
                    onClick = {
                        onChange(
                            light.copy(
                                intervalUnit = LightIntervalUnit.SECONDS,
                                intervalMillis = PreWakeLightPlan.switchUnit(
                                    millis,
                                    light.intervalUnit,
                                    LightIntervalUnit.SECONDS
                                )
                            )
                        )
                    },
                    label = { Text("Sekunden") }
                )
                FilterChip(
                    selected = light.intervalUnit == LightIntervalUnit.MINUTES,
                    onClick = {
                        onChange(
                            light.copy(
                                intervalUnit = LightIntervalUnit.MINUTES,
                                intervalMillis = PreWakeLightPlan.switchUnit(
                                    millis,
                                    light.intervalUnit,
                                    LightIntervalUnit.MINUTES
                                )
                            )
                        )
                    },
                    label = { Text("Minuten") }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Helligkeit mitsenden", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Schickt die Helligkeit mit, wenn die Lampe das kann.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = light.brightness != null,
                    onCheckedChange = { on ->
                        onChange(light.copy(brightness = if (on) light.brightness ?: 204 else null))
                    }
                )
            }
            if (light.brightness != null) {
                val percent = (light.brightness * 100 / 255).coerceIn(1, 100)
                StepperRow(
                    title = "Helligkeit",
                    valueLabel = "$percent %",
                    onMinus = { onChange(light.copy(brightness = percentToBrightness(percent - 5))) },
                    onPlus = { onChange(light.copy(brightness = percentToBrightness(percent + 5))) }
                )
            }
            Text("Geräte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Name suchen") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextButton(onClick = onLoad, enabled = !loading) { Text(if (loading) "Lade Geräte …" else "Geräte laden") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.error)
            }
            if (shown.isEmpty() && !loading) {
                Text(
                    if (choices.isEmpty() && extras.isEmpty()) {
                        "Noch nichts Passendes. Lade Lampen, Schalter, Helfer, Skripte oder Szenen."
                    } else {
                        "Nichts passt zur Suche."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            shown.forEach { entity ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Checkbox(
                        checked = light.entityIds.contains(entity.entityId),
                        onCheckedChange = { checked ->
                            val next = if (checked) light.entityIds + entity.entityId else light.entityIds - entity.entityId
                            onChange(light.copy(entityIds = next.distinct()))
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
            if (light.entityIds.isNotEmpty()) {
                Text(
                    "${light.entityIds.size} ausgewählt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { if (testRunning) onCancel() else onTest() },
                enabled = testRunning || light.entityIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(if (testRunning) "Abbrechen" else "Jetzt testen")
            }
            Text(
                "Spielt alle Stufen für die gewählten Einträge, in derselben Reihenfolge wie beim Wecken. Der Abstand ist beim Test kürzer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!testProgress.isNullOrBlank()) {
                Text(testProgress, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            testErrors.forEach { line ->
                Text(line, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (light.entityIds.isEmpty()) {
                Text(
                    "Wähle mindestens einen Eintrag, dann kannst du die Sequenz sofort ausprobieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun percentToBrightness(percent: Int): Int {
    return (percent.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)
}
