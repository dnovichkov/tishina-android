package ru.dmdp.tishina.feature.measure

import androidx.annotation.StringRes

/**
 * One-shot side effects the UI must surface to the system or user.
 *
 * Emitted through a `Channel.BUFFERED` so effects survive rotation (a
 * `StateFlow` would replay the latest value on resubscribe and double-fire
 * snackbars / settings intents).
 */
sealed interface MeasureUiEffect {
    /** Show the system RECORD_AUDIO request dialog. */
    data object RequestPermission : MeasureUiEffect

    /**
     * Surface a transient message to the user. The view layer maps
     * [messageRes] through its `SnackbarHostState`.
     */
    data class ShowSnackbar(@StringRes val messageRes: Int) : MeasureUiEffect

    /**
     * Route the user to the app's system settings page so they can re-enable
     * RECORD_AUDIO after a permanent denial.
     */
    data object OpenAppSettings : MeasureUiEffect
}
