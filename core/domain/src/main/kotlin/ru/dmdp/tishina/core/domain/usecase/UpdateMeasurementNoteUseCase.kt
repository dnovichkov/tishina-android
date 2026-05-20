package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.CancellationException
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
        // `runCatching` would swallow CancellationException — rethrow it so structured
        // concurrency cancels the rest of the calling coroutine instead of producing a
        // misleading `Result.failure` that the UI maps to "save failed". Errors (OOM etc.)
        // propagate unchanged; only recoverable Exception subtypes are wrapped.
        return try {
            Result.success(repository.updateNote(id, note))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
}
