package ru.dmdp.tishina.core.audio.dsp

import kotlin.math.log10
import kotlin.math.max

/**
 * Converts a normalised RMS amplitude (linear, 0..1 with 1.0 = full-scale) into decibels SPL.
 *
 * Calibration anchor follows AOSP CDD §7.8.3.1: close-talk reference of **PCM-RMS 2500** on a
 * 16-bit sample maps to **90 dB SPL**. Translated to the normalised domain
 * (`pcm / 32768`) that the DSP chain works in, RMS `2500 / 32768 ≈ 0.0763` is the 90 dB anchor.
 *
 * Formula: `dB = 20 · log10(max(rms, MIN_RMS) / referenceRms) + ANCHOR_DB + calibrationOffsetDb`.
 *
 * `MIN_RMS` is the safety clamp: `log10(0)` is `-∞`, which would crash the gauge UI; the clamp
 * keeps "complete silence" mappable to a very small finite number instead. Calibration offset
 * stacks on top per user setting (FR-11) — Phase 4 wires it to DataStore.
 */
class SplCalculator(private val referenceRms: Float = DEFAULT_REFERENCE_RMS) {

    /**
     * Convert [rms] in the normalised domain to dB SPL, adding [calibrationOffsetDb] on top.
     * Returns a finite number even when [rms] is zero or negative (clamped to [MIN_RMS]).
     */
    fun toDb(rms: Float, calibrationOffsetDb: Float = 0f): Float {
        val clamped = max(rms, MIN_RMS)
        val ratioDb = AMPLITUDE_DB_FACTOR * log10(clamped.toDouble() / referenceRms.toDouble())
        return (ratioDb + ANCHOR_DB).toFloat() + calibrationOffsetDb
    }

    companion object {
        /** Reference RMS that maps to [ANCHOR_DB]. Matches PCM-RMS 2500 on 16-bit samples. */
        const val DEFAULT_REFERENCE_RMS: Float = 2500f / 32768f

        /** SPL value that the reference RMS resolves to (AOSP close-talk anchor). */
        const val ANCHOR_DB: Float = 90f

        /** Lower bound applied to RMS before the logarithm — avoids `log10(0) = -∞`. */
        const val MIN_RMS: Float = 1e-9f

        /** Decibel formula for amplitude ratios: `dB = 20 · log10(a / a_ref)`. */
        private const val AMPLITUDE_DB_FACTOR: Double = 20.0
    }
}
