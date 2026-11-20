package io.qzz.xandll.morgo.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * App-Settings (Einstellungen)
 */
data class AppSettings(
    val webUntis: WebUntisSettings = WebUntisSettings(),
    val train: TrainSettings = TrainSettings(),
    val homeAssistant: HomeAssistantSettings = HomeAssistantSettings(),
    val alarm: AlarmSettings = AlarmSettings(),
    val useDynamicColor: Boolean = false,
    val onboardingComplete: Boolean = false,
    val update: UpdateSettings = UpdateSettings()
)

/** Öffentliches GitHub-Repo für Releases. Kein Token. */
data class UpdateSettings(
    val enabled: Boolean = true,
    val owner: String = "Xandll",
    val repo: String = "morgo"
)

data class WebUntisSettings(
    val server: String = "",
    val schoolName: String = "",
    val username: String = "",
    val password: String = "",
    val useSecret: Boolean = false, // QR-Code/Secret-Login statt Passwort
    val secret: String = "" // TOTP-Secret aus QR-Code
)

data class TrainSettings(
    val startStation: Station? = null,
    val destinationStation: Station? = null,
    val walkingTimeMinutes: Int = 10,
    val preparationTimeMinutes: Int = 30
)

data class HomeAssistantSettings(
    val url: String = "",
    val token: String = "",
    val wakeUpActions: List<String> = emptyList(), // Entity IDs die beim Wecken aktiviert werden
    val preWake: PreWakeLightSettings = PreWakeLightSettings()
)

data class AlarmSettings(
    val enabled: Boolean = true,
    val defaultWakeTime: LocalTime = LocalTime.of(7, 0),
    val snoozeMinutes: Int = 5,
    val wakeTimeRules: List<WakeTimeRule> = emptyList(),
    val planBEnabled: Boolean = true,
    val maxEarlierMinutes: Int = 30, // Maximal X Min früher wecken bei Plan B
    val earliestWakeTime: LocalTime = LocalTime.of(5, 0), // Nie vor dieser Uhrzeit
    val eveningCheckEnabled: Boolean = true,
    val eveningCheckTime: LocalTime = LocalTime.of(20, 0),
    val followUpEnabled: Boolean = false,
    val followUpMinutes: Int = 5,
    val followUpEvenIfAwake: Boolean = false,
    val wakeSound: WakeSound = WakeSound.RINGTONE,
    val music: MusicAlarmSettings = MusicAlarmSettings(),
    val extraAlarms: List<ExtraAlarm> = emptyList(),
    val dayOverride: DayOverride? = null
) {
    fun overrideFor(date: LocalDate): DayOverride? {
        val override = dayOverride ?: return null
        return if (override.date == date) override else null
    }
}

enum class DayOverrideMode {
    OFF,
    CUSTOM,
    FORCE_ON
}

/** Einmalige Abweichung, gilt nur für [date] und wird danach verworfen. */
data class DayOverride(
    val date: LocalDate,
    val mode: DayOverrideMode,
    val wakeTime: LocalTime? = null
)

/**
 * Weckzeit für eine Unterrichtsstunde.
 * [lessonNumber] ist der 1-basierte Timegrid-Index (1. Stunde, 2. Stunde, …).
 * [wakeTime] null heißt: Standard-Vorlauf (Vorbereitung vor dem Zug, sonst vor der Stunde).
 * [legacyStartTime] trägt alte uhrzeitbasierte Regeln, bis ein Timegrid die Nummer liefert.
 */
data class WakeTimeRule(
    val lessonNumber: Int,
    val wakeTime: LocalTime? = null,
    val preferredTrainDeparture: LocalTime? = null,
    val preferredTrainLine: String? = null,
    val legacyStartTime: LocalTime? = null
)

/**
 * Station
 */
data class Station(
    val id: String,
    val name: String,
    val extId: String? = null
)

/**
 * Unterrichtsstunde
 */
data class Lesson(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val teacher: String,
    val room: String,
    val status: LessonStatus
)

enum class LessonStatus {
    REGULAR,
    CANCELLED,
    SUBSTITUTED
}

/**
 * Zugverbindung
 */
data class TrainConnection(
    val departure: ConnectionPoint,
    val arrival: ConnectionPoint,
    val duration: Int, // Minuten
    val changes: Int,
    val status: ConnectionStatus,
    val products: List<TrainProduct> = emptyList(), // Zugtypen/Linien
    val remarks: List<String> = emptyList() // Hinweise/Störungen
)

/**
 * Suchergebnis für Zugverbindungen
 */
data class ConnectionSearchResult(
    val connections: List<TrainConnection>,
    val hasData: Boolean = connections.isNotEmpty()
)

/**
 * Zugprodukt (Zugtyp und Linie)
 */
data class TrainProduct(
    val name: String, // z.B. "REX 5"
    val category: String, // z.B. "REX", "S", "RJ"
    val number: String? = null
)

data class ConnectionPoint(
    val station: String,
    val plannedTime: LocalTime,
    val actualTime: LocalTime?,
    val platform: String?
)

enum class ConnectionStatus {
    ON_TIME,
    DELAYED,
    CANCELLED
}

/**
 * Weck-Plan
 */
data class AlarmPlan(
    val alarmTime: LocalTime,
    val reason: String,
    val firstLesson: Lesson?,
    val trainConnection: TrainConnection?,
    val rings: Boolean = true,
    val usedCache: Boolean = false,
    val uncertain: Boolean = false,
    val lessonNumber: Int? = null,
    val daySubjects: List<String> = emptyList()
)

/**
 * Home Assistant Entity
 */
data class HomeAssistantEntity(
    val entityId: String,
    val name: String,
    val state: String,
    val type: EntityType
)

enum class EntityType {
    LIGHT,
    SWITCH,
    SCENE,
    SCRIPT,
    UNKNOWN
}
