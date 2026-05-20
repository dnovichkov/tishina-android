package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Validates [NewMeasurement] and asks the repository to persist it.
 *
 * Returns [Result.success] with the new row id on success or [Result.failure]
 * (with an [IllegalArgumentException]) when validation fails. The UI layer
 * surfaces failures as localized snackbars; the DAO carries a CHECK constraint
 * as defense in depth, but the use-case is the primary validator so that the
 * user sees the error before a database round-trip.
 *
 * Validation rules:
 * - [NewMeasurement.title] ≤ [NewMeasurement.MAX_TITLE_LENGTH] characters (FR-6).
 * - [NewMeasurement.note] ≤ [NewMeasurement.MAX_NOTE_LENGTH] characters (FR-6).
 * - At least one entry in [NewMeasurement.samples] — saving a graph-less
 *   measurement is a UX nonsense ("nothing to save"), so we surface it as an
 *   error rather than silently writing an aggregate-only row.
 * - All aggregate dB scalars ([NewMeasurement.avgDb], [NewMeasurement.minDb],
 *   [NewMeasurement.maxDb]) must be finite. SQLite stores NaN/Infinity without
 *   complaint, which then poisons every UI surface that formats the row
 *   ("NaN dB" in History card, axis crash in Detail chart). Reject early.
 */
class SaveMeasurementUseCase(private val repository: MeasurementRepository) {

    suspend operator fun invoke(measurement: NewMeasurement): Result<Long> {
        val errorMessage = validate(measurement)
        return if (errorMessage != null) {
            Result.failure(IllegalArgumentException(errorMessage))
        } else {
            runCatching { repository.save(measurement) }
        }
    }

    private fun validate(measurement: NewMeasurement): String? = with(measurement) {
        when {
            title != null && title.length > NewMeasurement.MAX_TITLE_LENGTH ->
                "title length ${title.length} exceeds ${NewMeasurement.MAX_TITLE_LENGTH}"
            note != null && note.length > NewMeasurement.MAX_NOTE_LENGTH ->
                "note length ${note.length} exceeds ${NewMeasurement.MAX_NOTE_LENGTH}"
            samples.isEmpty() -> "samples must not be empty"
            !avgDb.isFinite() || !minDb.isFinite() || !maxDb.isFinite() ->
                "dB scalars must be finite (got avg=$avgDb min=$minDb max=$maxDb)"
            else -> null
        }
    }
}
