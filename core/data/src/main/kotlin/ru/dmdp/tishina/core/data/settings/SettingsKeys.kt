package ru.dmdp.tishina.core.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Stable [androidx.datastore.preferences.core.Preferences] keys for the user-tunable
 * settings persisted by [SettingsRepositoryImpl].
 *
 * **Stability contract:** these strings are part of the on-disk schema. Renaming a key
 * orphans existing data and silently downgrades the user to defaults on next launch.
 * Add a migration block in [SettingsRepositoryImpl] if a rename is unavoidable — never
 * just edit the string here.
 *
 * Enum values are stored as their [Enum.name] (e.g. `"Dark"`, `"SLOW"`) and parsed back
 * defensively in [SettingsRepositoryImpl] so a downgrade from a future version with a
 * new enum variant falls back to the default rather than crashing.
 */
internal object SettingsKeys {
    val CALIBRATION_OFFSET_DB = floatPreferencesKey("calibration_offset_db")
    val FREQUENCY_WEIGHTING = stringPreferencesKey("frequency_weighting")
    val TIME_WEIGHTING = stringPreferencesKey("time_weighting")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    val APP_LOCALE = stringPreferencesKey("app_locale")
}
