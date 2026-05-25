package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/** FR-16 — Fast (125 ms) / Slow (1 s) RMS time constant. */
class UpdateTimeWeightingUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(weighting: TimeWeighting) {
        repository.updateTimeWeighting(weighting)
    }
}
