package io.qzz.xandll.morgo.presentation.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.AlarmSchedulerService
import io.qzz.xandll.morgo.data.service.SecondaryAlarmScheduler
import io.qzz.xandll.morgo.data.service.WakeMusic
import io.qzz.xandll.morgo.data.service.WakeSoundService
import io.qzz.xandll.morgo.domain.model.FollowUpPlan
import io.qzz.xandll.morgo.domain.model.HaServiceKind
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import io.qzz.xandll.morgo.presentation.ui.sunriseBrush
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var alarmSchedulerService: AlarmSchedulerService
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var repository: MorgoRepository
    @Inject lateinit var secondary: SecondaryAlarmScheduler

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val kind = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_KIND) ?: SecondaryAlarmScheduler.KIND_MAIN
        val label = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_LABEL).orEmpty()
        if (kind == SecondaryAlarmScheduler.KIND_MAIN) {
            executeWakeUpActions()
            openWakeMusic()
        }
        setContent {
            val appSettings by settingsRepository.settingsFlow.collectAsState()
            MorgoTheme(dynamicColor = appSettings.useDynamicColor) {
                val settings = appSettings.alarm
                val note = when {
                    kind == SecondaryAlarmScheduler.KIND_EXTRA -> "Extra-Wecker"
                    kind == SecondaryAlarmScheduler.KIND_MAIN && FollowUpPlan.shouldSchedule(true, settings) -> {
                        if (settings.followUpEvenIfAwake) {
                            "Zweiter Wecker in ${settings.followUpMinutes} Min., auch wenn du wach bist"
                        } else {
                            "Zweiter Wecker in ${settings.followUpMinutes} Min., bis du „Ich bin wach“ tippst"
                        }
                    }
                    else -> null
                }
                AlarmScreen(
                    snoozeMinutes = settings.snoozeMinutes,
                    headline = when (kind) {
                        SecondaryAlarmScheduler.KIND_FOLLOW_UP -> "Zweiter Wecker"
                        SecondaryAlarmScheduler.KIND_EXTRA -> label.ifBlank { "Extra-Wecker" }
                        else -> "Guten Morgen"
                    },
                    note = note,
                    showSnooze = kind == SecondaryAlarmScheduler.KIND_MAIN,
                    onDismiss = {
                        if (kind == SecondaryAlarmScheduler.KIND_MAIN) {
                            secondary.onAwakeConfirmed(settings)
                        }
                        stopSound()
                        finish()
                    },
                    onSnooze = { minutes ->
                        stopSound()
                        alarmSchedulerService.snoozeAlarm(minutes)
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            finishReceiver,
            IntentFilter(WakeSoundService.ACTION_FINISH),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(finishReceiver)
        super.onStop()
    }

    private fun stopSound() {
        startService(Intent(this, WakeSoundService::class.java).setAction(WakeSoundService.ACTION_STOP))
    }

    private fun openWakeMusic() {
        val alarm = settingsRepository.loadSettings().alarm
        if (alarm.wakeSound != WakeSound.MUSIC || alarm.music.kind == MusicKind.STREAM) return
        if (!WakeMusic.open(this, alarm.music)) {
            startService(Intent(this, WakeSoundService::class.java).setAction(WakeSoundService.ACTION_MUSIC_FAILED))
        }
    }

    private fun executeWakeUpActions() {
        val settings = settingsRepository.loadSettings()
        val haSettings = settings.homeAssistant
        if (haSettings.url.isBlank() || haSettings.token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            haSettings.wakeUpActions.forEach { entityId ->
                repository.callHomeAssistant(
                    haSettings.url,
                    haSettings.token,
                    entityId,
                    HaServiceKind.TURN_ON
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    headline: String = "Guten Morgen",
    note: String? = null,
    showSnooze: Boolean = true
) {
    Box(Modifier.fillMaxSize().background(sunriseBrush(dark = true))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(headline, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge
                )
                if (note != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(note, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Ich bin wach", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                if (showSnooze) {
                    OutlinedButton(
                        onClick = { onSnooze(snoozeMinutes) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Schlummern, $snoozeMinutes Min.", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
