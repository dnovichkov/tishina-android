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
 * Mirrors [AWeightingFilterSpectralTest] for the 44.1 kHz fallback path the
 * [AudioRecordPcmSource][ru.dmdp.tishina.core.audio.source.AudioRecordPcmSource] uses when 48 kHz
 * is unavailable on the device.
 */
@DisplayName("AWeightingFilter @ 44.1 kHz — spectral response against IEC 61672-1")
@Execution(ExecutionMode.CONCURRENT)
class AWeightingFilter44100Test {

    @ParameterizedTest(name = "{0} Hz → expected {1} dB(A) ± 0.3 dB")
    @CsvSource(
        "31.5,  -39.4",
        "125.0, -16.1",
        "1000.0, 0.0",
        "8000.0, -1.1",
        // The Nyquist of 44.1 kHz is 22.05 kHz, so 16 kHz still has tolerable margin.
        "16000.0, -6.6",
    )
    fun `A-weighting response matches IEC table within tolerance at 44 100 Hz`(
        frequencyHz: Double,
        expectedDb: Double,
    ) {
        val sampleRateHz = 44_100
        val filter = AWeightingFilter(sampleRateHz)

        val sampleCount = sampleRateHz
        val signal = FloatArray(sampleCount) { n ->
            sin(2.0 * PI * frequencyHz * n / sampleRateHz).toFloat()
        }
        filter.process(signal)

        val skip = sampleRateHz / 10
        val tail = signal.copyOfRange(skip, signal.size)
        val tailRms = rms(tail)
        val expectedRms = 1.0 / sqrt(2.0)
        val gainDb = 20.0 * log10(tailRms / expectedRms)

        val deviation = kotlin.math.abs(gainDb - expectedDb)
        // Tolerance lifted at 16 kHz to 0.5 dB because 44.1 kHz bilinear warping pushes the
        // top-of-band pole closer to Nyquist; A-weighting standards permit Class 1 of ±1.5 dB at
        // 16 kHz so 0.5 dB is well inside the spec.
        val tolerance = if (frequencyHz >= 15_000.0) 0.5 else 0.3
        assertTrue(
            deviation < tolerance,
            "Frequency=$frequencyHz Hz (fs=44.1 kHz): expected $expectedDb dB(A), got $gainDb dB " +
                "(deviation=${"%.3f".format(deviation)} dB > $tolerance dB tolerance)",
        )
    }

    private fun rms(values: FloatArray): Double {
        var sum = 0.0
        for (v in values) sum += v.toDouble() * v.toDouble()
        return sqrt(sum / values.size)
    }
}
