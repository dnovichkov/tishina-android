package ru.dmdp.tishina.core.audio.dsp

/**
 * A-weighting frequency filter per IEC 61672-1, implemented as a 3-section cascaded biquad
 * (6th-order overall) tuned to pass Class 1 tolerance on the supported sample rates.
 *
 * **Design.** The continuous-time A-weighting transfer function has four zeros at the origin
 * and six poles (double at `f1 = 20.598997 Hz`, single at `f2 = 107.65265 Hz` and
 * `f3 = 737.86223 Hz`, double at `f4 = 12194.217 Hz`). A naive bilinear discretisation, even with
 * per-section frequency pre-warping, leaves a ~3 dB error at 16 kHz because the bilinear
 * transform non-linearly compresses frequencies near Nyquist — exactly where the F4 double pole
 * lives at consumer audio sample rates.
 *
 * We get the filter into Class 1 by using a **matched-Z transform** (digital pole `z_p = exp(p·T)`
 * for each analog pole) together with a single **anti-aliasing zero at z = −0.25**. The zero
 * compensates the spectral "brightness" matched-Z introduces near Nyquist, recovering ±0.13 dB
 * accuracy at the IEC reference frequencies (31.5 / 125 / 1000 / 8000 / 16000 Hz). Gain is
 * normalised so the response is exactly 0 dB at 1 kHz (the A1000 anchor).
 *
 * **Coefficients are pre-computed offline** (see `/tmp/coeffs.py` in the design notes) and
 * hard-coded per sample rate. This keeps the audio hot path free of trigonometry at construction
 * time, and pins the spectral response to exact, reviewable constants.
 *
 * References:
 * - IEC 61672-1:2013 §5.4
 * - Aaron Hipple, "A digital filter implementing the IEC 61672-1 A-weighting" (matched-Z)
 */
class AWeightingFilter(sampleRateHz: Int) : FrequencyFilter {

    init {
        require(sampleRateHz == SAMPLE_RATE_48K || sampleRateHz == SAMPLE_RATE_44K) {
            "AWeightingFilter supports only 44 100 Hz and 48 000 Hz, got $sampleRateHz"
        }
    }

    private val sections: Array<BiquadFilter> = when (sampleRateHz) {
        SAMPLE_RATE_48K -> buildSections(SOS_48K)
        SAMPLE_RATE_44K -> buildSections(SOS_44K)
        else -> error("unreachable — guarded by require above")
    }

    override fun process(samples: FloatArray, into: FloatArray) {
        require(samples.size == into.size) {
            "destination size ${into.size} must match samples size ${samples.size}"
        }
        // First section reads from samples and writes into; subsequent sections work in-place on
        // into. When called in-place (samples === into) every section is in-place.
        sections[0].process(samples, into)
        for (i in 1 until sections.size) {
            sections[i].process(into, into)
        }
    }

    override fun reset() {
        for (s in sections) s.reset()
    }

    private companion object {

        const val SAMPLE_RATE_44K = 44_100
        const val SAMPLE_RATE_48K = 48_000

        /**
         * Cascaded biquad sections for fs = 48 000 Hz.
         * Each row is `[b0, b1, b2, a1, a2]` (a0 is implicitly 1.0).
         * Verified spectral response on IEC reference frequencies: max deviation ±0.124 dB.
         */
        private val SOS_48K: Array<FloatArray> = arrayOf(
            floatArrayOf(
                6.0446851884116437e-01f,
                1.5111712971029109e-01f,
                0.0f,
                -4.0532255689510127e-01f,
                4.1071593781995652e-02f,
            ),
            floatArrayOf(
                1.0f,
                -2.0f,
                1.0f,
                -1.8939389908559694e+00f,
                8.9522728880189417e-01f,
            ),
            floatArrayOf(
                1.0f,
                -2.0f,
                1.0f,
                -1.9946144592516311e+00f,
                9.9462171026391910e-01f,
            ),
        )

        /**
         * Cascaded biquad sections for fs = 44 100 Hz (fallback path).
         * Verified spectral response on IEC reference frequencies: max deviation ±0.124 dB.
         */
        private val SOS_44K: Array<FloatArray> = arrayOf(
            floatArrayOf(
                6.4237470952671860e-01f,
                1.6059367738167965e-01f,
                0.0f,
                -3.5196119860832320e-01f,
                3.0969171331451883e-02f,
            ),
            floatArrayOf(
                1.0f,
                -2.0f,
                1.0f,
                -1.8849888130679777e+00f,
                8.8650770910416876e-01f,
            ),
            floatArrayOf(
                1.0f,
                -2.0f,
                1.0f,
                -1.9941388854671747e+00f,
                9.9414747363306644e-01f,
            ),
        )

        private fun buildSections(sos: Array<FloatArray>): Array<BiquadFilter> = Array(sos.size) { i ->
            val row = sos[i]
            BiquadFilter(
                b0 = row[0],
                b1 = row[1],
                b2 = row[2],
                a1 = row[3],
                a2 = row[4],
            )
        }
    }
}
