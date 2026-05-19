package ru.dmdp.tishina.core.domain.model

/**
 * Time-weighting (integration time constant) applied to the RMS detector.
 *
 * - [FAST] — 125 ms, default for live monitoring (FR-16).
 * - [SLOW] — 1000 ms, gives a more stable reading for steady-state noise.
 *
 * UI toggle to switch between these will appear in Phase 4.
 */
enum class TimeWeighting(val tauMs: Int) {
    FAST(125),
    SLOW(1_000),
}
