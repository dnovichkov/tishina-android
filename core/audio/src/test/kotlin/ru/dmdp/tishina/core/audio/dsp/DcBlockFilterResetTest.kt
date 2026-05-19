package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DcBlockFilter.reset() — wipes internal state between sessions")
class DcBlockFilterResetTest {

    @Test
    fun `reset restores filter to initial state so two runs produce identical output`() {
        val first = FloatArray(256) { 1f }
        val second = FloatArray(256) { 1f }

        val filter = DcBlockFilter()
        val firstOutput = FloatArray(first.size)
        filter.process(first, into = firstOutput)

        // Without reset, internal state would carry over and produce different output.
        filter.reset()
        val secondOutput = FloatArray(second.size)
        filter.process(second, into = secondOutput)

        assertArrayEquals(firstOutput, secondOutput, 0f)
    }

    @Test
    fun `without reset a second pass differs from the first (state carries over)`() {
        val first = FloatArray(256) { 1f }
        val second = FloatArray(256) { 1f }

        val filter = DcBlockFilter()
        val firstOutput = FloatArray(first.size)
        filter.process(first, into = firstOutput)
        val secondOutput = FloatArray(second.size)
        filter.process(second, into = secondOutput)

        // After processing, internal state holds the converged response; a second identical input
        // produces a different (closer-to-zero) early response. The two outputs must differ
        // at the head to confirm state was retained.
        var anyDifference = false
        for (i in 0 until 32) {
            if (firstOutput[i] != secondOutput[i]) {
                anyDifference = true
                break
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(
            anyDifference,
            "Without reset, the second pass must reflect retained filter state",
        )
    }
}
