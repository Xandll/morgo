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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.AlarmAlerts
import io.qzz.xandll.morgo.data.service.SecondaryAlarmScheduler
import io.qzz.xandll.morgo.data.service.WakeMusic
import io.qzz.xandll.morgo.data.service.WakeSoundService
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.model.FollowUpPlan
import io.qzz.xandll.morgo.domain.model.MusicKind
import io.qzz.xandll.morgo.domain.model.WakeSound
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import io.qzz.xandll.morgo.presentation.ui.sunriseBrush
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val kind = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_KIND) ?: SecondaryAlarmScheduler.KIND_MAIN
        val label = intent.getStringExtra(SecondaryAlarmScheduler.EXTRA_LABEL).orEmpty()
        if (kind == SecondaryAlarmScheduler.KIND_MAIN) {
            openWakeMusic()
        }
        setContent {
            val appSettings by settingsRepository.settingsFlow.collectAsState()
            MorgoTheme(dynamicColor = false) {
                val settings = appSettings.alarm
                val title = intent.getStringExtra(AlarmAlerts.EXTRA_TITLE)
                    ?: AlarmFace.title(kind, label, settingsRepository.alarmTitle())
                val note = when {
                    kind == SecondaryAlarmScheduler.KIND_MAIN && FollowUpPlan.shouldSchedule(true, settings) -> {
                        if (settings.followUpEvenIfAwake) {
                            "Zweiter Wecker in ${settings.followUpMinutes} Min., auch wenn du wach bist"
                        } else {
                            "Zweiter Wecker in ${settings.followUpMinutes} Min., bis du Stopp tippst"
                        }
                    }
                    else -> null
                }
                AlarmScreen(
                    snoozeMinutes = settings.snoozeMinutes,
                    headline = title,
                    note = note,
                    showSnooze = AlarmFace.showSnooze(kind, settings.snoozeMinutes, withSound = true),
                    onDismiss = {
                        startService(Intent(this@AlarmActivity, WakeSoundService::class.java).apply {
                            action = WakeSoundService.ACTION_AWAKE
                            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
                        })
                        finish()
                    },
                    onSnooze = { minutes ->
                        startService(Intent(this@AlarmActivity, WakeSoundService::class.java).apply {
                            action = WakeSoundService.ACTION_SNOOZE
                            putExtra(SecondaryAlarmScheduler.EXTRA_KIND, kind)
                            putExtra(AlarmAlerts.EXTRA_SNOOZE_MINUTES, minutes)
                        })
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

    private fun openWakeMusic() {
        val alarm = settingsRepository.loadSettings().alarm
        if (alarm.wakeSound != WakeSound.MUSIC || alarm.music.kind == MusicKind.STREAM) return
        if (!WakeMusic.open(this, alarm.music)) {
            startService(Intent(this, WakeSoundService::class.java).setAction(WakeSoundService.ACTION_MUSIC_FAILED))
        }
    }

}

@Composable
fun AlarmScreen(
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    headline: String = AlarmFace.DEFAULT_TITLE,
    note: String? = null,
    showSnooze: Boolean = true,
    time: LocalTime = LocalTime.now()
) {
    Box(Modifier.fillMaxSize().background(sunriseBrush(dark = true))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = Color.White,
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    headline,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (note != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        note,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (showSnooze) {
                    Button(
                        onClick = { onSnooze(snoozeMinutes) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFC2B8),
                            contentColor = Color(0xFF2A2038)
                        )
                    ) {
                        Text("Schlummern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A3158),
                        contentColor = Color.White
                    )
                ) {
                    Text("Stopp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
