package io.qzz.xandll.morgo.domain.model

/**
 * Sichtbare Zeilen der Weckzeit-Tabelle.
 * Zuerst zwei Stunden. Sind alle sichtbaren Zeilen mit einer Weckzeit gefüllt,
 * kommt genau eine leere Zeile dazu, höchstens bis zur letzten Stunde.
 * Gespeicherte Zeilen bleiben sichtbar, auch wenn davor Lücken sind.
 */
object WakeTable {
    fun visibleNumbers(rules: List<WakeTimeRule>, periodCount: Int): List<Int> {
        val max = periodCount.coerceAtLeast(1)
        val inRange = rules.filter { it.lessonNumber in 1..max }
        val saved = inRange.filter { it.isSaved() }.map { it.lessonNumber }
        val filled = inRange.filter { it.wakeTime != null }.map { it.lessonNumber }.toSet()
        val base = maxOf(2, saved.maxOrNull() ?: 0).coerceAtMost(max)
        val count = if (base < max && (1..base).all { it in filled }) base + 1 else base
        return (1..count).toList()
    }

    private fun WakeTimeRule.isSaved(): Boolean {
        return wakeTime != null ||
            preferredTrainDeparture != null ||
            !preferredTrainLine.isNullOrBlank()
    }
}
