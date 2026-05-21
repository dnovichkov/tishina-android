package ru.dmdp.tishina.feature.settings

import androidx.annotation.StringRes
import ru.dmdp.tishina.core.domain.model.AppLocale

/**
 * One-shot side effects emitted by [SettingsViewModel].
 *
 * Delivered through `Channel.BUFFERED` (see `effects` in the ViewModel) so a
 * rotation between emission and collection does not drop them. A `StateFlow`
 * would replay the latest value on every resubscribe, which would double-fire
 * snackbars and re-apply locales unexpectedly.
 */
sealed interface SettingsUiEffect {
    /**
     * Surface a transient message — the view layer routes [messageRes] through
     * a `SnackbarHostState`.
     */
    data class ShowSnackbar(@StringRes val messageRes: Int) : SettingsUiEffect

    /**
     * Pass-through signal for `MainActivity` (Task 7) to call
     * `AppCompatDelegate.setApplicationLocales`. The ViewModel stays
     * platform-agnostic — it persists the choice and announces "please apply".
     */
    data class ApplyAppLocale(val locale: AppLocale) : SettingsUiEffect
}
