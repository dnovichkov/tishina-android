package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Thin wrapper around [MeasurementRepository.observeSummaries] so HistoryViewModel
 * has a single named Hilt-provided dependency. Returning the repository flow
 * verbatim keeps the use-case zero-allocation.
 */
class GetMeasurementsUseCase(private val repository: MeasurementRepository) {

    operator fun invoke(): Flow<List<MeasurementSummary>> = repository.observeSummaries()
}
