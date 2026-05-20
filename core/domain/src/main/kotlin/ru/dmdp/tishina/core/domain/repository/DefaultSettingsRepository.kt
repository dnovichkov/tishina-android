package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Pure-Kotlin stub that always reports defaults and silently ignores setters.
 *
 * Kept in `:core:domain` so modules that can't / shouldn't depend on `:core:data`
 * (e.g. `:core:designsystem` screenshot tests, isolated module tests) still have
 * a no-op `SettingsRepository` to wire into their Hilt-test or Compose previews.
 * Production code in `:app` uses `SettingsRepositoryImpl` from `:core:data`.
 */
class DefaultSettingsRepository : SettingsRepository {

    override val config: Flow<MeasurementConfig> = flowOf(MeasurementConfig())

    override val appearance: Flow<AppearanceSettings> = flowOf(AppearanceSettings())

    override suspend fun updateCalibrationOffset(db: Float) = Unit

    override suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting) = Unit

    override suspend fun updateTimeWeighting(weighting: TimeWeighting) = Unit

    override suspend fun updateThemeMode(mode: ThemeMode) = Unit

    override suspend fun updateDynamicColors(enabled: Boolean) = Unit

    override suspend fun updateAppLocale(locale: AppLocale) = Unit

    override suspend fun resetCalibration() = Unit
}
