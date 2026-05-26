package ru.dmdp.tishina.feature.history

import ru.dmdp.tishina.core.domain.model.ExportFilter

/**
 * User intents on the History screen. The ViewModel maps each one to a state mutation +
 * optional one-shot effect (Snackbar etc.).
 */
sealed interface HistoryUiEvent {
    /** Triggered when a card is swiped past the dismiss threshold (FR-11). */
    data class DeleteRequested(val id: Long) : HistoryUiEvent

    /** Triggered by the "Undo" action on the single-delete snackbar, within 5 s of soft-delete. */
    data object UndoConfirmed : HistoryUiEvent

    /**
     * Enter multi-select mode (FR-12). When triggered by a long-press on a card the UI passes
     * the card's id via [initialId] so it becomes the first selection in one event round-trip —
     * otherwise the user would see the action bar empty for one frame after long-pressing.
     * A null [initialId] is valid for entering selection mode via a different affordance
     * (e.g. an explicit "Select" button in a context menu — not in MVP, but contract-safe).
     */
    data class EnterSelectionMode(val initialId: Long? = null) : HistoryUiEvent

    /**
     * Toggle a single id in the current selection. No-op outside selection mode and no-op
     * for ids that are currently soft-deleted (the UI hides them from the list, so toggling
     * them would create a "phantom selection" the user can't see or interact with).
     */
    data class ToggleSelection(val id: Long) : HistoryUiEvent

    /** Add every currently-visible measurement id to the selection. No-op outside selection mode. */
    data object SelectAll : HistoryUiEvent

    /**
     * Clear all selected ids but stay in selection mode. The action bar remains visible
     * with "0 selected" — this matches Material 3 multi-select patterns where ClearSelection
     * is distinct from ExitSelectionMode.
     */
    data object ClearSelection : HistoryUiEvent

    /** Leave selection mode entirely: selectionMode=false, selectedIds=emptySet(). */
    data object ExitSelectionMode : HistoryUiEvent

    /**
     * Trigger bulk soft-delete for all currently-selected ids (FR-12). Emits an error
     * snackbar effect and is a no-op when [HistoryUiState.selectedIds] is empty.
     */
    data object BulkDeleteRequested : HistoryUiEvent

    /** Triggered by the "Undo" action on the bulk-delete snackbar, within 5 s of soft-delete. */
    data object BulkUndoConfirmed : HistoryUiEvent

    /**
     * User asked to export the history (FR-20). The [filter] decides whether the export covers
     * the full history ([ExportFilter.All]) or just a hand-picked subset ([ExportFilter.ByIds],
     * raised from selection mode). The VM responds with [HistoryUiEffect.LaunchSafPicker] so
     * the screen can open the system Storage Access Framework `CreateDocument` picker.
     */
    data class ExportRequested(val filter: ExportFilter) : HistoryUiEvent

    /**
     * SAF returned a destination URI; trigger the actual export. [filter] is the same value the
     * matching [ExportRequested] carried — the screen forwards it through the
     * `rememberLauncherForActivityResult` callback. The VM runs [ExportHistoryUseCase] and emits
     * either [HistoryUiEffect.ShowExportSuccessSnackbar] or
     * [HistoryUiEffect.ShowExportFailedSnackbar].
     */
    data class ExportFileSelected(val targetUriString: String, val filter: ExportFilter) : HistoryUiEvent

    /**
     * SAF picker cancelled (Back / outside-tap). No-op for the ViewModel — the use-case never
     * runs, no snackbar is emitted. Kept as an explicit event so the screen can be exhaustive
     * about the picker callback paths and so future telemetry can hook into cancellations.
     */
    data object ExportCancelled : HistoryUiEvent
}
