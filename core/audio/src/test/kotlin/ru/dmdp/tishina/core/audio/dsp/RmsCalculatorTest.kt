package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@DisplayName("RmsCalculator — sliding-window root-mean-square over a primitive RingBuffer")
class RmsCalculatorTest {

    @ParameterizedTest(name = "sine amplitude={0} → RMS≈{0}/√2")
    @CsvSource(
        "0.25",
        "0.5",
        "1.0",
    )
    fun `sine wave RMS matches amplitude divided by sqrt 2 within tight tolerance`(amplitude: Float) {
        // 1 kHz sine at 48 kHz, window = 6000 samples (125 ms Fast). 1 kHz × 125 ms = 125 full
        // cycles per window — any complete window represents a steady-state RMS exactly.
        val sampleRateHz = 48_000
        val windowSize = sampleRateHz / 8 // 125 ms
        val calc = RmsCalculator(windowSize = windowSize)

        var lastRms = 0f
        for (n in 0 until windowSize) {
            val sample = amplitude * sin(2.0 * PI * 1_000.0 * n / sampleRateHz).toFloat()
            lastRms = calc.update(sample)
        }

        val expected = amplitude / sqrt(2f)
        assertEquals(expected, lastRms, 0.001f, "Expected $expected, got $lastRms")
    }

    @Test
    fun `constant zero signal yields RMS equal to zero`() {
        val calc = RmsCalculator(windowSize = 1024)
        var lastRms = -1f // negative sentinel to detect "never assigned"
        repeat(2048) { lastRms = calc.update(0f) }
        assertEquals(0f, lastRms, 0f)
    }

    @Test
    fun `RMS before any sample is zero (defends against sqrt of zero division)`() {
        val calc = RmsCalculator(windowSize = 1024)
        // No update() calls — query state via a single update with 0 to inspect the first reading.
        val first = calc.update(0f)
        assertEquals(0f, first, 0f)
    }

    @Test
    fun `constant DC level reaches that level as RMS once window is full`() {
        val calc = RmsCalculator(windowSize = 100)
        var lastRms = 0f
        repeat(100) { lastRms = calc.update(0.5f) }
        // RMS of a constant 0.5 signal is 0.5.
        assertEquals(0.5f, lastRms, 1e-4f)
    }

    @Test
    fun `non-positive window size is rejected by the constructor`() {
        assertThrows(IllegalArgumentException::class.java) { RmsCalculator(windowSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { RmsCalculator(windowSize = -8) }
    }

    @Test
    fun `reset clears history so two identical sequences produce identical final RMS`() {
        val calc = RmsCalculator(windowSize = 512)
        val seq = FloatArray(512) { i -> sin(2.0 * PI * 1_000.0 * i / 48_000.0).toFloat() }

        var rmsA = 0f
        for (v in seq) rmsA = calc.update(v)

        calc.reset()
        var rmsB = 0f
        for (v in seq) rmsB = calc.update(v)

        assertEquals(rmsA, rmsB, 0f)
    }

    @Test
    fun `older samples leave the window as it slides forward`() {
        // First fill the window with 1.0, then push enough 0.0 samples to evict everything.
        val calc = RmsCalculator(windowSize = 64)
        repeat(64) { calc.update(1f) }
        var lastRms = 0f
        repeat(64) { lastRms = calc.update(0f) }
        assertEquals(0f, lastRms, 1e-6f)
    }
}
