package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * Loads a single [MeasurementDetails] by id for the Detail screen (FR-10).
 *
 * Returns `null` for unknown ids — DetailViewModel uses that signal to emit a
 * "not found" Snackbar effect and pop the back stack, rather than throwing.
 */
class GetMeasurementByIdUseCase(private val repository: MeasurementRepository) {

    suspend operator fun invoke(id: Long): MeasurementDetails? = repository.getById(id)
}
