package ru.dmdp.tishina.core.domain.model

/**
 * Aggregate row shown in the History list (FR-9). One per measurement; samples are
 * loaded lazily for the Detail screen only.
 *
 * @property id stable database identifier; surfaced to the Detail route as type-safe arg.
 * @property createdAtEpochMs wall-clock time at which the user pressed Save (epoch ms,
 * UTC). Sorting is descending on this column.
 * @property durationMs total length of the measurement in milliseconds.
 * @property avgDb arithmetic mean dB over the whole session.
 * @property minDb / [maxDb] session-wide extremes.
 * @property title optional short label entered in the Save dialog. `null` means
 * "user didn't fill it in"; the card falls back to a localized "Замер" + date.
 * @property note optional longer free-form note (≤ 200 chars per FR-6 / NFR-12).
 * @property sparklinePreview ≤ 20-point downsample of [avgDb] over time for the
 * mini-chart on the card; empty list is a valid value (very short session or
 * historical record from a tooling glitch).
 */
data class MeasurementSummary(
    val id: Long,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val avgDb: Float,
    val minDb: Float,
    val maxDb: Float,
    val title: String?,
    val note: String?,
    val sparklinePreview: List<Float>,
)

/**
 * Full data needed by the Detail screen (FR-10): the summary plus the full
 * 5 Hz sample stream and the configuration snapshot that produced it.
 *
 * Settings (weighting, time-weighting, calibration offset) are stored per
 * measurement, not globally, so historical readings stay meaningful even after
 * the user changes Settings later (Phase 4).
 */
data class MeasurementDetails(
    val summary: MeasurementSummary,
    val samples: List<SoundSample>,
    val weighting: FrequencyWeighting,
    val timeWeighting: TimeWeighting,
    val calibrationOffsetDb: Float,
    val sampleRateHz: Int,
)

/**
 * DTO passed to [ru.dmdp.tishina.core.domain.repository.MeasurementRepository.save]
 * when the user confirms the Save dialog. Has no `id` — Room assigns it on insert.
 *
 * Validation of [title] / [note] length lives in `SaveMeasurementUseCase`; the
 * DAO carries a CHECK constraint as a defense-in-depth guarantee that data
 * inserted directly bypassing the use-case (tests, future imports) still obeys
 * the spec.
 */
data class NewMeasurement(
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val avgDb: Float,
    val minDb: Float,
    val maxDb: Float,
    val title: String?,
    val note: String?,
    val weighting: FrequencyWeighting,
    val timeWeighting: TimeWeighting,
    val calibrationOffsetDb: Float,
    val sampleRateHz: Int,
    val samples: List<SoundSample>,
) {
    companion object {
        /** Maximum length of [title] in characters (FR-6 / NFR-12). */
        const val MAX_TITLE_LENGTH: Int = 80

        /** Maximum length of [note] in characters (FR-6 / NFR-12). */
        const val MAX_NOTE_LENGTH: Int = 200
    }
}
