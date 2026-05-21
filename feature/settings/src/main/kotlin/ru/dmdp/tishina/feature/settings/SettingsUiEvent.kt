package ru.dmdp.tishina.feature.settings

import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Intents the Settings UI raises against [SettingsViewModel].
 *
 * Sealed so adding a new preference (C-weighting toggle in v1.1, OSS-licenses
 * link, etc.) becomes a compile-time check across `onEvent` and all current
 * UI tests.
 */
sealed interface SettingsUiEvent {
    /** FR-14 — slider drag finished; new offset in dB. May be out of range. */
    data class ChangeCalibration(val db: Float) : SettingsUiEvent

    /** FR-19 — reset button tapped. */
    data object ResetCalibration : SettingsUiEvent

    /** FR-16 — Fast (125 ms) / Slow (1 s) switch. */
    data class ChangeTimeWeighting(val weighting: TimeWeighting) : SettingsUiEvent

    /** FR-17 — System / Light / Dark chip. */
    data class ChangeThemeMode(val mode: ThemeMode) : SettingsUiEvent

    /** FR-17 — Material You toggle. */
    data class ChangeDynamicColors(val enabled: Boolean) : SettingsUiEvent

    /** FR-18 — System / Russian / English radio. */
    data class ChangeAppLocale(val locale: AppLocale) : SettingsUiEvent
}
