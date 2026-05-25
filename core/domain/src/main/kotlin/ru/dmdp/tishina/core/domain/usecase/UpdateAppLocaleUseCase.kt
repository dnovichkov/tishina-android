package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/** FR-18 — System / Russian / English UI locale. */
class UpdateAppLocaleUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(locale: AppLocale) {
        repository.updateAppLocale(locale)
    }
}
