package ru.dmdp.tishina.feature.measure

import ru.dmdp.tishina.core.domain.model.SoundSample

/**
 * Immutable snapshot rendered by `MeasureScreen`.
 *
 * Defaults mirror `MeasurementSnapshot.empty`: `Float.POSITIVE_INFINITY` /
 * `Float.NEGATIVE_INFINITY` act as monoid identities so the first sample's
 * `min(POSITIVE_INFINITY, x)` and `max(NEGATIVE_INFINITY, x)` initialise the
 * accumulator without any "first sample" branch.
 */
data class MeasureUiState(
    val current: Float = 0f,
    val min: Float = Float.POSITIVE_INFINITY,
    val max: Float = Float.NEGATIVE_INFINITY,
    val avg: Float = 0f,
    val durationMs: Long = 0L,
    val recent: List<SoundSample> = emptyList(),
    val phase: MeasurementPhase = MeasurementPhase.Idle,
    val permissionState: PermissionState = PermissionState.Unknown,
)
