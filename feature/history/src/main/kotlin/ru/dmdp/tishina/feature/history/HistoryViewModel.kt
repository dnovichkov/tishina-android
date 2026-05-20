package ru.dmdp.tishina.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import javax.inject.Inject

/**
 * Source of truth for the History screen (FR-8…FR-11).
 *
 * Soft-delete strategy for FR-11 Undo (chosen in plan over "delete + restore via save"):
 *
 *  1. On [HistoryUiEvent.DeleteRequested] we add the id to [softDeletedIds] — the combined
 *     state flow filters it out of the visible list immediately. The row stays on disk.
 *  2. A 5 s timer fires from [pendingDeleteJob]; when it elapses, we drop the id from the
 *     soft-deleted set and call [deleteMeasurement] for real.
 *  3. On [HistoryUiEvent.UndoConfirmed] we cancel the timer and clear the soft-deleted
 *     entry, restoring the id to the visible list without a Room round-trip.
 *
 * Edge case — two swipes in quick succession: the second [DeleteRequested] cancels the
 * first job *before* it can fire and replaces it with a fresh timer for the new id. The
 * already-soft-deleted id from the first swipe stays in [softDeletedIds] and is committed
 * immediately (via [commitOrphanedSoftDeletes]) so we never end up with two entries waiting
 * on the same job. Without this we'd lose Undo for the prior swipe AND keep the row visible
 * after the timer would have fired.
 *
 * `pendingUndoId` mirrors which id is currently undoable so the screen can render the
 * snackbar message without re-deriving it from the effect channel.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getMeasurements: GetMeasurementsUseCase,
    private val deleteMeasurement: DeleteMeasurementUseCase,
) : ViewModel() {

    private val softDeletedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingUndoId = MutableStateFlow<Long?>(null)
    private val initialState = HistoryUiState(loading = true)

    val state: StateFlow<HistoryUiState> = combine(
        getMeasurements(),
        softDeletedIds,
        pendingUndoId,
    ) { all, deleted, undoId ->
        HistoryUiState(
            items = all.filter { it.id !in deleted },
            loading = false,
            pendingUndoId = undoId,
        )
    }
        // Catch any upstream Room IO failure so the screen doesn't get pinned at loading=true.
        // We surface an empty list with loading=false — the empty-state CTA gives the user a
        // sensible recovery path (start a new measurement) rather than a blank pinned screen.
        .catch { emit(HistoryUiState(items = emptyList(), loading = false, pendingUndoId = null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_KEEPALIVE_MS), initialState)

    private val effectChannel = Channel<HistoryUiEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryUiEffect> = effectChannel.receiveAsFlow()

    private var pendingDeleteJob: Job? = null

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.DeleteRequested -> scheduleDelete(event.id)
            HistoryUiEvent.UndoConfirmed -> cancelPendingDelete()
        }
    }

    private fun scheduleDelete(id: Long) {
        // Latest-write-wins: if there is already a pending delete, commit it now (row was already
        // hidden, user already had their undo window) so we don't strand stale entries in the
        // soft-deleted set.
        commitOrphanedSoftDeletes(except = id)

        softDeletedIds.update { it + id }
        pendingUndoId.value = id
        effectChannel.trySend(
            HistoryUiEffect.ShowUndoSnackbar(
                messageRes = R.string.history_undo_snackbar_message,
                actionRes = R.string.history_undo_action,
            ),
        )
        pendingDeleteJob = viewModelScope.launch {
            delay(HistoryUiEffect.UNDO_WINDOW_MS)
            commitDelete(id)
        }
    }

    private fun commitOrphanedSoftDeletes(except: Long) {
        val orphans = softDeletedIds.value - except
        if (orphans.isEmpty()) return
        // Don't cancel here — let the actual commit hit; we just stop the OLD job from also doing
        // the same work after a delay.
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        viewModelScope.launch {
            orphans.forEach { id ->
                // Delete from Room first, then drop the soft-delete shadow. The reverse order
                // would create a one-frame window where the row reappears (because the combine
                // re-evaluates with the id no longer filtered, but Room's Flow hasn't yet
                // emitted the post-delete list).
                deleteMeasurement(id)
                softDeletedIds.update { it - id }
            }
        }
    }

    private suspend fun commitDelete(id: Long) {
        // Same ordering as commitOrphanedSoftDeletes — delete the row from Room, then clear the
        // soft-delete shadow. Room's Flow will emit the new list around the same time we drop
        // the shadow, so the filter stays consistent throughout.
        deleteMeasurement(id)
        softDeletedIds.update { it - id }
        if (pendingUndoId.value == id) pendingUndoId.value = null
    }

    private fun cancelPendingDelete() {
        val id = pendingUndoId.value ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        softDeletedIds.update { it - id }
        pendingUndoId.value = null
    }

    private companion object {
        /** Keep the upstream live ~5 s after the last collector to survive config changes
         *  (rotation, theme switch) without re-querying Room. */
        const val STATE_KEEPALIVE_MS = 5_000L
    }
}
