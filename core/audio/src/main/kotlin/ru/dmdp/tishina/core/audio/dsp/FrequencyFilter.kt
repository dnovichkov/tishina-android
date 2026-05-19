package ru.dmdp.tishina.core.audio.dsp

/**
 * Frequency-weighting stage of the SPL pipeline.
 *
 * Implementations shape the input PCM stream to match an equal-loudness contour (A-weighting),
 * a flat reference (Z-weighting), and — eventually in Phase 4 — C-weighting.
 *
 * Processing is push-style and stateful: callers feed contiguous chunks of audio in the order
 * captured from the microphone; the filter remembers the trailing IIR state so chunk boundaries
 * are seamless. Use [reset] when starting a fresh measurement so previous tail state cannot leak.
 */
interface FrequencyFilter {

    /**
     * Filters [samples] in place by default; pass an [into] buffer of the same size to keep the
     * source intact. Returning the filtered values via the destination avoids allocations on the
     * audio hot path.
     */
    fun process(samples: FloatArray, into: FloatArray = samples)

    /** Clears every internal delay register so the next call behaves as a cold-start. */
    fun reset()
}
