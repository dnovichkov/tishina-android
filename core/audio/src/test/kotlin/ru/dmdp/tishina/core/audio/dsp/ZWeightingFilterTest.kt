package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ZWeightingFilter — flat (no-op) frequency weighting used as the unfiltered baseline")
class ZWeightingFilterTest {

    @Test
    fun `in-place processing leaves samples bit-for-bit identical`() {
        val filter = ZWeightingFilter()
        val input = floatArrayOf(-1f, -0.5f, 0f, 0.25f, 0.999f, Float.MIN_VALUE)
        val expected = input.copyOf()

        filter.process(input)

        assertArrayEquals(expected, input, 0f)
    }

    @Test
    fun `copy mode copies the input into a separate destination unchanged`() {
        val filter = ZWeightingFilter()
        val input = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)
        val output = FloatArray(input.size)

        filter.process(input, into = output)

        assertNotSame(input, output)
        assertArrayEquals(input, output, 0f)
    }

    @Test
    fun `reset is a no-op even after processing data`() {
        val filter = ZWeightingFilter()
        val samples = FloatArray(64) { it.toFloat() }
        val baseline = samples.copyOf()

        filter.process(samples)
        filter.reset()

        // Both arrays must still match — Z-weighting must not mutate state nor data on reset.
        assertArrayEquals(baseline, samples, 0f)
    }

    @Test
    fun `mismatched destination size is rejected`() {
        val filter = ZWeightingFilter()
        assertThrows(IllegalArgumentException::class.java) {
            filter.process(FloatArray(8), into = FloatArray(7))
        }
    }
}
