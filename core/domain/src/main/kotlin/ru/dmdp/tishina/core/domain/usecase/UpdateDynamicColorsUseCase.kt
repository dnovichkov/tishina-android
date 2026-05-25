package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/** FR-17 — toggle Material You dynamic colors. No-op on API ≤ 30 (handled in theme). */
class UpdateDynamicColorsUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.updateDynamicColors(enabled)
    }
}
