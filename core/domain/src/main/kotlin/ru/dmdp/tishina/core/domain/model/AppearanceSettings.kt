package ru.dmdp.tishina.core.domain.model

/**
 * Visual presentation preferences persisted independently from [MeasurementConfig].
 *
 * Defaults follow the system: `ThemeMode.System` honors `isSystemInDarkTheme`,
 * `dynamicColors = true` requests Material You wallpapers on API 31+, and
 * `AppLocale.System` lets `AppCompatDelegate` fall back to the OS locale list.
 *
 * @property themeMode FR-17 — System / Light / Dark.
 * @property dynamicColors FR-17 — opt into `dynamicLightColorScheme` / `dynamicDarkColorScheme`
 *   on Android 12+. Silently ignored on older API levels.
 * @property locale FR-18 — System / Russian / English.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColors: Boolean = true,
    val locale: AppLocale = AppLocale.System,
) {
    companion object {
        /** FR-14 lower bound for the calibration offset slider. */
        const val CALIBRATION_MIN_DB: Float = -20.0f

        /** FR-14 upper bound for the calibration offset slider. */
        const val CALIBRATION_MAX_DB: Float = 20.0f

        /** FR-14 step for the calibration slider (0.1 dB granularity). */
        const val CALIBRATION_STEP_DB: Float = 0.1f
    }
}

/**
 * FR-17 — light/dark theme selection. `System` follows `isSystemInDarkTheme()`.
 */
enum class ThemeMode {
    System,
    Light,
    Dark,
}

/**
 * FR-18 — application UI language.
 *
 * @property tag BCP-47 tag used with `AppCompatDelegate.setApplicationLocales`.
 *   `System` carries an empty tag meaning "follow OS locale list".
 */
enum class AppLocale(val tag: String) {
    System(""),
    Russian("ru"),
    English("en"),
}

/**
 * Combined snapshot for downstream consumers (`MeasureViewModel`, `TishinaApp`)
 * that want a single subscription instead of zipping two flows manually.
 */
data class AppSettingsSnapshot(
    val config: MeasurementConfig,
    val appearance: AppearanceSettings,
)
