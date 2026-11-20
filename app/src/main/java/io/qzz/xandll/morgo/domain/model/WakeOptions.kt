package io.qzz.xandll.morgo.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class WakeSound {
    RINGTONE,
    MUSIC
}

enum class MusicKind {
    SPOTIFY,
    STREAM,
    APP
}

data class MusicAlarmSettings(
    val kind: MusicKind = MusicKind.STREAM,
    val spotifyInput: String = "",
    val streamUrl: String = "",
    val streamTitle: String = "",
    val appPackage: String = "",
    val appLabel: String = "",
    val searchQuery: String = "",
    val rampSeconds: Int = 30,
    val fallbackSeconds: Int = 20,
    val volumePercent: Int = 70,
    val radioBrowserHost: String = "https://de1.radio-browser.info"
) {
    fun needsNetwork(): Boolean = kind == MusicKind.SPOTIFY || kind == MusicKind.STREAM

    fun summary(): String = when (kind) {
        MusicKind.SPOTIFY -> SpotifyLink.parse(spotifyInput)?.let { "Spotify" } ?: "Spotify-Link fehlt"
        MusicKind.STREAM -> streamTitle.ifBlank { streamUrl.ifBlank { "Stream fehlt" } }
        MusicKind.APP -> appLabel.ifBlank { appPackage.ifBlank { "App fehlt" } }
    }
}

data class ExtraAlarm(
    val id: String,
    val label: String,
    val subject: String,
    val minutesAfterWake: Int = 15,
    val withSound: Boolean = true
)

data class DueExtra(
    val alarm: ExtraAlarm,
    val at: LocalDateTime
)

object FollowUpPlan {
    fun at(wake: LocalDateTime, minutes: Int): LocalDateTime {
        return wake.plusMinutes(minutes.coerceAtLeast(1).toLong())
    }

    fun shouldSchedule(rings: Boolean, alarm: AlarmSettings): Boolean {
        return rings &&
            alarm.wakeSound == WakeSound.RINGTONE &&
            alarm.followUpEnabled &&
            alarm.followUpMinutes > 0
    }

    /** Schlummern lässt den Nachwecker stehen. Nur „Ich bin wach“ kann ihn abbrechen. */
    fun cancelWhenConfirmedAwake(alarm: AlarmSettings): Boolean = !alarm.followUpEvenIfAwake
}

object ExtraAlarmPlan {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun normalize(subject: String): String {
        return subject.trim().replace(Regex("\\s+"), " ").lowercase()
    }

    fun matches(lessonSubject: String, wanted: String): Boolean {
        val lesson = normalize(lessonSubject)
        val target = normalize(wanted)
        return lesson.isNotEmpty() && lesson == target
    }

    fun activeSubjects(lessons: List<Lesson>, date: LocalDate): List<String> {
        return lessons
            .filter { it.date == date && it.status != LessonStatus.CANCELLED }
            .map { it.subject.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun due(wake: LocalDateTime, subjects: List<String>, extras: List<ExtraAlarm>): List<DueExtra> {
        return extras.mapNotNull { extra ->
            if (extra.label.isBlank() || extra.subject.isBlank()) return@mapNotNull null
            if (subjects.none { matches(it, extra.subject) }) return@mapNotNull null
            DueExtra(extra, wake.plusMinutes(extra.minutesAfterWake.coerceAtLeast(0).toLong()))
        }
    }

    fun lines(wake: LocalTime, subjects: List<String>, extras: List<ExtraAlarm>): List<String> {
        val wakeAt = LocalDate.of(2024, 1, 2).atTime(wake)
        return due(wakeAt, subjects, extras).map { item ->
            "${item.at.toLocalTime().format(clock)} ${item.label()}"
        }
    }

    private fun DueExtra.label(): String = alarm.label.trim()
}

object SpotifyLink {
    private val uri = Regex("spotify:(playlist|album|track|show|episode|artist):([A-Za-z0-9]+)")
    private val web = Regex(
        "https?://open\\.spotify\\.com/(?:intl-[a-z]{2}/)?(playlist|album|track|show|episode|artist)/([A-Za-z0-9]+)"
    )

    fun parse(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        uri.find(raw)?.let { return "spotify:${it.groupValues[1]}:${it.groupValues[2]}" }
        web.find(raw)?.let { return "spotify:${it.groupValues[1]}:${it.groupValues[2]}" }
        return null
    }
}

object WakeSoundMath {
    fun volumeAt(elapsedMs: Long, rampMs: Long, target: Float): Float {
        if (rampMs <= 0L) return target.coerceIn(0f, 1f)
        val fraction = (elapsedMs.toFloat() / rampMs.toFloat()).coerceIn(0f, 1f)
        return (target.coerceIn(0f, 1f) * fraction)
    }

    fun fallbackNow(
        needsNetwork: Boolean,
        online: Boolean,
        elapsedSeconds: Int,
        playing: Boolean,
        limitSeconds: Int
    ): Boolean {
        if (playing) return false
        if (needsNetwork && !online && elapsedSeconds >= 3) return true
        return elapsedSeconds >= limitSeconds.coerceAtLeast(5)
    }
}

object ExtraAlarmCodec {
    fun encode(alarms: List<ExtraAlarm>): String {
        return alarms.joinToString("\n") { alarm ->
            listOf(
                alarm.id,
                alarm.label,
                alarm.subject,
                alarm.minutesAfterWake.toString(),
                if (alarm.withSound) "1" else "0"
            ).joinToString("\t") { field ->
                field.replace("\\", "\\\\").replace("\t", " ").replace("\n", " ")
            }
        }
    }

    fun decode(raw: String): List<ExtraAlarm> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 5) return@mapNotNull null
            ExtraAlarm(
                id = parts[0],
                label = parts[1],
                subject = parts[2],
                minutesAfterWake = parts[3].toIntOrNull() ?: 15,
                withSound = parts[4] != "0"
            )
        }.toList()
    }
}
