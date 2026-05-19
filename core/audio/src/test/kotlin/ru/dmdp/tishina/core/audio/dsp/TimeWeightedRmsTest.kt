package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@DisplayName("TimeWeightedRms — IEC 61672-1 exponential smoothing for Fast/Slow time weightings")
class TimeWeightedRmsTest {

    @ParameterizedTest(name = "tauMs={0} → reaches ≥99% of sine RMS by 5·tau")
    @CsvSource(
        "125", // Fast
        "1000", // Slow
    )
    fun `step response reaches 99 percent of asymptote after five tau`(tauMs: Int) {
        val sampleRateHz = 48_000
        val frequencyHz = 1_000.0
        val amplitude = 1f
        val rms = TimeWeightedRms(sampleRateHz = sampleRateHz, tauMs = tauMs)

        // Drive with a sine wave starting from cold state (t=0).
        val totalSamples = (5L * tauMs * sampleRateHz / 1000L).toInt()
        var output = 0f
        for (n in 0 until totalSamples) {
            val sample = amplitude * sin(2.0 * PI * frequencyHz * n / sampleRateHz).toFloat()
            output = rms.update(sample)
        }

        val asymptote = amplitude / sqrt(2f) // ideal RMS of unit-amplitude sine
        val ratio = output / asymptote
        assertTrue(
            ratio >= 0.99f,
            "After 5·tau ($totalSamples samples, tauMs=$tauMs) " +
                "expected ≥99% of asymptote, got ratio=$ratio (output=$output, target≈$asymptote)",
        )
        // And not overshooting wildly (within +5% of asymptote).
        assertTrue(
            ratio <= 1.05f,
            "EMA must not overshoot asymptote materially, got ratio=$ratio",
        )
    }

    @Test
    fun `constant DC signal stabilises at the DC magnitude as time-weighted RMS`() {
        // For x[n] = c, the EMA of x² converges to c², so sqrt is |c|.
        val rms = TimeWeightedRms(sampleRateHz = 48_000, tauMs = 125)
        var output = 0f
        // 8·tau samples = comfortable convergence margin against float drift.
        repeat(48_000 / 8 * 8) { output = rms.update(0.3f) }
        assertEquals(0.3f, output, 1e-3f)
    }

    @Test
    fun `zero input leaves the output at zero forever`() {
        val rms = TimeWeightedRms(sampleRateHz = 48_000, tauMs = 125)
        var output = -1f
        repeat(10_000) { output = rms.update(0f) }
        assertEquals(0f, output, 0f)
    }

    @Test
    fun `Slow weighting responds more slowly than Fast for the same step input`() {
        // After exactly 125 ms of unit sine, Fast should already be very close to 0.7071,
        // while Slow (tau=1000 ms) should be far from it.
        val sampleRateHz = 48_000
        val samples125ms = sampleRateHz / 8
        val fast = TimeWeightedRms(sampleRateHz = sampleRateHz, tauMs = 125)
        val slow = TimeWeightedRms(sampleRateHz = sampleRateHz, tauMs = 1000)

        var fastOut = 0f
        var slowOut = 0f
        for (n in 0 until samples125ms) {
            val s = sin(2.0 * PI * 1_000.0 * n / sampleRateHz).toFloat()
            fastOut = fast.update(s)
            slowOut = slow.update(s)
        }

        val asymptote = 1f / sqrt(2f)
        // Fast already past 60% of asymptote (≈ 1 - e^-1 = 0.632 in state-space → ~0.79 in RMS-space).
        assertTrue(fastOut > 0.6f * asymptote, "Fast at 1·tau should be well-risen, got $fastOut")
        // Slow at t=0.125·tau is still very low.
        assertTrue(slowOut < 0.4f * asymptote, "Slow at 0.125·tau should still be low, got $slowOut")
        assertTrue(slowOut < fastOut, "Slow must trail Fast at equal time, got slow=$slowOut fast=$fastOut")
    }

    @Test
    fun `reset returns the filter to a cold-start response`() {
        val sampleRateHz = 48_000
        val rms = TimeWeightedRms(sampleRateHz = sampleRateHz, tauMs = 125)
        val seq = FloatArray(2_000) { n ->
            sin(2.0 * PI * 1_000.0 * n / sampleRateHz).toFloat()
        }

        var a = 0f
        for (v in seq) a = rms.update(v)

        rms.reset()
        var b = 0f
        for (v in seq) b = rms.update(v)

        assertEquals(a, b, 0f)
    }

    @Test
    fun `non-positive sample rate is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeWeightedRms(sampleRateHz = 0, tauMs = 125)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeWeightedRms(sampleRateHz = -1, tauMs = 125)
        }
    }

    @Test
    fun `non-positive tau is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeWeightedRms(sampleRateHz = 48_000, tauMs = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeWeightedRms(sampleRateHz = 48_000, tauMs = -5)
        }
    }
}
