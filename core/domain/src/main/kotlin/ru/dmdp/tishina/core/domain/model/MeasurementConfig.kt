package ru.dmdp.tishina.core.domain.model

/**
 * Snapshot of the measurement settings consumed by the audio engine on each Start.
 *
 * In Phase 2 defaults are used everywhere; Phase 4 will populate this from
 * `SettingsRepository` (DataStore-backed).
 *
 * @property frequencyWeighting which weighting curve to apply (default A — FR-15).
 * @property timeWeighting RMS integration time constant (default FAST — FR-16).
 * @property calibrationOffsetDb user-configurable correction in dB, added on top
 * of the SPL formula. Default `0.0f` means "trust the device baseline".
 */
data class MeasurementConfig(
    val frequencyWeighting: FrequencyWeighting = FrequencyWeighting.A,
    val timeWeighting: TimeWeighting = TimeWeighting.FAST,
    val calibrationOffsetDb: Float = 0.0f,
)
