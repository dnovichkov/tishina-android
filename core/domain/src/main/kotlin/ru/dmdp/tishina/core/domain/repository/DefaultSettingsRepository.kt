package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Phase-2 stub: always reports the default [MeasurementConfig] and silently
 * ignores setters. Replaced in Phase 4 by a DataStore-backed implementation.
 *
 * Kept in `:core:domain` (pure Kotlin) so that `:app` can wire it into the Hilt
 * graph without dragging in any Android-only persistence dependency yet.
 */
class DefaultSettingsRepository : SettingsRepository {

    override val config: Flow<MeasurementConfig> = flowOf(MeasurementConfig())

    override suspend fun updateCalibrationOffset(db: Float) = Unit

    override suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting) = Unit

    override suspend fun updateTimeWeighting(weighting: TimeWeighting) = Unit
}
