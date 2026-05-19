package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SoundSample data class")
class SoundSampleTest {

    @Test
    fun `holds db and timestamp values`() {
        val sample = SoundSample(db = 65.5f, timestampMs = 1_234L)
        assertEquals(65.5f, sample.db)
        assertEquals(1_234L, sample.timestampMs)
    }

    @Test
    fun `equal samples are equal by value`() {
        val a = SoundSample(db = 42.0f, timestampMs = 1_000L)
        val b = SoundSample(db = 42.0f, timestampMs = 1_000L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `samples differing in db are not equal`() {
        val a = SoundSample(db = 42.0f, timestampMs = 1_000L)
        val b = SoundSample(db = 43.0f, timestampMs = 1_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `samples differing in timestamp are not equal`() {
        val a = SoundSample(db = 42.0f, timestampMs = 1_000L)
        val b = SoundSample(db = 42.0f, timestampMs = 2_000L)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy creates new instance with overridden field`() {
        val original = SoundSample(db = 30.0f, timestampMs = 500L)
        val copied = original.copy(db = 90.0f)
        assertEquals(90.0f, copied.db)
        assertEquals(500L, copied.timestampMs)
        assertNotEquals(original, copied)
    }

    @Test
    fun `tolerates negative infinity dB (silence floor)`() {
        val sample = SoundSample(db = Float.NEGATIVE_INFINITY, timestampMs = 0L)
        assertTrue(sample.db.isInfinite())
    }

    @Test
    fun `tolerates NaN db without throwing`() {
        val sample = SoundSample(db = Float.NaN, timestampMs = 0L)
        assertTrue(sample.db.isNaN())
    }

    @Test
    fun `tolerates zero timestamp at measurement start`() {
        val sample = SoundSample(db = 60.0f, timestampMs = 0L)
        assertEquals(0L, sample.timestampMs)
    }
}
