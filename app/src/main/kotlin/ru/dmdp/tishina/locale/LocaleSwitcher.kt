package ru.dmdp.tishina.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ru.dmdp.tishina.core.domain.model.AppLocale

/**
 * FR-18 — translates the persisted [AppLocale] preference into the
 * `LocaleListCompat` shape `AppCompatDelegate.setApplicationLocales`
 * expects and hands it to the framework.
 *
 * Kept as a `object` (no Hilt) because:
 *  - it is platform-only glue (AndroidX AppCompat + LocaleListCompat),
 *  - `MainActivity` calls it as a method reference (`LocaleSwitcher::apply`)
 *    in response to `SettingsUiEffect.ApplyAppLocale`,
 *  - the per-app locale store lives on `AppCompatDelegate`, which is itself
 *    process-singleton — there is no per-component state worth scoping.
 *
 * Calling [apply] triggers `Activity.recreate()` automatically on Android 12-,
 * and on Android 13+ the system reads the value from `LocaleManager` so the
 * change survives process death without a manual re-apply on next launch.
 *
 * Idempotency: re-applying the same locale is a no-op inside AppCompat — the
 * delegate compares the new list to the existing one before triggering any
 * recreate. Tests assert this in `LocaleSwitcherTest`.
 */
object LocaleSwitcher {

    /**
     * Persist [locale] as the active per-app UI language. `AppLocale.System`
     * clears the override so the OS locale list governs the app again.
     */
    fun apply(locale: AppLocale) {
        AppCompatDelegate.setApplicationLocales(toLocaleListCompat(locale))
    }

    /**
     * Pure mapping from [AppLocale] to [LocaleListCompat]. Exposed (instead of
     * inlined inside [apply]) so unit tests can assert the mapping without
     * touching `AppCompatDelegate`'s global state.
     */
    fun toLocaleListCompat(locale: AppLocale): LocaleListCompat = when (locale) {
        AppLocale.System -> LocaleListCompat.getEmptyLocaleList()
        AppLocale.Russian, AppLocale.English -> LocaleListCompat.forLanguageTags(locale.tag)
    }
}
