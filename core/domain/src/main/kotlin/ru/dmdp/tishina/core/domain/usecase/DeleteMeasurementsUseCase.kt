package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.CancellationException
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Permanently removes a batch of measurements (FR-12 multi-select bulk-delete).
 *
 * Returns [Result.success] when the repository accepts the ids, or
 * [Result.failure] with an [IllegalArgumentException] when [ids] is empty —
 * an empty bulk-delete is a programming error (UI must hide the "Delete N"
 * action when nothing is selected), so we surface it as a failure rather than
 * a silent no-op. Unknown ids inside a non-empty set are tolerated by the
 * Data layer (idempotent contract), so the use-case forwards them without
 * pre-checking existence — the soft-delete + Undo flow can defer the real
 * call without first re-querying.
 *
 * Repository failures are wrapped in [Result.failure] so HistoryViewModel can
 * surface them as a localized snackbar; [CancellationException] is rethrown
 * so structured concurrency keeps working (a viewModelScope cancellation in
 * the middle of bulk-delete is not a "delete failed" — it's a cancellation).
 */
class DeleteMeasurementsUseCase(private val repository: MeasurementRepository) {

    suspend operator fun invoke(ids: Set<Long>): Result<Unit> {
        if (ids.isEmpty()) {
            return Result.failure(IllegalArgumentException("ids must not be empty"))
        }
        return try {
            repository.deleteAll(ids)
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
}
