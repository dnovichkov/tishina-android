package ru.dmdp.tishina.feature.history

import androidx.annotation.StringRes
import ru.dmdp.tishina.core.domain.model.ExportFilter

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

    /**
     * Open the system Storage Access Framework `CreateDocument` picker (FR-20). The screen
     * captures this effect via a `rememberLauncherForActivityResult(CreateDocument("text/csv"))`
     * launcher; the returned URI is fed back as [HistoryUiEvent.ExportFileSelected]. The
     * [suggestedName] is the dated default filename (`tishina-history-YYYY-MM-DD.csv`); the
     * [filter] echoes the originating [HistoryUiEvent.ExportRequested] so the screen can
     * carry it through the picker round-trip.
     */
    data class LaunchSafPicker(val suggestedName: String, val filter: ExportFilter) : HistoryUiEffect

    /**
     * Show a localized snackbar confirming a successful CSV export. [rowCount] is the number
     * of measurement rows written (header excluded) so the screen can format a pluralized
     * confirmation message ("Exported 3 measurements").
     */
    data class ShowExportSuccessSnackbar(val rowCount: Int) : HistoryUiEffect

    /** Generic failure snackbar — exporter threw or returned a null OutputStream. */
    data object ShowExportFailedSnackbar : HistoryUiEffect
}
