package ru.dmdp.tishina.core.domain.model

/**
 * Frequency weighting curve applied to the audio signal before SPL calculation.
 *
 * - [A] — IEC 61672-1 A-weighting, default for environmental noise (matches FR-15).
 * - [Z] — flat (no weighting), used as a bypass for verifying RMS pipeline in isolation.
 *
 * C-weighting will be added in Phase 4 together with the Settings screen.
 */
enum class FrequencyWeighting {
    A,
    Z,
}
