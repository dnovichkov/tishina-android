package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MeasurementSummary data class")
class MeasurementSummaryTest {

    private fun sample(
        id: Long = 1L,
        title: String? = "Спальня",
        note: String? = "Поздний вечер",
        sparkline: List<Float> = listOf(40f, 42f, 41f),
    ) = MeasurementSummary(
        id = id,
        createdAtEpochMs = 1_700_000_000_000L,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = title,
        note = note,
        sparklinePreview = sparkline,
    )

    @Test
    fun `holds all the values supplied to the constructor`() {
        val summary = sample()
        assertEquals(1L, summary.id)
        assertEquals(1_700_000_000_000L, summary.createdAtEpochMs)
        assertEquals(30_000L, summary.durationMs)
        assertEquals(45f, summary.avgDb)
        assertEquals(30f, summary.minDb)
        assertEquals(60f, summary.maxDb)
        assertEquals("Спальня", summary.title)
        assertEquals("Поздний вечер", summary.note)
        assertEquals(listOf(40f, 42f, 41f), summary.sparklinePreview)
    }

    @Test
    fun `value equality compares all fields`() {
        val a = sample()
        val b = sample()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing id breaks equality`() {
        assertNotEquals(sample(id = 1L), sample(id = 2L))
    }

    @Test
    fun `copy overrides only the named field`() {
        val original = sample()
        val updated = original.copy(note = "обновлено")
        assertEquals("обновлено", updated.note)
        assertEquals(original.title, updated.title)
        assertEquals(original.sparklinePreview, updated.sparklinePreview)
    }

    @Test
    fun `null title is preserved verbatim`() {
        val summary = sample(title = null)
        assertNull(summary.title)
    }

    @Test
    fun `empty note string is distinct from null`() {
        val withEmpty = sample(note = "")
        val withNull = sample(note = null)
        assertEquals("", withEmpty.note)
        assertNull(withNull.note)
        assertNotEquals(withEmpty, withNull)
    }

    @Test
    fun `empty sparkline list is a valid value`() {
        val summary = sample(sparkline = emptyList())
        assertTrue(summary.sparklinePreview.isEmpty())
    }
}
