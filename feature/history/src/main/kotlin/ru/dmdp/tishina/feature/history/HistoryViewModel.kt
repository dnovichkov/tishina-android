package ru.dmdp.tishina.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.ExportHistoryUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

/**
 * Source of truth for the History screen (FR-8…FR-12).
 *
 * # Soft-delete strategy
 *
 * Two parallel soft-delete channels share the same `softDeletedIds` visibility shadow:
 *
 *  - **Single (FR-11)** — one swipe → `pendingUndoId: Long?` + `pendingDeleteJob` timer →
 *    `deleteMeasurement(id)` at the 5 s mark. Driven by [HistoryUiEvent.DeleteRequested] /
 *    [HistoryUiEvent.UndoConfirmed].
 *
 *  - **Bulk (FR-12)** — multi-select → `pendingBulkIds: Set<Long>` + `pendingBulkDeleteJob` →
 *    `deleteMeasurements(ids)` at the 5 s mark. Driven by [HistoryUiEvent.BulkDeleteRequested]
 *    / [HistoryUiEvent.BulkUndoConfirmed].
 *
 * Each channel keeps the same two-phase invariants documented for the single-only design:
 *  1. On schedule we add the id(s) to [softDeletedIds] — the combined state filters them out
 *     of the visible list immediately. Rows stay on disk.
 *  2. A 5 s timer drops its own job handle ([pendingDeleteJob] / [pendingBulkDeleteJob]) the
 *     instant [delay] elapses, then atomically claims its ids in [committingIds] /
 *     [committingBulkIds] and enters the suspending repository call. This makes the commit
 *     phase uncancellable from outside without losing structured cancellation.
 *  3. Undo cancels the timer (if still pending) and lifts the soft-delete shadow.
 *
 * # Cross-channel orphan-commit (FR-11 ↔ FR-12)
 *
 * Each new soft-delete request — single OR bulk — eagerly commits any pending soft-delete of
 * the OTHER kind ([commitOrphanedSingle] + [commitOrphanedBulk]) before scheduling its own.
 * This keeps exactly one timer per channel and never leaves stale entries in the soft-delete
 * shadow. Concretely:
 *
 *  - single pending + bulk request   → single committed via [deleteMeasurement] right now, bulk
 *    timer starts.
 *  - bulk pending + single swipe     → bulk committed via [deleteMeasurements] right now, single
 *    timer starts.
 *  - bulk pending + new bulk request → previous bulk committed via [deleteMeasurements] right
 *    now, new bulk timer starts.
 *
 * An "orphan committed" delete reaches the repository exactly once — [commitOrphanedSingle]
 * / [commitOrphanedBulk] skip ids already in their respective `committingIds` sets so an
 * in-flight Room IO can't be cancelled mid-transaction and never gets a duplicate retry.
 *
 * Bulk Undo therefore only ever resurrects the LATEST bulk; the previously orphaned
 * single/bulk is already gone. This mirrors FR-11's "latest-write-wins" rule for consecutive
 * single swipes.
 *
 * # Selection mode (FR-12)
 *
 * `selectionMode` and `selectedIds` are packaged together in one internal [InternalSelection]
 * StateFlow so the 5-arg [combine] doesn't overflow into the array variant. The state
 * pipeline filters [InternalSelection.ids] against `softDeletedIds` as defense-in-depth — if
 * a race makes a soft-deleted id slip into the selection (e.g. concurrent long-press + swipe),
 * the action-bar count still matches the visible cards.
 *
 * [HistoryUiEvent.SelectAll] launches into [viewModelScope] and reads
 * `getMeasurements().first()` directly rather than `state.value.items`: the latter is
 * `WhileSubscribed`-gated and reads `initialState` (empty items) when no UI is currently
 * collecting `state`, which would silently turn `SelectAll` into a no-op for any caller
 * (notably tests) that doesn't pre-subscribe.
 *
 * # softDeletedIds reconciler (shared between channels)
 *
 * The shadow shrinks lazily against the upstream snapshot: once Room emits a list without an
 * id, the [transform] block intersects the shadow with the new visible-id set. This avoids
 * the async-invalidation race where clearing the shadow synchronously inside the commit
 * function (right after [deleteMeasurement] returns) would flash the row back into the
 * visible list for one frame — combine would re-evaluate with `all = pre-delete list` and
 * `deleted = ∅` because Room's InvalidationTracker delivers the post-delete emission on a
 * background executor.
 *
 * `pendingBulkIds` is cleared eagerly inside [commitBulkDelete] (before the suspending
 * deleteAll call) so the bulk snackbar dismisses at the 5 s mark regardless of how slow the
 * Room delete IO is — same property [pendingUndoId] has via [commitDeleteInternal].
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getMeasurements: GetMeasurementsUseCase,
    private val deleteMeasurement: DeleteMeasurementUseCase,
    private val deleteMeasurements: DeleteMeasurementsUseCase,
    private val exportHistory: ExportHistoryUseCase,
    @Named(NOW_MILLIS_PROVIDER) private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val softDeletedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingUndoId = MutableStateFlow<Long?>(null)
    private val pendingBulkIds = MutableStateFlow<Set<Long>>(emptySet())
    private val internalSelection = MutableStateFlow(InternalSelection(mode = false, ids = emptySet()))
    private val initialState = HistoryUiState(loading = true)

    private val effectChannel = Channel<HistoryUiEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryUiEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<HistoryUiState> = combine(
        // Reconciler: drop ids that are no longer in the upstream snapshot from the shadow.
        // ORDER MATTERS — see class kdoc for the Main.immediate reentrancy argument.
        getMeasurements().transform { all ->
            emit(all)
            val visibleIds = all.mapTo(mutableSetOf()) { it.id }
            softDeletedIds.update { current ->
                if (current.isEmpty()) current else current.intersect(visibleIds)
            }
        },
        softDeletedIds,
        pendingUndoId,
        pendingBulkIds,
        internalSelection,
    ) { all, deleted, undoId, bulkIds, selection ->
        // Defense-in-depth: filter soft-deleted ids out of the active selection so the
        // action-bar count never includes a card the user can't see. UI normally prevents
        // this (long-press is disabled on items mid-swipe), but races slip through.
        val effectiveSelection = if (selection.ids.isEmpty()) selection.ids else selection.ids - deleted
        HistoryUiState(
            items = all.filter { it.id !in deleted },
            loading = false,
            loadFailed = false,
            pendingUndoId = undoId,
            pendingBulkUndoCount = bulkIds.size,
            selectionMode = selection.mode,
            selectedIds = effectiveSelection,
        )
    }
        .catch {
            effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_load_failed))
            emit(
                HistoryUiState(
                    items = emptyList(),
                    loading = false,
                    loadFailed = true,
                    pendingUndoId = null,
                    pendingBulkUndoCount = 0,
                    selectionMode = false,
                    selectedIds = emptySet(),
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_KEEPALIVE_MS), initialState)

    private var pendingDeleteJob: Job? = null
    private var pendingBulkDeleteJob: Job? = null

    // Ids whose commit{,Bulk}Delete is currently in flight (post-timer, suspended inside the
    // repository call). Tracked so orphan-commits don't launch a duplicate retry for an id
    // that's already being committed.
    private val committingIds = mutableSetOf<Long>()
    private val committingBulkIds = mutableSetOf<Long>()

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.DeleteRequested -> scheduleDelete(event.id)
            HistoryUiEvent.UndoConfirmed -> cancelPendingDelete()
            is HistoryUiEvent.EnterSelectionMode -> enterSelectionMode(event.initialId)
            is HistoryUiEvent.ToggleSelection -> toggleSelection(event.id)
            HistoryUiEvent.SelectAll -> selectAllVisible()
            HistoryUiEvent.ClearSelection -> clearSelection()
            HistoryUiEvent.ExitSelectionMode -> exitSelectionMode()
            HistoryUiEvent.BulkDeleteRequested -> scheduleBulkDelete()
            HistoryUiEvent.BulkUndoConfirmed -> cancelPendingBulkDelete()
            is HistoryUiEvent.ExportRequested -> launchSafPicker(event.filter)
            is HistoryUiEvent.ExportFileSelected -> runExport(event.targetUriString, event.filter)
            HistoryUiEvent.ExportCancelled -> Unit
        }
    }

    private fun launchSafPicker(filter: ExportFilter) {
        // The suggested filename is dated UTC — same date everywhere regardless of the user's
        // timezone, which matches how the SAF picker shows it. We avoid `LocalDate.now()` and
        // route through the injected `nowMillisProvider` so tests can pin a fixed timestamp.
        val isoDate = DateTimeFormatter.ISO_LOCAL_DATE.format(
            Instant.ofEpochMilli(nowMillisProvider()).atOffset(ZoneOffset.UTC).toLocalDate(),
        )
        val suggestedName = "tishina-history-$isoDate.csv"
        effectChannel.trySend(HistoryUiEffect.LaunchSafPicker(suggestedName, filter))
    }

    private fun runExport(targetUriString: String, filter: ExportFilter) {
        viewModelScope.launch {
            val result = exportHistory(targetUriString, filter)
            val rowCount = result.getOrNull()
            if (result.isSuccess && rowCount != null) {
                effectChannel.trySend(HistoryUiEffect.ShowExportSuccessSnackbar(rowCount))
            } else {
                effectChannel.trySend(HistoryUiEffect.ShowExportFailedSnackbar)
            }
        }
    }

    private fun scheduleDelete(id: Long) {
        // Idempotent re-swipe of the same id.
        if (id == pendingUndoId.value && pendingDeleteJob != null) return

        // Latest-write-wins across BOTH channels. We orphan-commit any other pending single
        // (≠ this id) and any pending bulk before scheduling the new single. Failing to
        // commit the bulk here would leave its ids stranded in the soft-delete shadow with
        // no timer to ever clear them.
        commitOrphanedSingle(exceptId = id)
        commitOrphanedBulk()

        softDeletedIds.update { it + id }
        pendingUndoId.value = id
        // Drop the just-soft-deleted id from any active selection so the action bar count
        // stays in sync with the visible list (defense against simultaneous long-press + swipe).
        internalSelection.update { it.copy(ids = it.ids - id) }
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            // Atomic transition out of the cancellable "timer" phase — see class kdoc.
            pendingDeleteJob = null
            commitDelete(id)
        }
    }

    private fun scheduleBulkDelete() {
        val toDelete = internalSelection.value.ids
        if (toDelete.isEmpty()) {
            // Empty bulk-delete is a contract violation — UI must hide the "Delete N" button
            // when nothing is selected. Defense-in-depth surfaces a localized snackbar.
            effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_bulk_no_selection))
            return
        }

        commitOrphanedSingle()
        commitOrphanedBulk()

        softDeletedIds.update { it + toDelete }
        pendingBulkIds.value = toDelete
        // Auto-exit selection mode: the bulk Undo snackbar takes over as the user's affordance.
        // Leaving the action bar visible while a snackbar advertises Undo would be confusing.
        internalSelection.value = InternalSelection(mode = false, ids = emptySet())
        pendingBulkDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            pendingBulkDeleteJob = null
            commitBulkDelete(toDelete)
        }
    }

    private fun commitOrphanedSingle(exceptId: Long? = null) {
        val id = pendingUndoId.value ?: return
        if (id == exceptId) return
        // Already committing → owned by its own coroutine. Re-launching would duplicate IO
        // and could double-emit an error snackbar.
        if (id in committingIds) return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        // Dismiss the single Undo snackbar immediately; the orphan commit fires through
        // commitDelete (which would also null pendingUndoId, but we do it eagerly here so
        // the dismissal is observable on the next combine emission).
        pendingUndoId.value = null
        viewModelScope.launch {
            commitDelete(id)
        }
    }

    private fun commitOrphanedBulk() {
        val snapshot = pendingBulkIds.value
        if (snapshot.isEmpty()) return
        // If any id is already being committed, an in-flight commit owns the snapshot —
        // pendingBulkIds should already be empty in that case; defensive no-op.
        if (snapshot.any { it in committingBulkIds }) return
        pendingBulkDeleteJob?.cancel()
        pendingBulkDeleteJob = null
        pendingBulkIds.value = emptySet()
        viewModelScope.launch {
            commitBulkDelete(snapshot)
        }
    }

    private suspend fun commitDelete(id: Long) {
        committingIds.add(id)
        try {
            commitDeleteInternal(id)
        } finally {
            committingIds.remove(id)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitDeleteInternal(id: Long) {
        if (pendingUndoId.value == id) pendingUndoId.value = null
        val failure: Throwable? = try {
            deleteMeasurement(id)
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throwable
        }
        if (failure != null) {
            softDeletedIds.update { it - id }
            effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_delete_failed))
        }
    }

    private suspend fun commitBulkDelete(ids: Set<Long>) {
        committingBulkIds.addAll(ids)
        try {
            // Clear the snackbar state at the start of commit so the Undo affordance
            // disappears exactly at the 5 s mark, not after Room IO completes. Same property
            // [commitDeleteInternal] enforces for pendingUndoId.
            if (pendingBulkIds.value == ids) pendingBulkIds.value = emptySet()
            val result = deleteMeasurements(ids)
            if (result.isFailure) {
                softDeletedIds.update { it - ids }
                effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_bulk_delete_failed))
            }
        } finally {
            committingBulkIds.removeAll(ids)
        }
    }

    private fun cancelPendingDelete() {
        val id = pendingUndoId.value ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        softDeletedIds.update { it - id }
        pendingUndoId.value = null
    }

    private fun cancelPendingBulkDelete() {
        val snapshot = pendingBulkIds.value
        if (snapshot.isEmpty()) return
        pendingBulkDeleteJob?.cancel()
        pendingBulkDeleteJob = null
        softDeletedIds.update { it - snapshot }
        pendingBulkIds.value = emptySet()
    }

    private fun enterSelectionMode(initialId: Long?) {
        val seedId = initialId?.takeUnless { it in softDeletedIds.value }
        // Defense-in-depth: if a race delivers EnterSelectionMode while the user is already in
        // selection mode (UI normally gates this — long-press is wired only when
        // selectionMode == false), MERGE the seedId into the existing selection instead of
        // replacing it. Clobber-semantics would silently discard every previously-toggled card,
        // mirroring a data-loss UX bug. Matches the defense-in-depth posture of scheduleDelete,
        // which strips ids from the active selection rather than failing loudly.
        internalSelection.update { current ->
            if (current.mode) {
                if (seedId != null) current.copy(ids = current.ids + seedId) else current
            } else {
                InternalSelection(
                    mode = true,
                    ids = if (seedId != null) setOf(seedId) else emptySet(),
                )
            }
        }
    }

    private fun toggleSelection(id: Long) {
        val current = internalSelection.value
        if (!current.mode) return
        // Soft-deleted ids are invisible to the user — toggling them would create a phantom
        // selection. Also prevents the soft-deleted id from ending up in selectedIds (the
        // state-level filter would drop it, but keeping internal state consistent is cleaner).
        if (id in softDeletedIds.value) return
        internalSelection.value = current.copy(
            ids = if (id in current.ids) current.ids - id else current.ids + id,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun selectAllVisible() {
        if (!internalSelection.value.mode) return
        // We read upstream + softDeletedIds rather than `state.value.items` because the latter
        // is gated by WhileSubscribed: with no UI collector the StateFlow returns
        // [initialState] (empty items) and SelectAll silently becomes a no-op. Reading the
        // upstream directly avoids that dependency on subscriber count — at the cost of a
        // single launched coroutine to bridge the suspending .first() into the non-suspend
        // event handler.
        //
        // The main state pipeline catches upstream failures via .catch (above), but THIS
        // coroutine is a separate child of viewModelScope — without a try/catch, a Room IO
        // failure during SelectAll would propagate to CoroutineExceptionHandler and crash.
        // Mirror [commitDeleteInternal]: capture the throwable into a typed nullable so detekt
        // sees it observed (no SwallowedException), then surface the same generic snackbar the
        // main pipeline uses so the UI degrades gracefully instead of crashing.
        viewModelScope.launch {
            var failure: Throwable? = null
            val all = try {
                getMeasurements().first()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                failure = throwable
                null
            }
            if (failure != null || all == null) {
                effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_load_failed))
                return@launch
            }
            val deleted = softDeletedIds.value
            val visibleIds = all.mapNotNullTo(mutableSetOf()) { item ->
                item.id.takeUnless { id -> id in deleted }
            }
            // Re-check selection mode — user might have exited between the long-press / SelectAll
            // tap and the coroutine resuming (config change, fast Cancel tap).
            internalSelection.update { current ->
                if (current.mode) current.copy(ids = visibleIds) else current
            }
        }
    }

    private fun clearSelection() {
        val current = internalSelection.value
        if (!current.mode) return
        internalSelection.value = current.copy(ids = emptySet())
    }

    private fun exitSelectionMode() {
        internalSelection.value = InternalSelection(mode = false, ids = emptySet())
    }

    private data class InternalSelection(val mode: Boolean, val ids: Set<Long>)

    companion object {
        /** Window during which a soft-deleted row can be restored via Undo. Public so the
         *  screen can size its own animations / accessibility hints against the same value. */
        const val UNDO_WINDOW_MS: Long = 5_000L

        /** Keep the upstream live ~5 s after the last collector to survive config changes
         *  (rotation, theme switch) without re-querying Room. */
        private const val STATE_KEEPALIVE_MS = 5_000L

        /**
         * `@Named` qualifier for the `() -> Long` clock injected into the ViewModel. Hilt cannot
         * disambiguate function types by signature alone, so we tag the binding here and in the
         * matching `@Provides` in [ru.dmdp.tishina.feature.history.di.HistoryUseCaseModule].
         */
        const val NOW_MILLIS_PROVIDER: String = "HistoryViewModel.nowMillisProvider"
    }
}
