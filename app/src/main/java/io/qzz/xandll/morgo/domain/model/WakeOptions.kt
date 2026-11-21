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
    val radioBrowserHost: String = "https://de1.api.radio-browser.info"
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

/** Was der gespeicherte Stundenplan zu einem Fach sagt. Kein Netz. */
data class SubjectSchedule(
    val weekdays: String,
    val nextLine: String?,
    val upcoming: List<String>,
    val missing: Boolean
) {
    fun headline(): String? = when {
        missing -> "Keine Stunden gefunden"
        weekdays.isEmpty() -> null
        else -> listOfNotNull(weekdays, nextLine).joinToString(" · ")
    }

    companion object {
        val NONE = SubjectSchedule("", null, emptyList(), missing = false)
        val EMPTY = SubjectSchedule("", null, emptyList(), missing = true)
    }
}

object ExtraAlarmPlan {
    const val HORIZON_DAYS = 14
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

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

    /**
     * Stunden des Fachs ab `today` für die nächsten zwei Wochen.
     * Ausgefallene Stunden zählen nicht. Ein leeres Fach liefert keine Anzeige.
     */
    fun schedule(
        subject: String,
        lessons: List<Lesson>,
        today: LocalDate,
        horizonDays: Int = HORIZON_DAYS
    ): SubjectSchedule {
        if (subject.isBlank()) return SubjectSchedule.NONE
        val until = today.plusDays(horizonDays.coerceAtLeast(1) - 1L)
        val hits = lessons
            .asSequence()
            .filter { !it.date.isBefore(today) && !it.date.isAfter(until) }
            .filter { it.status != LessonStatus.CANCELLED }
            .filter { matches(it.subject, subject) }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .distinctBy { it.date to it.startTime }
            .toList()
        if (hits.isEmpty()) return SubjectSchedule.EMPTY
        val weekdays = hits
            .map { it.date.dayOfWeek }
            .distinct()
            .sortedBy { it.value }
            .joinToString(", ") { LessonGrid.dayLabel(it) }
        val lines = hits.map { formatWhen(it.date, it.startTime) }
        return SubjectSchedule(weekdays, "nächster: ${lines.first()}", lines, missing = false)
    }

    fun formatWhen(date: LocalDate, time: LocalTime): String {
        return "${LessonGrid.dayLabel(date.dayOfWeek)} ${date.format(day)} ${time.format(clock)}"
    }
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

enum class StreamPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}

/** Eine Beobachtung des Stream-Players. Die Lautstärke darf 0 sein, ohne als Ausfall zu gelten. */
data class StreamSample(
    val elapsedSeconds: Int,
    val limitSeconds: Int,
    val online: Boolean,
    val phase: StreamPhase,
    val playWhenReady: Boolean,
    val errorCode: String? = null,
    val errorDetail: String? = null
)

object WakeSoundMath {
    fun volumeAt(elapsedMs: Long, rampMs: Long, target: Float): Float {
        if (rampMs <= 0L) return target.coerceIn(0f, 1f)
        val fraction = (elapsedMs.toFloat() / rampMs.toFloat()).coerceIn(0f, 1f)
        return (target.coerceIn(0f, 1f) * fraction)
    }

    /** ExoPlayer bleibt bei Lautstärke 0 auf manchen Geräten stumm. Ein leiser Boden startet die Spur. */
    fun audibleVolume(ramped: Float): Float {
        val value = ramped.coerceIn(0f, 1f)
        return if (value < 0.02f) 0.02f else value
    }

    /** Bereit und abspielbereit zählt, auch wenn die Rampe noch bei null steht. */
    fun countsAsPlaying(phase: StreamPhase, playWhenReady: Boolean): Boolean {
        return playWhenReady && phase == StreamPhase.READY
    }

    fun fallbackNow(
        needsNetwork: Boolean,
        online: Boolean,
        elapsedSeconds: Int,
        playing: Boolean,
        limitSeconds: Int
    ): Boolean {
        if (playing) return false
        return elapsedSeconds >= limitSeconds.coerceAtLeast(5)
    }

    /**
     * null heißt: weiter warten. Ein Fehler oder fehlendes Netz beendet den Versuch erst
     * nach dem eingestellten Fallback, damit das WLAN nach dem Wecken noch hochfahren kann.
     */
    fun fallbackReason(sample: StreamSample): String? {
        if (countsAsPlaying(sample.phase, sample.playWhenReady)) return null
        val limit = sample.limitSeconds.coerceAtLeast(5)
        if (sample.elapsedSeconds < limit) return null
        val code = sample.errorCode?.trim().orEmpty()
        if (code.isNotEmpty()) return streamFailure(code, sample.errorDetail)
        if (!sample.online) return "Kein Netz nach ${limit}s, Klingelton"
        val state = when (sample.phase) {
            StreamPhase.IDLE -> "nicht gestartet"
            StreamPhase.BUFFERING -> "noch am Puffern"
            StreamPhase.READY -> "bereit, aber ohne Wiedergabe"
            StreamPhase.ENDED -> "Stream zu Ende"
        }
        return "Stream hat nach ${limit}s nicht gespielt ($state), Klingelton"
    }

    fun streamFailure(code: String, detail: String?): String {
        val extra = detail?.trim().orEmpty()
        return if (extra.isEmpty()) "Musik fehlgeschlagen: $code" else "Musik fehlgeschlagen: $code ($extra)"
    }

    fun shouldRetry(elapsedSeconds: Int, limitSeconds: Int, failed: Boolean, lastRetrySecond: Int): Boolean {
        if (!failed) return false
        val limit = limitSeconds.coerceAtLeast(5)
        if (elapsedSeconds >= limit) return false
        return elapsedSeconds - lastRetrySecond >= 3
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
