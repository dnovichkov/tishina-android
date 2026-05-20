package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MeasurementDetails data class")
class MeasurementDetailsTest {

    private val baseSummary = MeasurementSummary(
        id = 7L,
        createdAtEpochMs = 1_700_000_000_000L,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = "Кухня",
        note = null,
        sparklinePreview = listOf(40f, 50f),
    )

    private fun details(
        summary: MeasurementSummary = baseSummary,
        samples: List<SoundSample> = listOf(SoundSample(40f, 0L), SoundSample(60f, 200L)),
        weighting: FrequencyWeighting = FrequencyWeighting.A,
        timeWeighting: TimeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb: Float = 0f,
        sampleRateHz: Int = 44_100,
    ) = MeasurementDetails(
        summary = summary,
        samples = samples,
        weighting = weighting,
        timeWeighting = timeWeighting,
        calibrationOffsetDb = calibrationOffsetDb,
        sampleRateHz = sampleRateHz,
    )

    @Test
    fun `composes a summary plus the sample stream and configuration snapshot`() {
        val d = details()
        assertEquals(baseSummary, d.summary)
        assertEquals(2, d.samples.size)
        assertEquals(FrequencyWeighting.A, d.weighting)
        assertEquals(TimeWeighting.FAST, d.timeWeighting)
        assertEquals(0f, d.calibrationOffsetDb)
        assertEquals(44_100, d.sampleRateHz)
    }

    @Test
    fun `value equality covers nested summary`() {
        val a = details()
        val b = details()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing samples list breaks equality`() {
        val a = details()
        val b = details(samples = emptyList())
        assertNotEquals(a, b)
    }

    @Test
    fun `differing weighting breaks equality`() {
        val a = details(weighting = FrequencyWeighting.A)
        val b = details(weighting = FrequencyWeighting.Z)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy can mutate calibration offset without changing samples`() {
        val original = details()
        val updated = original.copy(calibrationOffsetDb = -3.5f)
        assertEquals(-3.5f, updated.calibrationOffsetDb)
        assertEquals(original.samples, updated.samples)
    }

    @Test
    fun `empty samples list is valid for record-keeping use cases`() {
        val d = details(samples = emptyList())
        assertTrue(d.samples.isEmpty())
    }
}
