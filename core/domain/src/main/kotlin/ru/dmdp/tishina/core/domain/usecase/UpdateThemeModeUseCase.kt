package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/** FR-17 — System / Light / Dark theme selection. */
class UpdateThemeModeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) {
        repository.updateThemeMode(mode)
    }
}
