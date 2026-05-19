package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin

@DisplayName("AWeightingFilter — reset clears all biquad delay lines")
class AWeightingFilterResetTest {

    @Test
    fun `two identical runs separated by reset produce identical outputs`() {
        val sampleRateHz = 48_000
        val filter = AWeightingFilter(sampleRateHz)
        val input = FloatArray(2_048) { n ->
            // Sum two tones so all biquads have meaningful activity in their state.
            (
                sin(2.0 * PI * 1_000.0 * n / sampleRateHz) +
                    0.5 * sin(2.0 * PI * 100.0 * n / sampleRateHz)
                ).toFloat()
        }

        val firstRun = input.copyOf()
        filter.process(firstRun)

        filter.reset()

        val secondRun = input.copyOf()
        filter.process(secondRun)

        assertArrayEquals(firstRun, secondRun, 0f)
    }

    @Test
    fun `without reset a second run differs because biquad state persists`() {
        val sampleRateHz = 48_000
        val filter = AWeightingFilter(sampleRateHz)
        val input = FloatArray(2_048) { n ->
            sin(2.0 * PI * 1_000.0 * n / sampleRateHz).toFloat()
        }

        val firstRun = input.copyOf()
        filter.process(firstRun)

        val secondRun = input.copyOf()
        filter.process(secondRun)

        // Initial samples of run two diverge from run one because delay lines carry over.
        var anyDifference = false
        for (i in 0 until 64) {
            if (kotlin.math.abs(firstRun[i] - secondRun[i]) > 1e-7f) {
                anyDifference = true
                break
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(
            anyDifference,
            "Without reset the second run must start from a non-zero state, " +
                "yet first 64 samples matched the cold-start run exactly.",
        )
    }
}
