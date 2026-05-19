package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * End-to-end DSP smoke: feeds a synthetic sine through the whole chain
 * `DcBlock → AWeighting | ZWeighting → TimeWeightedRms → Spl` and asserts the dB SPL output
 * matches the anchor: PCM-RMS 2500 (close-talk reference) ⇒ 90 dB SPL.
 *
 * 1 kHz is special — A-weighting response is 0 dB there, so A and Z must agree.
 * A-weighting at 100 Hz drops the response by −19.1 dB per IEC 61672-1; we use that
 * to confirm the weighting stage is actually applied, not bypassed.
 */
@DisplayName("AudioProcessor integration — full pipeline produces ~90 dB at the AOSP-calibrated input")
class AudioProcessorIntegrationTest {

    private val sampleRateHz = 48_000

    @Test
    fun `1 kHz sine with PCM-RMS 2500 reads as 90 dB through A-weighting`() {
        val processor = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = AWeightingFilter(sampleRateHz),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )

        val db = warmUpAndMeasure(processor, frequencyHz = 1_000.0, pcmRms = 2500.0)

        assertEquals(90.0f, db, 0.5f, "1 kHz @ PCM-RMS 2500 expected 90 dB(A), got $db")
    }

    @Test
    fun `1 kHz sine with PCM-RMS 2500 reads as 90 dB through Z-weighting too`() {
        val processor = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = ZWeightingFilter(),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )

        val db = warmUpAndMeasure(processor, frequencyHz = 1_000.0, pcmRms = 2500.0)

        assertEquals(90.0f, db, 0.5f, "1 kHz @ PCM-RMS 2500 expected 90 dB(Z) too, got $db")
    }

    @Test
    fun `100 Hz sine drops by approximately 19 dB under A-weighting versus Z-weighting`() {
        // Reference: A-weighting response at 100 Hz is −19.1 dB per IEC 61672-1.
        val aProc = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = AWeightingFilter(sampleRateHz),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )
        val zProc = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = ZWeightingFilter(),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )

        val dbA = warmUpAndMeasure(aProc, frequencyHz = 100.0, pcmRms = 2500.0)
        val dbZ = warmUpAndMeasure(zProc, frequencyHz = 100.0, pcmRms = 2500.0)

        val deltaDb = dbZ - dbA
        // Expected ≈ 19.1 dB. Allow ±1.0 dB to absorb FAST integration ripple and discretisation.
        assertTrue(
            kotlin.math.abs(deltaDb - 19.1f) < 1.0f,
            "Expected ~19.1 dB difference Z vs A at 100 Hz, got Δ=$deltaDb dB (A=$dbA, Z=$dbZ)",
        )
    }

    @Test
    fun `calibration offset is passed through to the final SPL value`() {
        val processor = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = AWeightingFilter(sampleRateHz),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )

        val db = warmUpAndMeasure(processor, frequencyHz = 1_000.0, pcmRms = 2500.0, offsetDb = +5.0f)

        assertEquals(95.0f, db, 0.5f)
    }

    /**
     * Feeds the processor enough sine-wave audio that the IIR transients (DC-block, weighting,
     * time-weighted RMS) have all settled, then returns the final dB. We pump in eight FAST
     * time-constants (8·125 ms = 1 s) which is well past the 99% asymptote.
     */
    private fun warmUpAndMeasure(
        processor: AudioProcessor,
        frequencyHz: Double,
        pcmRms: Double,
        offsetDb: Float = 0f,
    ): Float {
        val chunkSize = sampleRateHz / 10 // 100 ms per chunk — same shape AudioRecord delivers
        val totalChunks = 10 // 1.0 s of warm-up + measurement
        val peak = pcmRms * sqrt(2.0) // sine RMS → peak conversion
        var sampleIndex = 0
        var lastDb = 0f
        repeat(totalChunks) {
            val chunk = ShortArray(chunkSize) {
                val v = peak * sin(2.0 * PI * frequencyHz * sampleIndex / sampleRateHz)
                sampleIndex++
                v.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            lastDb = processor.process(chunk, offsetDb)
        }
        return lastDb
    }
}
