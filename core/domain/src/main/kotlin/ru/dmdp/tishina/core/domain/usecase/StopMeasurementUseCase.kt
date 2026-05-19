package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot

/**
 * Freezes the current measurement.
 *
 * Phase-2 contract: cancellation of the upstream `Flow<MeasurementSnapshot>` is
 * driven by the **caller** (`MeasureViewModel` cancels its `viewModelScope`
 * collect job). This use-case exists so the ViewModel has a single, named
 * Hilt-injectable hook for the stop action, and Phase 3 can replace it with a
 * version that also flushes the snapshot to history without changing the
 * call-site.
 */
class StopMeasurementUseCase {

    /** Returns the snapshot unchanged — Phase 2 has no extra side-effects. */
    operator fun invoke(current: MeasurementSnapshot): MeasurementSnapshot = current
}
