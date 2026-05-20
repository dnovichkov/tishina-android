package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Persists user-tunable settings.
 *
 * Two independent flows are exposed so consumers subscribe only to what they need:
 *  - [config] feeds the audio pipeline (`MeasureViewModel`);
 *  - [appearance] feeds theme/locale observers (`TishinaApp`, `MainActivity`).
 *
 * Phase 4 replaces the Phase-2 stub (`DefaultSettingsRepository`) with a
 * DataStore-backed implementation living in `:core:data`.
 */
interface SettingsRepository {

    /**
     * Hot flow that emits the current [MeasurementConfig] and re-emits on every
     * setter call. Consumers — `MeasureViewModel`, `SettingsViewModel` — collect
     * this and pass the value into [AudioRepository.samples].
     */
    val config: Flow<MeasurementConfig>

    /**
     * Hot flow that emits the current [AppearanceSettings] (theme, dynamic colors,
     * locale). Re-emits on each `update*` setter call.
     */
    val appearance: Flow<AppearanceSettings>

    /** Persists a new calibration offset in dB (e.g. `+3.5f`). */
    suspend fun updateCalibrationOffset(db: Float)

    /** Persists the frequency weighting choice (A / Z; C arrives post-MVP). */
    suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting)

    /** Persists the RMS integration time constant (Fast / Slow). */
    suspend fun updateTimeWeighting(weighting: TimeWeighting)

    /** Persists the theme mode (System / Light / Dark). FR-17. */
    suspend fun updateThemeMode(mode: ThemeMode)

    /** Persists the Material You dynamic-colors toggle. FR-17. */
    suspend fun updateDynamicColors(enabled: Boolean)

    /** Persists the application UI locale (System / Russian / English). FR-18. */
    suspend fun updateAppLocale(locale: AppLocale)

    /** FR-19: resets the calibration offset back to `0.0f`. */
    suspend fun resetCalibration()
}
