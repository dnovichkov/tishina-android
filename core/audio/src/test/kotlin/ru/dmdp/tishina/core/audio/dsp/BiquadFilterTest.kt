package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@DisplayName("BiquadFilter — Direct Form II Transposed second-order IIR section")
class BiquadFilterTest {

    @Test
    fun `unity coefficients pass the input through unchanged`() {
        val filter = BiquadFilter(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)
        val input = floatArrayOf(0.1f, -0.5f, 0.25f, 0.75f, -0.9f)
        val expected = input.copyOf()

        filter.process(input)

        assertArrayEquals(expected, input, 1e-7f)
    }

    @Test
    fun `processing with explicit destination keeps source untouched`() {
        val filter = BiquadFilter(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)
        val srcSnapshot = input.copyOf()
        val output = FloatArray(3)

        filter.process(input, into = output)

        assertArrayEquals(srcSnapshot, input, 1e-7f)
        assertArrayEquals(srcSnapshot, output, 1e-7f)
    }

    @Test
    fun `mismatched destination size is rejected`() {
        val filter = BiquadFilter(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)
        val input = FloatArray(8)
        val output = FloatArray(7)
        assertThrows(IllegalArgumentException::class.java) {
            filter.process(input, into = output)
        }
    }

    @Test
    fun `dc blocking biquad attenuates a constant signal toward zero`() {
        // Bilinear-transform of a single-pole HPF at fc=100 Hz, fs=48 kHz, dressed as a biquad
        // (b2 = a2 = 0 so the second pole/zero is absent — effectively first-order).
        val fs = 48_000.0
        val fc = 100.0
        val k = kotlin.math.tan(PI * fc / fs)
        val norm = 1.0 / (1.0 + k)
        val b0 = norm
        val b1 = -norm
        val a1 = (k - 1.0) * norm
        val filter = BiquadFilter(
            b0 = b0.toFloat(),
            b1 = b1.toFloat(),
            b2 = 0f,
            a1 = a1.toFloat(),
            a2 = 0f,
        )
        val samples = FloatArray(4_000) { 1f }

        filter.process(samples)

        // After enough samples the DC component is below 1 % of the input.
        for (i in 3_500 until samples.size) {
            assertTrue(
                kotlin.math.abs(samples[i]) < 0.01f,
                "Expected |y[$i]| < 0.01 once DC settles, got ${samples[i]}",
            )
        }
    }

    @Test
    fun `dc blocking biquad preserves 1 kHz sine RMS within tolerance`() {
        val fs = 48_000.0
        val fc = 100.0
        val k = kotlin.math.tan(PI * fc / fs)
        val norm = 1.0 / (1.0 + k)
        val b0 = norm.toFloat()
        val b1 = (-norm).toFloat()
        val a1 = ((k - 1.0) * norm).toFloat()
        val filter = BiquadFilter(b0 = b0, b1 = b1, b2 = 0f, a1 = a1, a2 = 0f)

        val durationSamples = 48_000
        val input = FloatArray(durationSamples) { n ->
            sin(2.0 * PI * 1_000.0 * n / fs).toFloat()
        }
        val expectedRms = rms(input)

        val output = input.copyOf()
        filter.process(output)

        // Discard the first 100 ms transient.
        val tail = output.sliceArray(4_800 until output.size)
        val deviation = kotlin.math.abs(rms(tail) - expectedRms) / expectedRms
        assertTrue(
            deviation < 0.02f,
            "1 kHz RMS preserved within 2 %% by 100 Hz HPF, got deviation=${deviation * 100}%",
        )
    }

    @Test
    fun `reset clears internal delay line so repeated runs produce identical output`() {
        val fs = 48_000.0
        val fc = 100.0
        val k = kotlin.math.tan(PI * fc / fs)
        val norm = 1.0 / (1.0 + k)
        val filter = BiquadFilter(
            b0 = norm.toFloat(),
            b1 = (-norm).toFloat(),
            b2 = 0f,
            a1 = ((k - 1.0) * norm).toFloat(),
            a2 = 0f,
        )
        val input = FloatArray(256) { i -> sin(2.0 * PI * 1_000.0 * i / fs).toFloat() }

        val firstRun = input.copyOf()
        filter.process(firstRun)

        filter.reset()

        val secondRun = input.copyOf()
        filter.process(secondRun)

        assertArrayEquals(firstRun, secondRun, 0f)
    }

    @Test
    fun `unity biquad with a delay coefficient introduces no instability over long runs`() {
        // b0 + b1·z⁻¹ - z⁻² style identity: y[n] = x[n]; just check no NaN/Inf creeps in over 10 s.
        val filter = BiquadFilter(b0 = 1f, b1 = 0f, b2 = 0f, a1 = 0f, a2 = 0f)
        val n = 48_000 * 10
        val out = FloatArray(n) { (it % 1024).toFloat() / 1024f }
        filter.process(out)
        for (v in out) {
            assertTrue(v.isFinite(), "Filter output must stay finite over long runs")
        }
        assertEquals(0f, out[0], 1e-6f)
    }

    private fun rms(values: FloatArray): Float {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v.toDouble()
        return sqrt(sum / values.size).toFloat()
    }
}
