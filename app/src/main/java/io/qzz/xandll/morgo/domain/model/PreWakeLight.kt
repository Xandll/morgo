package io.qzz.xandll.morgo.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    val intervalAmount: Int = 15,
    val intervalUnit: LightIntervalUnit = LightIntervalUnit.SECONDS,
    val brightness: Int? = null,
    val leaveOffEnabled: Boolean = false,
    val leaveOffOffsetMinutes: Int = 0,
    val leaveOff: Map<String, LeaveOffChoice> = emptyMap()
) {
    fun isActive(): Boolean = enabled && entityIds.isNotEmpty()

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
        val count = toggleCount.coerceIn(0, 10)
        val gap = Duration.ofSeconds(intervalSeconds.coerceAtLeast(1))
        return buildList {
            add(LightStep(Duration.ZERO, turnOn = true))
            repeat(count) { add(LightStep(gap, turnOn = false)) }
        }
    }
}
