package ru.dmdp.tishina.core.audio.dsp

/**
 * Second-order IIR section implemented in Direct Form II Transposed (DF II T).
 *
 * The transfer function is normalised so `a0 = 1` and stored as five coefficients (b0, b1, b2,
 * a1, a2). DF II T is numerically the kindest of the four direct forms for fixed-point and
 * single-precision floats: only two state registers per section and the addition order produces
 * smaller round-off error than DF I when the poles sit close to the unit circle (typical for
 * the low-frequency stages of A-weighting).
 *
 * Recurrence:
 * ```
 * y[n] = b0·x[n] + d1
 * d1   = b1·x[n] − a1·y[n] + d2
 * d2   = b2·x[n] − a2·y[n]
 * ```
 *
 * The class is stateful — feed contiguous chunks of audio and call [reset] only when starting
 * a fresh measurement so the trailing IIR memory doesn't leak between sessions.
 */
class BiquadFilter(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {

    private var d1: Float = 0f
    private var d2: Float = 0f

    /**
     * Filters [samples] through the biquad. In-place by default; supply [into] for a copy mode.
     */
    fun process(samples: FloatArray, into: FloatArray = samples) {
        require(samples.size == into.size) {
            "destination size ${into.size} must match samples size ${samples.size}"
        }
        var s1 = d1
        var s2 = d2
        for (i in samples.indices) {
            val x = samples[i]
            val y = b0 * x + s1
            s1 = b1 * x - a1 * y + s2
            s2 = b2 * x - a2 * y
            into[i] = y
        }
        d1 = s1
        d2 = s2
    }

    fun reset() {
        d1 = 0f
        d2 = 0f
    }
}
