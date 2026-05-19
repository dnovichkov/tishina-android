package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MeasurementSnapshot data class")
class MeasurementSnapshotTest {

    @Test
    fun `default empty snapshot has neutral metrics`() {
        val empty = MeasurementSnapshot.empty
        assertEquals(0.0f, empty.currentDb)
        assertEquals(0L, empty.durationMs)
        assertTrue(empty.recent.isEmpty())
        assertTrue(empty.minDb.isInfinite() && empty.minDb > 0)
        assertTrue(empty.maxDb.isInfinite() && empty.maxDb < 0)
        assertEquals(0.0f, empty.avgDb)
    }

    @Test
    fun `data class equality compares all fields including recent list contents`() {
        val list = listOf(SoundSample(60f, 0L), SoundSample(61f, 100L))
        val a = MeasurementSnapshot(
            currentDb = 61f,
            minDb = 60f,
            maxDb = 61f,
            avgDb = 60.5f,
            durationMs = 100L,
            recent = list,
        )
        val b = MeasurementSnapshot(
            currentDb = 61f,
            minDb = 60f,
            maxDb = 61f,
            avgDb = 60.5f,
            durationMs = 100L,
            recent = listOf(SoundSample(60f, 0L), SoundSample(61f, 100L)),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `copy lets you update a single metric`() {
        val original = MeasurementSnapshot(
            currentDb = 50f,
            minDb = 40f,
            maxDb = 80f,
            avgDb = 60f,
            durationMs = 1_000L,
            recent = emptyList(),
        )
        val updated = original.copy(currentDb = 55f)
        assertEquals(55f, updated.currentDb)
        assertEquals(40f, updated.minDb)
        assertNotEquals(original, updated)
    }

    @Test
    fun `recent list is preserved verbatim`() {
        val samples = (0 until 600).map { SoundSample(40f + (it % 50), it.toLong() * 100) }
        val snapshot = MeasurementSnapshot(
            currentDb = 60f,
            minDb = 40f,
            maxDb = 89f,
            avgDb = 65f,
            durationMs = 60_000L,
            recent = samples,
        )
        assertEquals(600, snapshot.recent.size)
        assertEquals(samples.first(), snapshot.recent.first())
        assertEquals(samples.last(), snapshot.recent.last())
    }

    @Test
    fun `tolerates NaN metrics without throwing`() {
        val snapshot = MeasurementSnapshot(
            currentDb = Float.NaN,
            minDb = Float.NaN,
            maxDb = Float.NaN,
            avgDb = Float.NaN,
            durationMs = 0L,
            recent = emptyList(),
        )
        assertTrue(snapshot.currentDb.isNaN())
    }
}
