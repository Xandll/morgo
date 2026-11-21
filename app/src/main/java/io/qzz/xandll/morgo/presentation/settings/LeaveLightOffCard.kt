package io.qzz.xandll.morgo.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import io.qzz.xandll.morgo.domain.model.LeaveOffChoice
import io.qzz.xandll.morgo.domain.model.PreWakeLightSettings
import io.qzz.xandll.morgo.presentation.ui.IconBadge
import io.qzz.xandll.morgo.presentation.ui.SoftCard
import io.qzz.xandll.morgo.presentation.ui.StepperRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveLightOffCard(
    light: PreWakeLightSettings,
    entities: List<HomeAssistantEntity>,
    onChange: (PreWakeLightSettings) -> Unit,
    onTest: () -> Unit
) {
    val known = entities.filter { HomeAssistantTargets.isSelectable(it.entityId) }
    val selected = light.entityIds.map { id ->
        known.find { it.entityId == id } ?: HomeAssistantEntity(
            id,
            id.substringAfter('.').replace('_', ' '),
            "",
            HomeAssistantTargets.type(id)
        )
    }
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.AutoMirrored.Outlined.DirectionsWalk, "Losgehen")
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Licht beim Losgehen ausschalten", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (light.leaveOffEnabled) offsetSummary(light.leaveOffOffsetMinutes) else "Noch aus",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = light.leaveOffEnabled,
                    onCheckedChange = { onChange(light.copy(leaveOffEnabled = it)) }
                )
            }
            Text(
                "Zum Losgehen: Abfahrt des Zugs minus Fußweg. 0 ist genau dann, ein Pluswert später. Nur an Tagen mit Wecker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StepperRow(
                title = "Versatz",
                valueLabel = offsetLabel(light.leaveOffOffsetMinutes),
                onMinus = {
                    onChange(light.copy(leaveOffOffsetMinutes = (light.leaveOffOffsetMinutes - 1).coerceAtLeast(-60)))
                },
                onPlus = {
                    onChange(light.copy(leaveOffOffsetMinutes = (light.leaveOffOffsetMinutes + 1).coerceAtMost(180)))
                }
            )
            if (selected.isEmpty()) {
                Text(
                    "Zuerst unter Licht vor dem Wecken etwas auswählen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            selected.forEach { entity ->
                val choice = (light.leaveOff[entity.entityId] ?: LeaveOffChoice()).normalized()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        entity.name.ifBlank { entity.entityId },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${HomeAssistantTargets.domainLabel(entity.entityId)} · ${entity.entityId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (HomeAssistantTargets.domain(entity.entityId)) {
                        "light", "switch", "input_boolean" -> Text(
                            "Wird mit turn_off ausgeschaltet. Ein zweites Mal ändert nichts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        "script" -> {
                            Text(
                                "Skripte schalten nur um. Das Skript wird so oft aufgerufen, oder du wählst ein eigenes Aus-Skript.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (choice.offEntityId.isBlank()) {
                                StepperRow(
                                    title = "Aufrufe",
                                    valueLabel = choice.calls.toString(),
                                    onMinus = {
                                        onChange(light.withChoice(entity.entityId, choice.copy(calls = choice.calls - 1)))
                                    },
                                    onPlus = {
                                        onChange(light.withChoice(entity.entityId, choice.copy(calls = choice.calls + 1)))
                                    }
                                )
                            }
                            OffEntityPicker(
                                label = "Eigenes Aus-Skript",
                                selected = choice.offEntityId,
                                options = (known + selected).distinctBy { it.entityId },
                                onSelect = { id ->
                                    onChange(light.withChoice(entity.entityId, choice.copy(offEntityId = id)))
                                }
                            )
                        }
                        "scene" -> {
                            Text(
                                "Eine Szene kann nicht ausgehen. Optional eine eigene Aus-Szene oder ein Aus-Skript.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OffEntityPicker(
                                label = "Aus-Entity",
                                selected = choice.offEntityId,
                                options = (known + selected).distinctBy { it.entityId }.filter { it.entityId != entity.entityId },
                                onSelect = { id ->
                                    onChange(light.withChoice(entity.entityId, choice.copy(offEntityId = id)))
                                }
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onTest,
                enabled = light.entityIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text("Jetzt ausschalten testen")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffEntityPicker(
    label: String,
    selected: String,
    options: List<HomeAssistantEntity>,
    onSelect: (String) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.find { it.entityId == selected }
    val shown = when {
        selected.isBlank() -> "keins"
        current != null -> current.name.ifBlank { current.entityId }
        else -> selected
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("keins") },
                onClick = {
                    onSelect("")
                    open = false
                }
            )
            options.forEach { entity ->
                DropdownMenuItem(
                    text = {
                        Text("${entity.name.ifBlank { entity.entityId }} · ${HomeAssistantTargets.domainLabel(entity.entityId)}")
                    },
                    onClick = {
                        onSelect(entity.entityId)
                        open = false
                    }
                )
            }
        }
    }
}

private fun PreWakeLightSettings.withChoice(entityId: String, choice: LeaveOffChoice): PreWakeLightSettings {
    return copy(leaveOff = leaveOff + (entityId to choice.normalized()))
}

private fun offsetLabel(minutes: Int): String {
    return when {
        minutes > 0 -> "+$minutes min"
        else -> "$minutes min"
    }
}

private fun offsetSummary(minutes: Int): String {
    return when {
        minutes > 0 -> "$minutes min nach dem Losgehen"
        minutes < 0 -> "${-minutes} min vor dem Losgehen"
        else -> "Genau zum Losgehen"
    }
}
