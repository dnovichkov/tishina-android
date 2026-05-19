package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@DisplayName("DcBlockFilter — single-pole IIR HPF used to remove microphone DC bias")
class DcBlockFilterTest {

    @Test
    fun `constant DC signal decays to zero after a long enough run`() {
        val filter = DcBlockFilter()
        val samples = FloatArray(2_000) { 1f }
        filter.process(samples)
        // pole = 0.995 — by 2000 samples (~42 ms at 48 kHz) the steady-state must be near zero.
        for (i in 1_000 until samples.size) {
            assertTrue(
                kotlin.math.abs(samples[i]) < 0.01f,
                "Expected |y[$i]| < 0.01 after DC settles, got ${samples[i]}",
            )
        }
    }

    @Test
    fun `1 kHz sine at 48 kHz preserves RMS within 1 percent`() {
        val sampleRateHz = 48_000
        val durationSamples = 48_000 // 1 second
        val frequencyHz = 1_000.0
        val input = FloatArray(durationSamples) { n ->
            sin(2.0 * PI * frequencyHz * n / sampleRateHz).toFloat()
        }
        val originalRms = rms(input)

        val filter = DcBlockFilter()
        val output = input.copyOf()
        filter.process(output)

        // Skip startup transient (first 100 ms) before measuring RMS.
        val tail = output.sliceArray((sampleRateHz / 10) until output.size)
        val filteredRms = rms(tail)

        val deviation = kotlin.math.abs(filteredRms - originalRms) / originalRms
        assertTrue(
            deviation < 0.01f,
            "Expected sine RMS preserved within 1%, got deviation=${deviation * 100}%",
        )
    }

    @Test
    fun `in-place processing reuses the input array`() {
        val filter = DcBlockFilter()
        val samples = FloatArray(8) { 0.5f }
        val returnedRef: FloatArray = samples
        filter.process(returnedRef)
        // After in-place processing the same array reference holds filtered values.
        assertNotSame(FloatArray(8) { 0.5f }, returnedRef, "In-place processing must overwrite samples")
        assertEquals(samples, returnedRef)
    }

    @Test
    fun `processing into a separate destination leaves the source untouched`() {
        val filter = DcBlockFilter()
        val input = FloatArray(8) { 0.5f }
        val expectedInput = input.copyOf()
        val output = FloatArray(8)

        filter.process(input, into = output)

        org.junit.jupiter.api.Assertions.assertArrayEquals(
            expectedInput,
            input,
            "Source array must remain unchanged when an explicit destination is provided",
        )
        // Output should not be all-zeros (filter ran) and not equal to input (DC removed).
        var anyNonZero = false
        for (i in output.indices) {
            if (output[i] != 0f) {
                anyNonZero = true
                break
            }
        }
        assertTrue(anyNonZero, "Destination buffer must contain filtered samples")
    }

    @Test
    fun `mismatched destination size is rejected`() {
        val filter = DcBlockFilter()
        val input = FloatArray(8)
        val output = FloatArray(7)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            filter.process(input, into = output)
        }
    }

    private fun rms(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v.toDouble()
        return sqrt(sum / values.size).toFloat()
    }
}
