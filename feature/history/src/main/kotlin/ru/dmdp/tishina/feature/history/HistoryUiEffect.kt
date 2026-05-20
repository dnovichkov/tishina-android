package ru.dmdp.tishina.feature.history

import androidx.annotation.StringRes

/**
 * One-shot side effects from the History ViewModel to the screen.
 *
 * `ShowUndoSnackbar` carries the localized message resource and a duration in ms; the
 * screen wires it to [androidx.compose.material3.SnackbarHostState.showSnackbar] with
 * a 5 s timeout so the user has the same window the ViewModel uses internally before
 * committing the delete.
 */
sealed interface HistoryUiEffect {
    data class ShowUndoSnackbar(@StringRes val messageRes: Int, @StringRes val actionRes: Int, val durationMs: Long = UNDO_WINDOW_MS) :
        HistoryUiEffect

    companion object {
        /** Matches HistoryViewModel's internal timer window — the snackbar disappears at the
         *  same moment the soft-delete is committed. */
        const val UNDO_WINDOW_MS: Long = 5_000L
    }
}
