package ru.dmdp.tishina.feature.history

import ru.dmdp.tishina.core.domain.model.MeasurementSummary

/**
 * Immutable Compose-facing snapshot for the History screen (FR-8 / FR-9).
 *
 * `loading=true` is the initial value before the repository Flow emits anything; once we
 * receive an emission (even an empty list) we flip it to false. This distinction matters
 * because an empty list with `loading=false` means "definitely empty → show empty state",
 * while `loading=true` keeps the screen quiet during initial inflation.
 *
 * `pendingUndoId` carries the id of the most recent soft-delete so the screen can render
 * Snackbar feedback / drive Undo without re-deriving it from effects.
 */
data class HistoryUiState(val items: List<MeasurementSummary> = emptyList(), val loading: Boolean = true, val pendingUndoId: Long? = null)
