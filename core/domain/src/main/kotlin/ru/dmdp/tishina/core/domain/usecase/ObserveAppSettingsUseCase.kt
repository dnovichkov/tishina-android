package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.dmdp.tishina.core.domain.model.AppSettingsSnapshot
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/**
 * Combines [SettingsRepository.config] and [SettingsRepository.appearance] into
 * a single [AppSettingsSnapshot] flow so subscribers (`SettingsViewModel`,
 * `MeasureViewModel`, `AppViewModel`) avoid duplicating combine boilerplate.
 *
 * Emits whenever either upstream changes.
 */
class ObserveAppSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettingsSnapshot> =
        combine(repository.config, repository.appearance) { config, appearance ->
            AppSettingsSnapshot(config, appearance)
        }
}
