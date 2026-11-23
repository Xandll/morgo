package io.qzz.xandll.morgo.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LightIntervalUnit {
    SECONDS,
    MINUTES
}

/**
 * Licht vor dem Wecker. Lampen, Schalter und Helfer werden eingeschaltet und
 * danach umgeschaltet. Skripte und Szenen bekommen bei jedem Schritt einen Aufruf.
 */
data class PreWakeLightSettings(
    val enabled: Boolean = false,
    val entityIds: List<String> = emptyList(),
    val leadMinutes: Int = 10,
    val toggleCount: Int = 3,
    /**
     * Abstand in Millisekunden. Negativ heißt: noch der alte Wert aus
     * [intervalAmount] und [intervalUnit], bis er migriert ist.
     */
    val intervalMillis: Long = -1,
    val intervalAmount: Int = 15,
    val intervalUnit: LightIntervalUnit = LightIntervalUnit.SECONDS,
    val brightness: Int? = null,
    val leaveOffEnabled: Boolean = false,
    val leaveOffOffsetMinutes: Int = 0,
    val leaveOff: Map<String, LeaveOffChoice> = emptyMap()
) {
    fun isActive(): Boolean = enabled && entityIds.isNotEmpty()

    /** Wirklicher Abstand. Alte Sekunden- und Minutenwerte werden dabei umgerechnet. */
    fun delayMillis(): Long {
        if (intervalMillis >= 0) return intervalMillis.coerceIn(0L, PreWakeLightPlan.MAX_INTERVAL_MILLIS)
        return PreWakeLightPlan.migrateMillis(intervalAmount, intervalUnit)
    }

    fun summaryLine(): String {
        if (!isActive()) return "aus"
        return "$leadMinutes min vor dem Wecker"
    }
}

data class LightStep(
    val wait: Duration,
    val turnOn: Boolean
)

object PreWakeLightPlan {

    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    const val MAX_INTERVAL_MILLIS = 30L * 60L * 1000L
    private const val MAX_SECONDS = 300L
    private const val MAX_MINUTES = 30

    /**
     * Gespeicherte Sekunden oder Minuten in Millisekunden.
     * Der alte Bereich bleibt: 1–300 Sekunden oder 1–30 Minuten.
     */
    fun migrateMillis(amount: Int, unit: LightIntervalUnit): Long {
        return when (unit) {
            LightIntervalUnit.MINUTES -> amount.coerceIn(1, MAX_MINUTES) * 60_000L
            LightIntervalUnit.SECONDS -> amount.coerceIn(1, MAX_SECONDS.toInt()) * 1_000L
        }
    }

    /** Anzeige auf Deutsch. Unter einer Sekunde eine Nachkommastelle, 0 bleibt „0 s“. */
    fun formatInterval(millis: Long, unit: LightIntervalUnit): String {
        val ms = millis.coerceIn(0, MAX_INTERVAL_MILLIS)
        if (unit == LightIntervalUnit.MINUTES) {
            return "${ms / 60_000L} min"
        }
        if (ms == 0L) return "0 s"
        if (ms < 1_000L || ms % 1_000L != 0L) {
            val text = String.format(Locale.GERMAN, "%.1f", ms / 1000.0)
            return "$text s"
        }
        return "${ms / 1_000L} s"
    }

    /** Sekunden-Schritt: unter einer Sekunde in 100-ms-Schritten, 0 ist erlaubt. */
    fun stepSeconds(millis: Long, delta: Int): Long {
        val ms = millis.coerceIn(0, MAX_SECONDS * 1_000L)
        val next = when {
            delta < 0 && ms in 1..1_000 -> ms - 100
            delta > 0 && ms < 1_000 -> ms + 100
            else -> ms + delta * 1_000L
        }
        return next.coerceIn(0, MAX_SECONDS * 1_000L)
    }

