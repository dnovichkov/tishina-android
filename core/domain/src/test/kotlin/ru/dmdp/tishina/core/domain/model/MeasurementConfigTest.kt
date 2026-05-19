package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MeasurementConfig defaults and copy behavior")
class MeasurementConfigTest {

    @Test
    fun `default config matches FR-15 (A-weighting) and FR-16 (Fast)`() {
        val config = MeasurementConfig()
        assertEquals(FrequencyWeighting.A, config.frequencyWeighting)
        assertEquals(TimeWeighting.FAST, config.timeWeighting)
        assertEquals(0.0f, config.calibrationOffsetDb)
    }

    @Test
    fun `copy lets you override frequency weighting only`() {
        val updated = MeasurementConfig().copy(frequencyWeighting = FrequencyWeighting.Z)
        assertEquals(FrequencyWeighting.Z, updated.frequencyWeighting)
        assertEquals(TimeWeighting.FAST, updated.timeWeighting)
    }

    @Test
    fun `copy lets you override time weighting only`() {
        val updated = MeasurementConfig().copy(timeWeighting = TimeWeighting.SLOW)
        assertEquals(FrequencyWeighting.A, updated.frequencyWeighting)
        assertEquals(TimeWeighting.SLOW, updated.timeWeighting)
    }

    @Test
    fun `copy lets you override calibration offset only`() {
        val updated = MeasurementConfig().copy(calibrationOffsetDb = -3.5f)
        assertEquals(-3.5f, updated.calibrationOffsetDb)
        assertEquals(FrequencyWeighting.A, updated.frequencyWeighting)
    }

    @Test
    fun `time weighting exposes time-constant in ms`() {
        assertEquals(125, TimeWeighting.FAST.tauMs)
        assertEquals(1_000, TimeWeighting.SLOW.tauMs)
    }

    @Test
    fun `frequency weighting enum exposes A and Z (C reserved for phase 4)`() {
        val values = FrequencyWeighting.entries
        assertEquals(2, values.size)
        assertEquals(FrequencyWeighting.A, FrequencyWeighting.valueOf("A"))
        assertEquals(FrequencyWeighting.Z, FrequencyWeighting.valueOf("Z"))
    }

    @Test
    fun `differing configs are not equal`() {
        val a = MeasurementConfig()
        val b = MeasurementConfig(frequencyWeighting = FrequencyWeighting.Z)
        assertNotEquals(a, b)
    }
}
