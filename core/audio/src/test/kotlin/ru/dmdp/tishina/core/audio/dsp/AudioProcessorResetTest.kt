package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * After a `reset()`, every stateful block (DC-block, A-weighting biquads, time-weighted RMS) must
 * forget its trailing state. Verified by replaying the same input twice and asserting bit-equal
 * results — the same path the production code follows on "stop measurement → start again".
 */
@DisplayName("AudioProcessor.reset() — every stateful stage returns to cold-start")
class AudioProcessorResetTest {

    private val sampleRateHz = 48_000

    @Test
    fun `two identical runs after reset produce identical dB output`() {
        val processor = AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = AWeightingFilter(sampleRateHz),
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = 125),
            spl = SplCalculator(),
        )

        val chunk = sineChunk(frequencyHz = 1_000.0, pcmRms = 2500.0, chunkSize = sampleRateHz / 10)

        // Warm-up + measurement run 1
        var dbA = 0f
        repeat(10) { dbA = processor.process(chunk, 0f) }

        processor.reset()

        var dbB = 0f
        repeat(10) { dbB = processor.process(chunk, 0f) }

        assertEquals(dbA, dbB, 0f, "Identical input after reset must give bit-identical dB output")
    }

    private fun sineChunk(frequencyHz: Double, pcmRms: Double, chunkSize: Int): ShortArray {
        val peak = pcmRms * sqrt(2.0)
        return ShortArray(chunkSize) { n ->
            val v = peak * sin(2.0 * PI * frequencyHz * n / sampleRateHz)
            v.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