    fun stepMinutes(millis: Long, delta: Int): Long {
        val minutes = (millis / 60_000L).toInt().coerceIn(1, MAX_MINUTES)
        return (minutes + delta).coerceIn(1, MAX_MINUTES) * 60_000L
    }

    /**
     * Wechsel der Einheit wie bisher über die angezeigte Zahl:
     * 20 Sekunden werden 20 Minuten, 20 Minuten werden 20 Sekunden.
     */
    fun switchUnit(millis: Long, from: LightIntervalUnit, to: LightIntervalUnit): Long {
        if (from == to) return millis.coerceIn(0, MAX_INTERVAL_MILLIS)
        val count = when (from) {
            LightIntervalUnit.MINUTES -> (millis / 60_000L).toInt()
            LightIntervalUnit.SECONDS -> if (millis < 1_000L) 0 else (millis / 1_000L).toInt()
        }
        return when (to) {
            LightIntervalUnit.MINUTES -> count.coerceIn(1, MAX_MINUTES) * 60_000L
            LightIntervalUnit.SECONDS -> count.coerceIn(1, MAX_SECONDS.toInt()) * 1_000L
        }
    }

    fun shouldArm(rings: Boolean, light: PreWakeLightSettings): Boolean {
        return rings && light.isActive()
    }

    /** Uhrzeit, zu der das Licht angeht. Mitternacht wird auf den Vortag gelegt. */
    fun start(wake: LocalDateTime, leadMinutes: Int): LocalDateTime {
        return wake.minusMinutes(leadMinutes.coerceAtLeast(0).toLong())
    }

    /**
     * Zeitpunkt für den exakten Alarm.
     * Liegt der Vorlauf schon hinter uns, der Wecker aber noch vor uns, startet das Licht in wenigen Sekunden.
     * Ist der Wecker vorbei, gibt es kein Licht.
     */
    fun triggerAt(now: LocalDateTime, wake: LocalDateTime, leadMinutes: Int): LocalDateTime? {
        if (!wake.isAfter(now)) return null
        val start = start(wake, leadMinutes)
        return if (start.isAfter(now)) start else now.plusSeconds(5)
    }

    fun hint(wake: LocalTime, leadMinutes: Int): String {
        val at = start(LocalDate.of(2024, 1, 2).atTime(wake), leadMinutes).toLocalTime()
        return "Licht geht um ${at.format(clock)} an"
    }

    /** Testabstand: höchstens eine Sekunde, kürzere echte Abstände bleiben. */
    fun testDelay(wait: Duration): Duration {
        if (wait.isZero || wait.isNegative) return Duration.ZERO
        val cap = Duration.ofSeconds(1)
        return if (wait <= cap) wait else cap
    }

    fun progress(index: Int, total: Int): String = "Schritt ${index + 1}/$total"

    fun intervalSeconds(amount: Int, unit: LightIntervalUnit): Long {
        return when (unit) {
            LightIntervalUnit.SECONDS -> amount.coerceIn(1, 300).toLong()
            LightIntervalUnit.MINUTES -> amount.coerceIn(1, 30).toLong() * 60
        }
    }

    /** Erst einschalten, danach [toggleCount] Umschaltungen im festen Abstand. */
    fun steps(toggleCount: Int, intervalSeconds: Long): List<LightStep> {
        return stepsFor(toggleCount, intervalSeconds.coerceAtLeast(1) * 1_000L)
    }

    /** Wie [steps], der Abstand ist Millisekunden. 0 lässt die Schritte direkt nacheinander laufen. */
    fun stepsFor(toggleCount: Int, intervalMillis: Long): List<LightStep> {
        val count = toggleCount.coerceIn(0, 10)
        val gap = Duration.ofMillis(intervalMillis.coerceIn(0, MAX_INTERVAL_MILLIS))
        return buildList {
            add(LightStep(Duration.ZERO, turnOn = true))
            repeat(count) { add(LightStep(gap, turnOn = false)) }
        }
    }
}
