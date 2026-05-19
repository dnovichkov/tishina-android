package ru.dmdp.tishina.core.domain.model

/**
 * Accumulated state of an active measurement, produced by `StartMeasurementUseCase`
 * on every new [SoundSample]. Drives the UI in `MeasureScreen`.
 *
 * @property currentDb most recent reading (drives the big live readout).
 * @property minDb minimum dB seen so far in this session.
 * Sentinel `Float.POSITIVE_INFINITY` in [empty] is a monoid identity for `min`.
 * @property maxDb maximum dB seen so far in this session.
 * Sentinel `Float.NEGATIVE_INFINITY` in [empty] is a monoid identity for `max`.
 * @property avgDb running arithmetic mean (NOT energy-average — see § 6 spec).
 * @property durationMs how long the measurement has been running.
 * @property recent rolling window of the last 60 seconds of samples,
 * used to render the line chart. Trimmed by [StartMeasurementUseCase].
 */
data class MeasurementSnapshot(
    val currentDb: Float,
    val minDb: Float,
    val maxDb: Float,
    val avgDb: Float,
    val durationMs: Long,
    val recent: List<SoundSample>,
) {
    companion object {
        val empty: MeasurementSnapshot = MeasurementSnapshot(
            currentDb = 0.0f,
            minDb = Float.POSITIVE_INFINITY,
            maxDb = Float.NEGATIVE_INFINITY,
            avgDb = 0.0f,
            durationMs = 0L,
            recent = emptyList(),
        )
    }
}
