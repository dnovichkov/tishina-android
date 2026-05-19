package ru.dmdp.tishina.core.audio.dsp

/**
 * Composes the full DSP chain from a raw PCM chunk to a single dB SPL reading:
 *
 *   `ShortArray (PCM 16-bit) → normalise → DcBlockFilter → FrequencyFilter (A | Z) →
 *    TimeWeightedRms (Fast | Slow) → SplCalculator → Float (dB SPL)`
 *
 * The processor is stateful across chunks — every IIR block remembers its tail — so callers
 * MUST feed contiguous audio in the order it was captured. Use [reset] when a new measurement
 * begins so leftover state from a previous session does not bleed in.
 *
 * The internal [scratch] buffer is reused across calls; it only reallocates when the chunk size
 * changes. With AudioRecord's stable buffer cadence (~100 ms = 4800 samples at 48 kHz) this means
 * zero allocations on the steady-state hot path.
 */
class AudioProcessor(
    private val dcBlock: DcBlockFilter,
    private val weighting: FrequencyFilter,
    private val timeWeightedRms: TimeWeightedRms,
    private val spl: SplCalculator,
) {

    private var scratch: FloatArray = FloatArray(0)

    /**
     * Push [pcm] through the chain and return the current dB SPL with [offsetDb] applied.
     * The PCM input is left untouched; all processing happens on the internal [scratch] buffer.
     */
    fun process(pcm: ShortArray, offsetDb: Float): Float {
        val n = pcm.size
        if (n == 0) return spl.toDb(rms = 0f, calibrationOffsetDb = offsetDb)
        if (scratch.size != n) scratch = FloatArray(n)

        // Normalise short PCM to [-1, 1) Float. Division by 32 768 keeps positive full-scale at
        // 32767 / 32768 ≈ 0.99997 — close enough for SPL where we have several dB of headroom.
        for (i in 0 until n) {
            scratch[i] = pcm[i] / NORMALISER
        }

        dcBlock.process(scratch)
        weighting.process(scratch)

        var lastRms = 0f
        for (i in 0 until n) {
            lastRms = timeWeightedRms.update(scratch[i])
        }
        return spl.toDb(lastRms, offsetDb)
    }

    /** Clears every internal delay register so the next [process] call behaves as a cold start. */
    fun reset() {
        dcBlock.reset()
        weighting.reset()
        timeWeightedRms.reset()
    }

    private companion object {
        /** PCM 16-bit values are normalised by 2^15 = 32 768. */
        const val NORMALISER: Float = 32_768f
    }
}
