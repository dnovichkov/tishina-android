package ru.dmdp.tishina.core.audio.dsp

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Exponentially-weighted moving RMS — the time-weighting half of IEC 61672-1 Fast/Slow.
 *
 * Implements a first-order IIR low-pass filter on the squared signal:
 * `state[n] = α·state[n−1] + (1 − α)·x²[n]`, where `α = exp(−1/N)` and `N = sampleRateHz·τ_s`.
 * Returning `sqrt(state)` converts the smoothed power back to an amplitude RMS.
 *
 * Memory and CPU footprint are O(1) per sample — appropriate for the audio hot path where the
 * sliding-window [RmsCalculator] would cost an extra `sumOfSquares()` pass per chunk.
 */
class TimeWeightedRms(sampleRateHz: Int, tauMs: Int) {

    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
        require(tauMs > 0) { "tauMs must be positive, was $tauMs" }
    }

    private val alpha: Float = run {
        val samplesPerTau = sampleRateHz.toDouble() * tauMs / MILLIS_PER_SECOND
        exp(-1.0 / samplesPerTau).toFloat()
    }
    private val oneMinusAlpha: Float = 1f - alpha

    private var state: Float = 0f

    /** Folds [sample] into the running power estimate and returns the current RMS. */
    fun update(sample: Float): Float {
        state = alpha * state + oneMinusAlpha * sample * sample
        return sqrt(state)
    }

    fun reset() {
        state = 0f
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
