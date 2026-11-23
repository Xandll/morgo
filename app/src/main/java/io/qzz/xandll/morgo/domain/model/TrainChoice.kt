package io.qzz.xandll.morgo.domain.model

import java.time.LocalTime
import kotlin.math.abs

/**
 * Wahl des Zugs für die Zugkarte und den Live-Check.
 *
 * 1. Bevorzugter Zug der Stunde (Abfahrt, optional Linie), falls er fährt.
 * 2. Sonst der letzte Zug, der rechtzeitig zur Stunde ankommt.
 * Züge vor [earliestDeparture] (Weckzeit plus Vorbereitung) zählen nie.
 */
object TrainChoice {

    /** Toleranz zwischen gespeicherter und gefundener Abfahrt. */
    const val DEPARTURE_TOLERANCE_MINUTES = 2

    fun choose(
        connections: List<TrainConnection>,
        requiredArrival: LocalTime,
        preferredDeparture: LocalTime?,
        preferredLine: String?,
        earliestDeparture: LocalTime?
    ): TrainConnection? {
        val usable = connections
            .filter { it.status != ConnectionStatus.CANCELLED }
            .filter { earliestDeparture == null || !it.departure.plannedTime.isBefore(earliestDeparture) }
        if (usable.isEmpty()) return null

        if (preferredDeparture != null) {
            val near = usable.filter {
                diffMinutes(it.departure.plannedTime, preferredDeparture) <= DEPARTURE_TOLERANCE_MINUTES
            }
            val withLine = near.filter { matchesLine(it, preferredLine) }
            val pick = (withLine.ifEmpty { near }).minByOrNull {
                diffMinutes(it.departure.plannedTime, preferredDeparture)
            }
            if (pick != null) return pick
        }

        val lineOnly = if (preferredDeparture == null && !preferredLine.isNullOrBlank()) {
            usable.filter { matchesLine(it, preferredLine) }
        } else {
            emptyList()
        }
        val pool = lineOnly.ifEmpty { usable }
        return pool
            .filter { !arrivalOf(it).isAfter(requiredArrival) }
            .maxByOrNull { arrivalOf(it) }
    }

    fun matchesLine(connection: TrainConnection, line: String?): Boolean {
        val wanted = normalize(line)
        if (wanted.isEmpty()) return true
        return connection.products.any {
            val name = normalize(it.name)
            val combined = normalize(it.category + (it.number ?: ""))
            name == wanted || combined == wanted || name.contains(wanted) || combined.contains(wanted)
        }
    }

    private fun normalize(text: String?): String =
        text.orEmpty().filter { !it.isWhitespace() }.lowercase()

    private fun arrivalOf(c: TrainConnection): LocalTime = c.arrival.actualTime ?: c.arrival.plannedTime

    private fun diffMinutes(a: LocalTime, b: LocalTime): Int =
        abs(a.toSecondOfDay() - b.toSecondOfDay()) / 60
}
