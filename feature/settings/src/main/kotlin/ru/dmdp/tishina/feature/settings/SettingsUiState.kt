package ru.dmdp.tishina.feature.settings

import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Immutable snapshot rendered by `SettingsScreen`.
 *
 * Mirrors `AppSettingsSnapshot` from `:core:domain` but flattens its two
 * sub-objects into top-level fields so Compose previews and tests can build
 * the state without depending on `MeasurementConfig` / `AppearanceSettings`.
 *
 * `loading = true` only on the very first composition, before the upstream
 * `combine(config, appearance)` emits — switches to `false` after the first
 * snapshot lands so the UI can swap a progress indicator for the controls.
 */
data class SettingsUiState(
    val calibrationOffsetDb: Float = 0f,
    val timeWeighting: TimeWeighting = TimeWeighting.FAST,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColors: Boolean = true,
    val locale: AppLocale = AppLocale.System,
    val loading: Boolean = true,
)
