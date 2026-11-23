package io.qzz.xandll.morgo.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.qzz.xandll.morgo.domain.model.AlarmPlan
import io.qzz.xandll.morgo.domain.model.ConnectionStatus
import io.qzz.xandll.morgo.domain.model.DayOverrideMode
import io.qzz.xandll.morgo.domain.model.HomeAssistantEntity
import io.qzz.xandll.morgo.domain.model.WakeActionList
import io.qzz.xandll.morgo.presentation.ui.SoftCard
import io.qzz.xandll.morgo.presentation.ui.sunriseBrush
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    openOverride: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val entities by viewModel.homeAssistantEntities.collectAsState()
    val showOverride by viewModel.showOverride.collectAsState()

    LaunchedEffect(openOverride) {
        if (openOverride) viewModel.openOverride()
    }

    MorningDashboard(
        state = uiState,
        entities = entities,
        onSettings = onNavigateToSettings,
        onRefresh = { viewModel.loadData() },
        onChangeTomorrow = { viewModel.openOverride() },
        onTurnOff = { viewModel.applyOverride(DayOverrideMode.OFF, null) },
        onActivate = { viewModel.activateEntity(it) }
    )

    if (showOverride) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeOverride() },
            sheetState = sheetState
        ) {
            TomorrowEditor(
                onCustom = { viewModel.applyOverride(DayOverrideMode.CUSTOM, it) },
                onOff = { viewModel.applyOverride(DayOverrideMode.OFF, null) },
                onForce = { viewModel.applyOverride(DayOverrideMode.FORCE_ON, null) },
                onDismiss = { viewModel.closeOverride() }
            )
        }
    }
}

@Composable
fun MorningDashboard(
    state: HomeUiState,
    entities: List<HomeAssistantEntity>,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onChangeTomorrow: () -> Unit,
    onTurnOff: () -> Unit,
    onActivate: (String) -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        when (state) {
            HomeUiState.Loading -> LoadingMorning(dark, onSettings, padding)
            is HomeUiState.Error -> ErrorMorning(state.message, onRefresh, onSettings, padding)
            is HomeUiState.Ready -> ReadyMorning(
                state = state,
                entities = entities,
                dark = dark,
                onSettings = onSettings,
                onChangeTomorrow = onChangeTomorrow,
                onTurnOff = onTurnOff,
                onActivate = onActivate,
                padding = padding
            )
        }
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
private fun LoadingMorning(dark: Boolean, onSettings: () -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        HeroHeader(dark = dark, dateLabel = "Morgen wird vorbereitet", onSettings = onSettings) {
            val ink = if (dark) Color.White else Color(0xFF1E2350)
            CircularProgressIndicator(color = ink, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(16.dp))
            Text("Stundenplan und Zug werden gelesen …", color = ink)
        }
    }
}

@Composable
private fun ErrorMorning(message: String, onRefresh: () -> Unit, onSettings: () -> Unit, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.WbSunny, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Der Morgen lässt sich gerade nicht laden", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("Erneut versuchen") }
        TextButton(onClick = onSettings) { Text("Einstellungen") }
    }
}

@Composable
private fun ReadyMorning(
    state: HomeUiState.Ready,
    entities: List<HomeAssistantEntity>,
    dark: Boolean,
    onSettings: () -> Unit,
    onChangeTomorrow: () -> Unit,
    onTurnOff: () -> Unit,
    onActivate: (String) -> Unit,
    padding: PaddingValues
) {
    val plan = state.plan
    val dateLabel = state.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            HeroHeader(dark = dark, dateLabel = dateLabel, onSettings = onSettings) {
                HeroTime(plan, state.date, state.lightHint, state.leaveHint, state.extraLines)
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TrainCard(plan)
                SmartHomeCard(state.wakeActions, entities, onActivate)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onChangeTomorrow,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) { Text("Morgen anders") }
                    FilledTonalButton(
                        onClick = onTurnOff,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                    ) { Text("Morgen aus") }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    dark: Boolean,
    dateLabel: String,
    onSettings: () -> Unit,
    body: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(sunriseBrush(dark))
            .statusBarsPadding()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 36.dp)
    ) {
        val ink = if (dark) Color.White else Color(0xFF1E2350)
        val muted = ink.copy(alpha = 0.72f)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Morgo", color = muted, style = MaterialTheme.typography.labelLarge)
                    Text(dateLabel, color = ink, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen", tint = ink)
                }
            }
            Spacer(Modifier.height(18.dp))
            body()
        }
    }
}

