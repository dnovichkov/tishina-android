package ru.dmdp.tishina.core.testing.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

/**
 * In-memory [SettingsRepository] backed by two [MutableStateFlow]s — one per
 * observable surface — so tests can drive `MeasureViewModel`,
 * `SettingsViewModel` and theme observers without touching DataStore.
 *
 * The flows always emit the current value to new subscribers (hot StateFlow
 * semantics), which mirrors the conflated behavior of the real DataStore-backed
 * implementation arriving in `:core:data` (Phase 4 Task 2).
 *
 * Tests pre-populate state via [seed]; setters then publish updates the same
 * way the real impl will.
 */
open class FakeSettingsRepository(
    initialConfig: MeasurementConfig = MeasurementConfig(),
    initialAppearance: AppearanceSettings = AppearanceSettings(),
) : SettingsRepository {

    private val configState = MutableStateFlow(initialConfig)
    private val appearanceState = MutableStateFlow(initialAppearance)

    override val config: Flow<MeasurementConfig> = configState
    override val appearance: Flow<AppearanceSettings> = appearanceState

    override suspend fun updateCalibrationOffset(db: Float) {
        configState.update { it.copy(calibrationOffsetDb = db) }
    }

    override suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting) {
        configState.update { it.copy(frequencyWeighting = weighting) }
    }

    override suspend fun updateTimeWeighting(weighting: TimeWeighting) {
        configState.update { it.copy(timeWeighting = weighting) }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        appearanceState.update { it.copy(themeMode = mode) }
    }

    override suspend fun updateDynamicColors(enabled: Boolean) {
        appearanceState.update { it.copy(dynamicColors = enabled) }
    }

    override suspend fun updateAppLocale(locale: AppLocale) {
        appearanceState.update { it.copy(locale = locale) }
    }

    override suspend fun resetCalibration() {
        configState.update { it.copy(calibrationOffsetDb = 0f) }
    }

    /**
     * Replaces the in-memory state without going through a suspend setter.
     * Useful in test setup blocks where the test wants a starting snapshot.
     */
    fun seed(
        config: MeasurementConfig? = null,
        appearance: AppearanceSettings? = null,
    ) {
        if (config != null) configState.value = config
        if (appearance != null) appearanceState.value = appearance
    }

    /** Direct read of current config — convenient for assertions. */
    fun currentConfig(): MeasurementConfig = configState.value

    /** Direct read of current appearance — convenient for assertions. */
    fun currentAppearance(): AppearanceSettings = appearanceState.value
}
