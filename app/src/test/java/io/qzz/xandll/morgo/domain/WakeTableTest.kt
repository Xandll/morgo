package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.WakeTable
import io.qzz.xandll.morgo.domain.model.WakeTimeRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class WakeTableTest {

    @Test
    fun `starts with two rows`() {
        assertEquals(listOf(1, 2), WakeTable.visibleNumbers(emptyList(), 10))
    }

    @Test
    fun `one filled row keeps the second empty`() {
        val rules = listOf(WakeTimeRule(1, LocalTime.of(6, 20)))
        assertEquals(listOf(1, 2), WakeTable.visibleNumbers(rules, 10))
    }

    @Test
    fun `filled visible rows reveal exactly one more`() {
        val rules = listOf(
            WakeTimeRule(1, LocalTime.of(6, 20)),
            WakeTimeRule(2, LocalTime.of(7, 0))
        )
        assertEquals(listOf(1, 2, 3), WakeTable.visibleNumbers(rules, 10))
    }

    @Test
    fun `saved later rows stay visible`() {
        val rules = listOf(WakeTimeRule(4, LocalTime.of(8, 0), preferredTrainLine = "REX"))
        assertEquals(listOf(1, 2, 3, 4), WakeTable.visibleNumbers(rules, 8))
    }

    @Test
    fun `does not grow past the timegrid`() {
        val rules = (1..4).map { WakeTimeRule(it, LocalTime.of(6, it)) }
        assertEquals(listOf(1, 2, 3, 4), WakeTable.visibleNumbers(rules, 4))
    }

    @Test
    fun `train without wake time does not count as filled`() {
        val rules = listOf(
            WakeTimeRule(1, LocalTime.of(6, 0)),
            WakeTimeRule(2, preferredTrainDeparture = LocalTime.of(7, 10))
        )
        assertEquals(listOf(1, 2), WakeTable.visibleNumbers(rules, 10))
    }
}
