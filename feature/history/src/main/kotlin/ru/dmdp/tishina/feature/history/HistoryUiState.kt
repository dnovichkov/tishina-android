package ru.dmdp.tishina.feature.history

import ru.dmdp.tishina.core.domain.model.MeasurementSummary

/**
 * Immutable Compose-facing snapshot for the History screen (FR-8 / FR-9).
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
 * `pendingUndoId` carries the id of the most recent soft-delete so the screen can render
 * Snackbar feedback / drive Undo without re-deriving it from effects.
 */
data class HistoryUiState(
    val items: List<MeasurementSummary> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val pendingUndoId: Long? = null,
)
