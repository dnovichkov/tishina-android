package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The SPL conversion turns time-weighted RMS into decibels SPL using an empirical anchor:
 * `RMS = 2500/32768 ↔ 90 dB SPL` (AOSP CDD §7.8.3.1 close-talk reference). Tests verify the
 * 20·log10 law around that anchor and the clamp that keeps `rms = 0` from producing `-∞`.
 */
@DisplayName("SplCalculator — 20·log10 around the AOSP close-talk 90 dB anchor with safe zero-clamp")
class SplCalculatorTest {

    @ParameterizedTest(name = "rms={0}, offset={1} → {2} dB ± {3}")
    @CsvSource(
        // rms,         offset, expectedDb, tolerance
        "0.07629395,    0.0,    90.0,       0.05", // anchor: 2500/32768
        "0.007629395,   0.0,    70.0,       0.05", // 10× quieter ⇒ −20 dB
        "0.7629395,     0.0,    110.0,      0.05", // 10× louder ⇒ +20 dB
        "0.07629395,    5.0,    95.0,       0.05", // calibration offset adds linearly
        "0.07629395,    -3.5,   86.5,       0.05", // negative offset still adds linearly
    )
    fun `dB SPL follows 20 log10 of rms over the 2500-32768 anchor with calibration offset`(
        rms: Float,
        offsetDb: Float,
        expectedDb: Float,
        tolerance: Float,
    ) {
        val calc = SplCalculator()

        val db = calc.toDb(rms, offsetDb)

        assertEquals(expectedDb, db, tolerance, "rms=$rms offset=$offsetDb")
    }

    @Test
    fun `rms equal to zero is clamped so the result is finite instead of negative infinity`() {
        val calc = SplCalculator()

        val db = calc.toDb(rms = 0f, calibrationOffsetDb = 0f)

        assertTrue(db.isFinite(), "SPL must be finite for rms=0, got $db")
        assertTrue(db < 0f, "SPL of silence must be a very low number, got $db dB")
        // With MIN_RMS = 1e-9 and ref ≈ 0.0763, 20·log10(MIN_RMS/ref) + 90 ≈ −67.6 dB.
        // Use a wide bound to allow future MIN_RMS adjustments; the contract is "finite & very low".
        assertTrue(db > -300f, "SPL must be bounded below by the clamp, got $db dB")
    }

    @Test
    fun `negative rms is also clamped — sqrt of state never delivers a negative but we double-check`() {
        val calc = SplCalculator()

        val db = calc.toDb(rms = -0.5f, calibrationOffsetDb = 0f)

        assertTrue(db.isFinite(), "SPL must be finite for negative rms, got $db")
    }

    @Test
    fun `custom reference rms can re-anchor the curve at the same 90 dB point`() {
        // A device with twice the gain reports double the RMS at the same SPL. By passing the
        // observed RMS-at-90-dB as the reference we re-anchor toDb(ref) = 90 dB.
        val customRef = 0.1525879f // 5000/32768 — twice the close-talk default
        val calc = SplCalculator(referenceRms = customRef)

        val db = calc.toDb(customRef, 0f)

        assertEquals(90.0f, db, 0.05f)
    }
}
