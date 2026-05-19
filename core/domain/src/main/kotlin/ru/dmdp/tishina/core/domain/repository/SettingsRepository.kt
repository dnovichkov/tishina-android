package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Persists the user-tunable measurement settings (frequency weighting, time
 * weighting, calibration offset).
 *
 * Phase 2 ships only the interface plus a stub that always returns defaults
 * (see `DefaultSettingsRepository`). The DataStore-backed implementation will
 * arrive in Phase 4 together with the Settings screen.
 */
interface SettingsRepository {

    /**
     * Hot flow that emits the current [MeasurementConfig] and re-emits on every
     * setter call. Consumers — `MeasureViewModel`, `SettingsViewModel` — collect
     * this and pass the value into [AudioRepository.samples].
     */
    val config: Flow<MeasurementConfig>

    /** Persists a new calibration offset in dB (e.g. `+3.5f`). */
    suspend fun updateCalibrationOffset(db: Float)

    /** Persists the frequency weighting choice (A / Z; C arrives in Phase 4). */
    suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting)

    /** Persists the RMS integration time constant (Fast / Slow). */
    suspend fun updateTimeWeighting(weighting: TimeWeighting)
}
