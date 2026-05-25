package ru.dmdp.tishina.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.dmdp.tishina.core.data.di.IoDispatcher
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SettingsRepository] persisting both measurement config and
 * appearance preferences into a single Preferences file.
 *
 * **Two flows from one DataStore:** [config] and [appearance] both observe
 * `dataStore.data` and project into their respective domain models. Each call to
 * `dataStore.edit { ... }` re-emits the *entire* preferences snapshot, so both flows
 * receive every event — `distinctUntilChanged` prevents redundant downstream work
 * when a setter touches a key that the other flow doesn't care about.
 *
 * **Defensive enum parsing:** `Enum.valueOf("Unknown")` throws on a missing entry.
 * That's a real risk during downgrade (user installs a future version that adds
 * `ThemeMode.HighContrast`, then rolls back to this build). [parseOrDefault] catches
 * the IAE and falls back to the documented default so the user lands on a sane state
 * rather than crash-looping on launch. Mirrors the `corruptionHandler` strategy used
 * when the file itself is broken.
 *
 * **Thread discipline:** reads and writes use `flowOn(ioDispatcher)` / DataStore's
 * own internal dispatcher; the public API is suspend-friendly. Production wires
 * `Dispatchers.IO` via [IoDispatcher]; tests pass `UnconfinedTestDispatcher` directly
 * through the constructor.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    override val config: Flow<MeasurementConfig> = dataStore.data
        .map { prefs ->
            MeasurementConfig(
                frequencyWeighting = parseOrDefault(
                    prefs[SettingsKeys.FREQUENCY_WEIGHTING],
                    FrequencyWeighting.A,
                ) { FrequencyWeighting.valueOf(it) },
                timeWeighting = parseOrDefault(
                    prefs[SettingsKeys.TIME_WEIGHTING],
                    TimeWeighting.FAST,
                ) { TimeWeighting.valueOf(it) },
                calibrationOffsetDb = prefs[SettingsKeys.CALIBRATION_OFFSET_DB] ?: 0f,
            )
        }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    override val appearance: Flow<AppearanceSettings> = dataStore.data
        .map { prefs ->
            AppearanceSettings(
                themeMode = parseOrDefault(
                    prefs[SettingsKeys.THEME_MODE],
                    ThemeMode.System,
                ) { ThemeMode.valueOf(it) },
                dynamicColors = prefs[SettingsKeys.DYNAMIC_COLORS] ?: true,
                locale = parseOrDefault(
                    prefs[SettingsKeys.APP_LOCALE],
                    AppLocale.System,
                ) { AppLocale.valueOf(it) },
            )
        }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    override suspend fun updateCalibrationOffset(db: Float) {
        dataStore.edit { prefs -> prefs[SettingsKeys.CALIBRATION_OFFSET_DB] = db }
    }

    override suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting) {
        dataStore.edit { prefs -> prefs[SettingsKeys.FREQUENCY_WEIGHTING] = weighting.name }
    }

    override suspend fun updateTimeWeighting(weighting: TimeWeighting) {
        dataStore.edit { prefs -> prefs[SettingsKeys.TIME_WEIGHTING] = weighting.name }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[SettingsKeys.THEME_MODE] = mode.name }
    }

    override suspend fun updateDynamicColors(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.DYNAMIC_COLORS] = enabled }
    }

    override suspend fun updateAppLocale(locale: AppLocale) {
        dataStore.edit { prefs -> prefs[SettingsKeys.APP_LOCALE] = locale.name }
    }

    override suspend fun resetCalibration() {
        updateCalibrationOffset(0f)
    }

    /**
     * Maps a nullable stored string into an enum value or [default]. A `null` means
     * "never written" — return the default. A non-null string that doesn't resolve
     * via [decode] (downgrade / typo) is treated the same way so the UI never has to
     * special-case "I have a value but I don't understand it".
     *
     * Catches [IllegalArgumentException] specifically (the only failure mode
     * [Enum.valueOf] produces) rather than `runCatching`, which would silently swallow
     * a [kotlinx.coroutines.CancellationException] and break structured cancellation if
     * `decode` ever became suspending. Narrow catch keeps that contract honest.
     */
    private inline fun <T : Enum<T>> parseOrDefault(
        raw: String?,
        default: T,
        decode: (String) -> T,
    ): T {
        if (raw == null) return default
        return try {
            decode(raw)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
