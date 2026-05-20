package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Updates only the `note` column of an existing measurement.
 *
 * Validates that [note] does not exceed [NewMeasurement.MAX_NOTE_LENGTH] (FR-6).
 * Passing `null` clears the note. Returns [Result] so DetailViewModel can show
 * a snackbar without try-catching.
 */
class UpdateMeasurementNoteUseCase(private val repository: MeasurementRepository) {

    suspend operator fun invoke(id: Long, note: String?): Result<Unit> {
        if (note != null && note.length > NewMeasurement.MAX_NOTE_LENGTH) {
            return Result.failure(
                IllegalArgumentException(
                    "note length ${note.length} exceeds ${NewMeasurement.MAX_NOTE_LENGTH}",
                ),
            )
        }
        return runCatching { repository.updateNote(id, note) }
    }
}
