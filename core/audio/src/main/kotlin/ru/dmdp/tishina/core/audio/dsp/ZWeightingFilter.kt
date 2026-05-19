package ru.dmdp.tishina.core.audio.dsp

/**
 * Z-weighting (a.k.a. "linear" or "flat") frequency response — IEC 61672-1 defines it as no
 * weighting at all between 10 Hz and 20 kHz. In this project Z is the unfiltered baseline used
 * both for raw RMS unit tests and for users who later opt out of A-weighting via Settings
 * (Phase 4).
 *
 * The implementation is a no-op in-place: it does not even touch `samples` when used in-place,
 * and only copies bytes when an explicit destination is provided. That keeps the audio hot path
 * allocation-free.
 */
class ZWeightingFilter : FrequencyFilter {

    override fun process(samples: FloatArray, into: FloatArray) {
        require(samples.size == into.size) {
            "destination size ${into.size} must match samples size ${samples.size}"
        }
        if (samples !== into) {
            System.arraycopy(samples, 0, into, 0, samples.size)
        }
    }

    override fun reset() {
        // Z-weighting has no internal state.
    }
}
