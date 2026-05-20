package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Permanently removes a measurement (and its samples via FK CASCADE).
 *
 * Idempotent: invoking with an unknown id is a no-op, matching SQLite
 * `DELETE` semantics. HistoryViewModel relies on this so that the soft-delete
 * + Undo flow can defer the real call without first checking row existence
 * (the row may have been removed by a concurrent operation, e.g. backup
 * restore).
 */
class DeleteMeasurementUseCase(private val repository: MeasurementRepository) {

    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
