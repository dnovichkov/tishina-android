package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/**
 * FR-19 — resets the calibration offset to `0.0f`. Kept as a dedicated use-case
 * (rather than calling `updateCalibrationOffset(0f)` from the ViewModel) so
 * future logic (analytics, "are you sure?" gate) can be added in one place.
 */
class ResetCalibrationUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke() {
        repository.resetCalibration()
    }
}
