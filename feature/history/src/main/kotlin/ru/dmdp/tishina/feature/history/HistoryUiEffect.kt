package ru.dmdp.tishina.feature.history

import androidx.annotation.StringRes

/**
 * One-shot side effects from the History ViewModel to the screen.
 *
 * Undo (single and bulk) is intentionally NOT an effect here: the Undo affordance is tied
 * to the VM's `pendingUndoId` / `pendingBulkUndoCount` state, which survives configuration
 * changes (rotation, theme switch). A one-shot effect would be consumed by the previous
 * Composition and never replay onto the recreated screen, while the VM's commit timer keeps
 * running — silently committing the delete without a usable Undo button. The screen drives
 * the snackbar from state instead so re-entering the screen mid-window reattaches the
 * affordance.
 *
 * `ShowErrorSnackbar` stays as an effect because it's genuinely one-shot — losing a
 * "Couldn't delete" toast on rotation is benign (the row reappeared from the soft-delete
 * shadow already; the user can see that), unlike losing the actionable Undo button.
 */
sealed interface HistoryUiEffect {
    data class ShowErrorSnackbar(@StringRes val messageRes: Int) : HistoryUiEffect
}
