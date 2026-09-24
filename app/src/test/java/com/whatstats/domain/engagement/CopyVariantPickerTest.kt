package com.whatstats.domain.engagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyVariantPickerTest {
    @Test
    fun zeroOrOneVariantAlwaysReturnsZero() {
        assertEquals(0, CopyVariantPicker.pick("peak_hour", 20_000L, 0))
        assertEquals(0, CopyVariantPicker.pick("peak_hour", 20_000L, 1))
    }

    @Test
    fun resultIsAlwaysInRange() {
        (0L until 1_000L).forEach { day ->
            val index = CopyVariantPicker.pick("messages_sent", day, 3)
            assertTrue(index in 0 until 3)
        }
    }

    @Test
    fun sameInputGivesSameVariant() {
        val first = CopyVariantPicker.pick("night_share", 19_999L, 3)
        repeat(10) { assertEquals(first, CopyVariantPicker.pick("night_share", 19_999L, 3)) }
    }

    @Test
    fun variantChangesAcrossDays() {
        val variants = (0L until 30L).map { day -> CopyVariantPicker.pick("record_streak", 20_000L + day, 3) }.toSet()
        assertTrue(variants.size > 1)
    }
}
