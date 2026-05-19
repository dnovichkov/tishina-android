package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot

/**
 * Resets the in-memory accumulator to the canonical empty state.
 *
 * Kept as its own use-case (rather than calling `MeasurementSnapshot.empty`
 * directly from the ViewModel) so a Phase-3 evolution — e.g. also clearing a
 * "pending save" buffer — can be added without touching the ViewModel.
 */
class ResetMeasurementUseCase {

    operator fun invoke(): MeasurementSnapshot = MeasurementSnapshot.empty
}
