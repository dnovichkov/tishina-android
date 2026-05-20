package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("NewMeasurement DTO + companion constants")
class NewMeasurementTest {

    private fun newMeasurement(
        title: String? = "Спальня",
        note: String? = "Поздний вечер",
        samples: List<SoundSample> = listOf(SoundSample(40f, 0L)),
    ) = NewMeasurement(
        createdAtEpochMs = 1_700_000_000_000L,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 44_100,
        samples = samples,
    )

    @Test
    fun `MAX_TITLE_LENGTH is 80 (FR-6 NFR-12)`() {
        assertEquals(80, NewMeasurement.MAX_TITLE_LENGTH)
    }

    @Test
    fun `MAX_NOTE_LENGTH is 200 (FR-6 NFR-12)`() {
        assertEquals(200, NewMeasurement.MAX_NOTE_LENGTH)
    }

    @Test
    fun `holds all the values supplied to the constructor`() {
        val m = newMeasurement()
        assertEquals(1_700_000_000_000L, m.createdAtEpochMs)
        assertEquals(30_000L, m.durationMs)
        assertEquals(45f, m.avgDb)
        assertEquals(30f, m.minDb)
        assertEquals(60f, m.maxDb)
        assertEquals("Спальня", m.title)
        assertEquals("Поздний вечер", m.note)
        assertEquals(FrequencyWeighting.A, m.weighting)
        assertEquals(TimeWeighting.FAST, m.timeWeighting)
        assertEquals(0f, m.calibrationOffsetDb)
        assertEquals(44_100, m.sampleRateHz)
        assertEquals(1, m.samples.size)
    }

    @Test
    fun `null title is preserved`() {
        val m = newMeasurement(title = null)
        assertNull(m.title)
    }

    @Test
    fun `empty title is distinct from null`() {
        val withNull = newMeasurement(title = null)
        val withEmpty = newMeasurement(title = "")
        assertNotEquals(withNull, withEmpty)
        assertEquals("", withEmpty.title)
    }

    @Test
    fun `80-character title is at the boundary (valid)`() {
        val title = "a".repeat(NewMeasurement.MAX_TITLE_LENGTH)
        val m = newMeasurement(title = title)
        assertEquals(80, m.title?.length)
    }

    @Test
    fun `81-character title is over the boundary (model accepts it - validation lives in use-case)`() {
        val title = "a".repeat(NewMeasurement.MAX_TITLE_LENGTH + 1)
        val m = newMeasurement(title = title)
        // Model is a dumb data carrier; SaveMeasurementUseCase enforces the rule.
        assertEquals(81, m.title?.length)
    }

    @Test
    fun `200-character note is at the boundary`() {
        val note = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH)
        val m = newMeasurement(note = note)
        assertEquals(200, m.note?.length)
    }

    @Test
    fun `value equality compares every field including samples list`() {
        val a = newMeasurement()
        val b = newMeasurement()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing samples list breaks equality`() {
        val a = newMeasurement(samples = listOf(SoundSample(40f, 0L)))
        val b = newMeasurement(samples = listOf(SoundSample(50f, 0L)))
        assertNotEquals(a, b)
    }

    @Test
    fun `copy lets you override the title only`() {
        val original = newMeasurement()
        val updated = original.copy(title = "Новый")
        assertEquals("Новый", updated.title)
        assertEquals(original.note, updated.note)
        assertEquals(original.samples, updated.samples)
    }
}
