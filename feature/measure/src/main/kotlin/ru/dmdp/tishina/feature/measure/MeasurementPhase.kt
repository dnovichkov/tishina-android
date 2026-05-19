package ru.dmdp.tishina.feature.measure

/**
 * High-level lifecycle of the measurement session as seen by the UI.
 *
 * `Idle` covers both the freshly opened screen and the post-`Reset` state —
 * they are visually identical in Phase 2, so collapsing them simplifies
 * `MeasureScreen` rendering. Phase 3 may split them when a "saved" badge
 * is added.
 */
enum class MeasurementPhase {
    Idle,
    Running,
    Paused,
}
