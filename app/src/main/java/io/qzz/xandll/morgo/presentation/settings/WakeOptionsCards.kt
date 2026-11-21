package io.qzz.xandll.morgo.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import io.qzz.xandll.morgo.data.datasource.RadioStation
import io.qzz.xandll.morgo.domain.model.AlarmSettings
import io.qzz.xandll.morgo.domain.model.ExtraAlarm
import io.qzz.xandll.morgo.domain.model.ExtraAlarmPlan
import io.qzz.xandll.morgo.domain.model.Lesson
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.SonaraCatalog
import io.qzz.xandll.morgo.domain.model.SonaraPlaylist
import io.qzz.xandll.morgo.domain.model.SonaraShuffle
import io.qzz.xandll.morgo.domain.model.SpotifyLink
import io.qzz.xandll.morgo.domain.model.WakeSound
import java.time.LocalDate
import io.qzz.xandll.morgo.presentation.ui.IconBadge
import io.qzz.xandll.morgo.presentation.ui.SoftCard
import io.qzz.xandll.morgo.presentation.ui.StepperRow
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeSoundCard(
    alarm: AlarmSettings,
    onChange: (AlarmSettings) -> Unit,
    radioResults: List<RadioStation>,
    onSearchRadio: (String) -> Unit,
    onPickApp: () -> Unit,
    onTest: () -> Unit,
    initialRadioQuery: String = "",
    sonaraPlaylists: List<SonaraPlaylist> = emptyList()
) {
    var radioQuery by rememberSaveable { mutableStateOf(initialRadioQuery) }
    val music = alarm.music
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.MusicNote, "Weck-Art")
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Weck-Art", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (alarm.wakeSound == WakeSound.RINGTONE) "Klingelton" else music.summary(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = alarm.wakeSound == WakeSound.RINGTONE,
                    onClick = { onChange(alarm.copy(wakeSound = WakeSound.RINGTONE)) },
                    label = { Text("Klingelton") }
                )
                FilterChip(
                    selected = alarm.wakeSound == WakeSound.MUSIC,
                    onClick = { onChange(alarm.copy(wakeSound = WakeSound.MUSIC)) },
                    label = { Text("Musik") }
                )
            }
            if (alarm.wakeSound == WakeSound.MUSIC) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = music.kind == MusicKind.SPOTIFY, onClick = { onChange(alarm.copy(music = music.copy(kind = MusicKind.SPOTIFY))) }, label = { Text("Spotify") })
                    FilterChip(selected = music.kind == MusicKind.STREAM, onClick = { onChange(alarm.copy(music = music.copy(kind = MusicKind.STREAM))) }, label = { Text("Radio") })
                    FilterChip(selected = music.kind == MusicKind.APP, onClick = { onChange(alarm.copy(music = music.copy(kind = MusicKind.APP))) }, label = { Text("App") })
                }
                when (music.kind) {
                    MusicKind.SPOTIFY -> {
                        OutlinedTextField(
                            value = music.spotifyInput,
                            onValueChange = { onChange(alarm.copy(music = music.copy(spotifyInput = it))) },
                            label = { Text("Spotify-Link oder URI") },
                            placeholder = { Text("spotify:playlist:… oder open.spotify.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        val parsed = SpotifyLink.parse(music.spotifyInput)
                        Text(
                            parsed ?: "Link noch nicht erkannt. Playlist, Album oder Titel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MusicKind.STREAM -> {
                        OutlinedTextField(
                            value = radioQuery,
                            onValueChange = { radioQuery = it },
                            label = { Text("Sender suchen") },
                            placeholder = { Text("z. B. Ö3") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        TextButton(onClick = { onSearchRadio(radioQuery) }) { Text("Suchen") }
                        radioResults.forEach { station ->
                            TextButton(
                                onClick = {
                                    onChange(alarm.copy(music = music.copy(streamUrl = station.streamUrl, streamTitle = station.name)))
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(station.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        station.country.ifBlank { station.streamUrl },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = music.streamUrl,
                            onValueChange = { onChange(alarm.copy(music = music.copy(streamUrl = it))) },
                            label = { Text("Stream-URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = music.streamTitle,
                            onValueChange = { onChange(alarm.copy(music = music.copy(streamTitle = it))) },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = music.radioBrowserHost,
                            onValueChange = { onChange(alarm.copy(music = music.copy(radioBrowserHost = it))) },
                            label = { Text("Suchserver") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    MusicKind.APP -> {
                        Button(onClick = onPickApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("App auswählen")
                        }
                        OutlinedTextField(
                            value = music.searchQuery,
                            onValueChange = { onChange(alarm.copy(music = music.copy(searchQuery = it))) },
                            label = { Text("Suchbegriff") },
                            placeholder = { Text("Playlist oder Sender") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (music.appLabel.isNotBlank()) {
                            Text("Gewählt: ${music.appLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (music.appPackage == SonaraShuffle.PACKAGE) {
                            SonaraPlaylistField(
                                selectedId = music.sonaraPlaylistId,
                                selectedTitle = music.sonaraPlaylistTitle,
                                browsed = sonaraPlaylists,
                                onSelect = { playlist ->
                                    onChange(
                                        alarm.copy(
                                            music = music.copy(
                                                sonaraPlaylistId = playlist.id,
                                                sonaraPlaylistTitle = playlist.title
                                            )
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                if (music.kind != MusicKind.STREAM) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Zufällige Wiedergabe")
                            Text(
                                "Morgo bittet die App darum. Klappt das nicht, startet sie wie bisher.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = music.shuffle,
                            onCheckedChange = { onChange(alarm.copy(music = music.copy(shuffle = it))) }
                        )
                    }
                }
                StepperRow("Lauter werden", "${music.rampSeconds} s", {
                    onChange(alarm.copy(music = music.copy(rampSeconds = (music.rampSeconds - 5).coerceAtLeast(0))))
                }, {
                    onChange(alarm.copy(music = music.copy(rampSeconds = (music.rampSeconds + 5).coerceAtMost(120))))
                })
                StepperRow("Fallback nach", "${music.fallbackSeconds} s", {
                    onChange(alarm.copy(music = music.copy(fallbackSeconds = (music.fallbackSeconds - 5).coerceAtLeast(5))))
                }, {
                    onChange(alarm.copy(music = music.copy(fallbackSeconds = (music.fallbackSeconds + 5).coerceAtMost(60))))
                })
                StepperRow("Lautstärke", "${music.volumePercent} %", {
                    onChange(alarm.copy(music = music.copy(volumePercent = (music.volumePercent - 5).coerceAtLeast(10))))
                }, {
                    onChange(alarm.copy(music = music.copy(volumePercent = (music.volumePercent + 5).coerceAtMost(100))))
                })
                Text(
                    "Streams spielt Morgo selbst und wird langsam lauter. Spotify und andere Apps öffnet der Wecker auf dem Bildschirm. Kommt ${music.fallbackSeconds} s nichts oder kein Netz, klingelt der Klingelton.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onTest, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Jetzt testen")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SonaraPlaylistField(
    selectedId: String,
    selectedTitle: String,
    browsed: List<SonaraPlaylist>,
    onSelect: (SonaraPlaylist) -> Unit
) {
    val choices = SonaraCatalog.choices(browsed, selectedId, selectedTitle)
    val shown = choices.firstOrNull { it.id == selectedId.trim() }?.title ?: SonaraShuffle.FAVORITES
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text("Playlist") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.title) },
                    onClick = {
                        onSelect(playlist)
                        open = false
                    }
                )
            }
        }
    }
    Text(
        "Ohne erreichbare Liste bleiben die Lieblingssongs. Gemischt spielt Sonara sie erst, wenn sie den Befehl anbietet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun FollowUpCard(alarm: AlarmSettings, onChange: (AlarmSettings) -> Unit) {
    val music = alarm.wakeSound == WakeSound.MUSIC
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Alarm, "Zweiter Wecker")
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Zweiter Wecker", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (music) "Bei Musik aus" else if (alarm.followUpEnabled) "${alarm.followUpMinutes} Min. nach dem Wecker" else "Aus",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = alarm.followUpEnabled && !music,
                    onCheckedChange = { onChange(alarm.copy(followUpEnabled = it)) },
                    enabled = !music
                )
            }
            if (music) {
                Text(
                    "Bei Musik gibt es keinen zweiten Wecker.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (alarm.followUpEnabled) {
                StepperRow("Abstand", "${alarm.followUpMinutes} Min.", {
                    onChange(alarm.copy(followUpMinutes = (alarm.followUpMinutes - 1).coerceAtLeast(1)))
                }, {
                    onChange(alarm.copy(followUpMinutes = (alarm.followUpMinutes + 1).coerceAtMost(60)))
                })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Trotzdem läuten", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Auch nach „Ich bin wach“. Schlummern stoppt ihn nicht.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = alarm.followUpEvenIfAwake,
                        onCheckedChange = { onChange(alarm.copy(followUpEvenIfAwake = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExtraAlarmsCard(
    alarm: AlarmSettings,
    subjects: List<String>,
    onChange: (AlarmSettings) -> Unit,
    onLoadSubjects: () -> Unit,
    expanded: Boolean = false,
    lessons: List<Lesson> = emptyList(),
    today: LocalDate = LocalDate.now()
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(if (expanded) alarm.extraAlarms.firstOrNull()?.id else null) }
    val shown = subjects.filter { query.isBlank() || it.contains(query, ignoreCase = true) }.take(8)
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Notifications, "Extra-Wecker")
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Extra-Wecker", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Nur an Tagen, an denen das Fach stattfindet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            alarm.extraAlarms.forEach { extra ->
                ExtraRow(extra, editing == extra.id, shown, query, { query = it }, onLoadSubjects, lessons, today) { updated ->
                    if (updated == null) {
                        onChange(alarm.copy(extraAlarms = alarm.extraAlarms.filter { it.id != extra.id }))
                        if (editing == extra.id) editing = null
                    } else {
                        onChange(alarm.copy(extraAlarms = alarm.extraAlarms.map { if (it.id == extra.id) updated else it }))
                    }
                }
                TextButton(onClick = { editing = if (editing == extra.id) null else extra.id }) {
                    Text(if (editing == extra.id) "Fertig" else "Bearbeiten")
                }
            }
            val atLimit = alarm.extraAlarms.size >= 16
            TextButton(
                onClick = {
                    if (atLimit) return@TextButton
                    val id = UUID.randomUUID().toString().take(8)
                    onChange(alarm.copy(extraAlarms = alarm.extraAlarms + ExtraAlarm(id, "", "", 15, true)))
                    editing = id
                },
                enabled = !atLimit
            ) { Text("Extra-Wecker hinzufügen") }
            if (atLimit) {
                Text(
                    "Höchstens 16 Extra-Wecker.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ExtraRow(
    extra: ExtraAlarm,
    open: Boolean,
    subjects: List<String>,
    query: String,
    onQuery: (String) -> Unit,
    onLoad: () -> Unit,
    lessons: List<Lesson>,
    today: LocalDate,
    onChange: (ExtraAlarm?) -> Unit
) {
    val schedule = ExtraAlarmPlan.schedule(extra.subject, lessons, today)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            listOf(extra.label.ifBlank { "Ohne Namen" }, extra.subject.ifBlank { "kein Fach" }, "${extra.minutesAfterWake} Min.").joinToString(" · "),
            style = MaterialTheme.typography.titleMedium
        )
        schedule.headline()?.let { headline ->
            Text(
                headline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (schedule.upcoming.size > 1) {
            schedule.upcoming.forEach { line ->
                Text(
                    line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (!open) return
        OutlinedTextField(extra.label, { onChange(extra.copy(label = it)) }, label = { Text("Text") }, placeholder = { Text("Turnzeug einpacken") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(query, onQuery, label = { Text("Fach suchen") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TextButton(onClick = onLoad) { Text("Fächer aus WebUntis laden") }
        subjects.forEach { subject ->
            TextButton(onClick = { onChange(extra.copy(subject = subject)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (extra.subject == subject) "$subject  ✓" else subject)
            }
        }
        if (extra.subject.isNotBlank()) Text("Fach: ${extra.subject}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepperRow("Nach dem Aufstehen", "${extra.minutesAfterWake} Min.", {
            onChange(extra.copy(minutesAfterWake = (extra.minutesAfterWake - 5).coerceAtLeast(0)))
        }, {
            onChange(extra.copy(minutesAfterWake = (extra.minutesAfterWake + 5).coerceAtMost(180)))
        })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (extra.withSound) "Mit Ton" else "Nur Benachrichtigung", modifier = Modifier.weight(1f))
            Switch(checked = extra.withSound, onCheckedChange = { onChange(extra.copy(withSound = it)) })
        }
        TextButton(onClick = { onChange(null) }) { Text("Entfernen") }
    }
}
