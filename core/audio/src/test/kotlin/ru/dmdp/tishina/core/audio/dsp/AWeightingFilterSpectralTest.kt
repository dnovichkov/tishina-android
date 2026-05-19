package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verifies that [AWeightingFilter] reproduces the standard A-weighting response (IEC 61672-1)
 * at 48 kHz to within ±0.3 dB on five reference frequencies.
 *
 * Methodology: feed a pure sine of amplitude 1.0 through the filter; once the IIR transient
 * has decayed, the steady-state output is a sine with amplitude |H(f)| where H is the filter
 * transfer function. Comparing RMS gives |H(f)| directly, and 20·log10 converts to decibels.
 */
@DisplayName("AWeightingFilter @ 48 kHz — spectral response against IEC 61672-1")
@Execution(ExecutionMode.CONCURRENT)
class AWeightingFilterSpectralTest {

    @ParameterizedTest(name = "{0} Hz → expected {1} dB(A) ± 0.3 dB")
    @CsvSource(
        "31.5,  -39.4",
        "125.0, -16.1",
        "1000.0, 0.0",
        "8000.0, -1.1",
        "16000.0, -6.6",
    )
    fun `A-weighting response matches IEC table within tolerance`(
        frequencyHz: Double,
        expectedDb: Double,
    ) {
        val sampleRateHz = 48_000
        val filter = AWeightingFilter(sampleRateHz)

        val gainDb = measureGainDb(filter, sampleRateHz, frequencyHz)

        val deviation = kotlin.math.abs(gainDb - expectedDb)
        assertTrue(
            deviation < 0.3,
            "Frequency=$frequencyHz Hz: expected $expectedDb dB(A), got $gainDb dB " +
                "(deviation=${"%.3f".format(deviation)} dB > 0.3 dB tolerance)",
        )
    }

    private fun measureGainDb(
        filter: AWeightingFilter,
        sampleRateHz: Int,
        frequencyHz: Double,
    ): Double {
        // 1 second of signal — long enough that the transient (a few ms) is negligible.
        val totalSamples = sampleRateHz
        val input = FloatArray(totalSamples) { n ->
            sin(2.0 * PI * frequencyHz * n / sampleRateHz).toFloat()
        }
        val output = input.copyOf()
        filter.process(output)

        // Skip the first 100 ms transient before measuring power.
        val skip = sampleRateHz / 10
        val tail = output.copyOfRange(skip, output.size)
        val tailRms = rms(tail)
        // Reference RMS of a unit-amplitude sine is 1/√2.
        val expectedRms = 1.0 / sqrt(2.0)
        return 20.0 * log10(tailRms / expectedRms)
    }

    private fun rms(values: FloatArray): Double {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v.toDouble()
        return sqrt(sum / values.size)
    }
}
