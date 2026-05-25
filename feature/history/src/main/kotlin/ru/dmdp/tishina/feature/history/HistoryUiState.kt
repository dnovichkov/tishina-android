package ru.dmdp.tishina.feature.history

import ru.dmdp.tishina.core.domain.model.MeasurementSummary

/**
 * Immutable Compose-facing snapshot for the History screen (FR-8 / FR-9 / FR-12).
 *
 * `loading=true` is the initial value before the repository Flow emits anything; once we
 * receive an emission (even an empty list) we flip it to false. This distinction matters
 * because an empty list with `loading=false && !loadFailed` means "definitely empty → show
 * the empty-state CTA", while `loading=true` keeps the screen quiet during initial inflation.
 *
 * `loadFailed=true` means the upstream Room read threw — the list is unreadable, not empty.
 * The empty-state CTA would mislead the user ("we have nothing yet, start measuring") when
 * data actually exists on disk. Branch the screen on this flag to a distinct error state.
 *
 * `pendingUndoId` carries the id of the most recent SINGLE soft-delete so the screen can
 * render Snackbar feedback / drive Undo without re-deriving it from effects.
 *
 * `pendingBulkUndoCount` is the size of the current pending BULK soft-delete (FR-12). It
 * is zero when no bulk delete is pending. The screen renders a pluralized snackbar from
 * this count and resolves the Undo action to [HistoryUiEvent.BulkUndoConfirmed]. Single
 * and bulk Undo are surfaced through distinct VM state fields so the snackbar text
 * (singular vs pluralized) and the Undo callback are unambiguous — both can never be
 * non-zero at the same time because each new soft-delete kind orphan-commits the other.
 *
 * `selectionMode` toggles the screen into multi-select mode (FR-12). While true, taps on
 * cards toggle selection instead of navigating to Detail, and the screen renders the
 * selection action bar (Select all / Cancel / Delete N).
 *
 * `selectedIds` is the set of measurement ids that are currently checked in selection
 * mode. It is always a subset of the visible [items] (soft-deleted ids are filtered out
 * by the ViewModel's combine pipeline, see HistoryViewModel).
 */
data class HistoryUiState(
    val items: List<MeasurementSummary> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val pendingUndoId: Long? = null,
    val pendingBulkUndoCount: Int = 0,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)
