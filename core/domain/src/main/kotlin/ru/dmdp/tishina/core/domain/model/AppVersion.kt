package ru.dmdp.tishina.core.domain.model

/**
 * Application version derived from `BuildConfig.VERSION_NAME` / `VERSION_CODE` at the
 * `:app` boundary (FR-21 AboutScreen header).
 *
 * Lives in `:core:domain` so the Hilt graph can inject it into both `:feature:about`
 * UI and any future `:feature:settings` "About this app" row without dragging in an
 * Android-specific dependency on `BuildConfig`. The provider lives in `:core:data`.
 *
 * @property versionName Marketing version, e.g. `"0.1.0-foundation"`.
 * @property versionCode Monotonically increasing build number for the Play Store.
 */
data class AppVersion(val versionName: String, val versionCode: Int) {

    /** Human-readable label rendered as a single line in the About header. */
    val displayName: String get() = "$versionName (build $versionCode)"
}
