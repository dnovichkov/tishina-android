package ru.dmdp.tishina.core.domain.model

/**
 * Application version derived from `BuildConfig.VERSION_NAME` / `VERSION_CODE` at the
 * `:app` boundary (FR-21 AboutScreen header).
 *
 * Lives in `:core:domain` so the Hilt graph can inject it into both `:feature:about`
 * UI and any future `:feature:settings` "About this app" row without dragging in an
 * Android-specific dependency on `BuildConfig`. The provider lives in `:core:data`.
 *
 * No `displayName` helper is exposed: human-readable formatting is done in the UI layer
 * via `stringResource(R.string.about_version_format, ...)` so the "build"/"сборка" word
 * stays localised (NFR-17 / NFR-18). Keeping the model UI-string-free also lets domain
 * tests run without Android resources.
 *
 * @property versionName Marketing version, e.g. `"0.1.0-foundation"`. May be blank when
 *           `PackageManager.NameNotFoundException` forces a fallback path — the UI
 *           branches on `versionName.isBlank()` to drop the empty prefix.
 * @property versionCode Monotonically increasing build number for the Play Store.
 */
data class AppVersion(val versionName: String, val versionCode: Int)