@Composable
private fun HeroTime(
    plan: AlarmPlan?,
    date: LocalDate,
    lightHint: String?,
    leaveHint: String?,
    extraLines: List<String>
) {
    val ink = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF1E2350)
    val rings = plan?.rings == true
    val timeText = if (rings && plan != null) {
        plan.alarmTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        "Kein Wecker"
    }
    AnimatedContent(targetState = timeText, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "weckzeit") { text ->
        Text(
            text = text,
            color = ink,
            style = if (rings) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { contentDescription = "Nächster Wecker $text" }
        )
    }
    if (rings && plan != null) {
        Countdown(date, plan.alarmTime)
        val detail = buildString {
            plan.lessonNumber?.let { append("$it. Stunde") }
            plan.firstLesson?.let {
                if (isNotEmpty()) append(" ")
                append(it.startTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                if (it.subject.isNotBlank()) append(" · ${it.subject}")
            }
            if (isEmpty()) append(plan.reason)
        }
        Text(detail, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        if (lightHint != null) {
            Text(lightHint, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
        }
        if (leaveHint != null) {
            Text(leaveHint, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
        }
        extraLines.forEach { line ->
            Text(line, color = ink.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
        }
        if (plan.usedCache) {
            Text("Stundenplan aus dem Zwischenspeicher", color = ink, style = MaterialTheme.typography.bodyMedium)
        }
        if (plan.uncertain) {
            Text("WebUntis nicht erreichbar, der Wecker bleibt an", color = ink, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Text(
            plan?.reason ?: "Wecker ist ausgeschaltet",
            color = ink.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun Countdown(date: LocalDate, time: LocalTime) {
    var label by remember { mutableStateOf("") }
    LaunchedEffect(date, time) {
        while (true) {
            val minutes = Duration.between(LocalDateTime.now(), date.atTime(time)).toMinutes()
            label = when {
                minutes <= 0 -> "Gleich"
                minutes < 60 -> "in $minutes Minuten"
                else -> "in ${minutes / 60} Std. ${minutes % 60} Min."
            }
            delay(30_000)
        }
    }
    if (label.isNotEmpty()) {
        val ink = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else Color(0xFF1E2350)
        Text(label, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun TrainCard(plan: AlarmPlan?) {
    val train = plan?.trainConnection
    SoftCard {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Train, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("  Zug", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            if (train == null) {
                Text(
                    if (plan?.rings == false) "Morgen ist kein Zug nötig." else "Noch keine Verbindung für diesen Morgen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val (label, color) = when (train.status) {
                    ConnectionStatus.ON_TIME -> "pünktlich" to Color(0xFF1B7F4E)
                    ConnectionStatus.DELAYED -> "verspätet" to Color(0xFFC56A00)
                    ConnectionStatus.CANCELLED -> "fällt aus" to Color(0xFFC62828)
                }
                Text(
                    "${train.departure.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ab ${train.departure.station}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text("  $label", color = color, fontWeight = FontWeight.SemiBold)
                    Text(
                        "  ·  an ${train.arrival.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ${train.arrival.station}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SmartHomeCard(
    actions: List<String>,
    entities: List<HomeAssistantEntity>,
    onActivate: (String) -> Unit
) {
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text("  Smarthome", style = MaterialTheme.typography.titleMedium)
            }
            val items = WakeActionList.resolve(actions, entities)
            if (items.isEmpty()) {
                Text(
                    "Beim Wecken passiert hier noch nichts. Home Assistant kannst du unter Smarthome verbinden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${items.size} Aktion${if (items.size == 1) "" else "en"} beim Wecken",
                    style = MaterialTheme.typography.bodyLarge
                )
                items.forEach { item ->
                    TextButton(onClick = { onActivate(item.entityId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(item.name)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TomorrowEditor(
    onCustom: (LocalTime) -> Unit,
    onOff: () -> Unit,
    onForce: () -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberTimePickerState(initialHour = 6, initialMinute = 30, is24Hour = true)
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Nur für morgen", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Gilt einmal und wird danach verworfen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TimePicker(state = picker)
        Button(
            onClick = { onCustom(LocalTime.of(picker.hour, picker.minute)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Diese Weckzeit") }
        FilledTonalButton(onClick = onOff, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Wecker morgen aus")
        }
        OutlinedButton(onClick = onForce, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Trotzdem einschalten")
        }
        TextButton(onClick = onDismiss) { Text("Abbrechen") }
    }
}
