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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
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
 *  2. A 5 s timer fires from [pendingDeleteJob]; when [delay] elapses, the job clears its
 *     own [pendingDeleteJob] handle (so a later swipe can no longer cancel it) and proceeds
 *     to [commitDelete]. The id is also added to [committingIds] for the duration of the
 *     repository call so [commitOrphanedSoftDeletes] doesn't double-launch a retry while
 *     the original commit is in flight.
 *  3. On [HistoryUiEvent.UndoConfirmed] we cancel the timer (if it's still pending) and
 *     clear the soft-deleted entry, restoring the id to the visible list without a Room
 *     round-trip. Once the timer has fired, [pendingDeleteJob] is null and Undo is a no-op
 *     (which is what we want — the undo window has expired).
 *
 * Edge case — two swipes in quick succession: the second [DeleteRequested] cancels the
 * first job's *timer* (only the timer — never an in-flight commit) and replaces it with a
 * fresh timer for the new id. The already-soft-deleted id from the first swipe is committed
 * immediately (via [commitOrphanedSoftDeletes]) so we never end up with two entries waiting
 * on the same job. Without this we'd lose Undo for the prior swipe AND keep the row visible
 * after the timer would have fired.
 *
 * Edge case — re-swipe of the SAME id while its timer is still pending: idempotent no-op.
 * Without an explicit guard, `commitOrphanedSoftDeletes(except = id)` would see no orphans
 * (the id is excluded from the orphan set as `except`) and the old timer would never be
 * cancelled — but [pendingDeleteJob] would be overwritten with a fresh job, leaving the
 * original timer running in the background. A subsequent Undo would then only cancel the
 * latest job, and the orphaned first timer would still commit the delete after its 5 s.
 *
 * Why separate the timer and commit phases: if [pendingDeleteJob] still owned the in-flight
 * commit, a later swipe would cancel it mid-Room-transaction. Room would roll the
 * transaction back (cooperative cancellation), and the orphan retry would have to do the
 * delete again — wasted IO and a microsecond race window where a process kill between
 * cancel and retry could resurrect a row whose undo window has already expired. Clearing
 * the handle right after [delay] returns makes the commit uncancellable from outside;
 * [committingIds] keeps orphan retries from doubling up on the same id.
 *
 * Why softDeletedIds is reconciled against the upstream list instead of cleared in
 * [commitDelete]: Room's `InvalidationTracker` delivers post-delete emissions asynchronously
 * (on its background executor), so a synchronous `softDeletedIds.update { it - id }` right
 * after [deleteMeasurement] returns races with the upstream Flow — `combine` can still hold
 * the pre-delete list while the soft-delete shadow is already gone, flashing the row back
 * into the visible list for a frame. Instead we keep the shadow until the upstream emits
 * a snapshot that no longer contains the id, and only then drop it via the `transform` hook
 * in the [state] pipeline below. Failure path is still synchronous: [commitDeleteInternal]
 * clears the id on its own so the row reappears with an error snackbar.
 *
 * Why the reconciler uses `transform { emit; softDeletedIds.update }` rather than the
 * symmetric-looking `onEach { softDeletedIds.update }; emit-implicit`: `onEach` runs the
 * side effect BEFORE forwarding the value downstream, so on `Dispatchers.Main.immediate`
 * the softDeletedIds update could trigger combine's source-1 collector reentrantly — combine
 * then sees `latestValues = [OLD all, new deleted=∅, undoId]` (the new `all` hasn't reached
 * source-0 yet) and emits a transient state where the just-deleted row reappears for one
 * frame. Doing `emit(all)` first guarantees combine's `latestValues[0]` updates to the new
 * `all` before `softDeletedIds` changes; the subsequent `softDeletedIds` update produces the
 * same items list and StateFlow dedupes the duplicate, so no buggy frame ever reaches the UI.
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

    private val effectChannel = Channel<HistoryUiEffect>(Channel.BUFFERED)
    val effects: Flow<HistoryUiEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<HistoryUiState> = combine(
        // Reconcile softDeletedIds against the latest upstream snapshot. Once Room actually
        // emits a list that no longer contains a soft-deleted id, we drop that id from the
        // shadow set — this is the only place softDeletedIds shrinks on the success path.
        // Doing it here (rather than after [deleteMeasurement] returns) avoids the
        // async-invalidation race described in the class kdoc.
        //
        // ORDER MATTERS: emit FIRST, mutate softDeletedIds SECOND. Inverting the two (e.g.
        // via onEach which runs before forwarding) creates a Main.immediate reentrancy hole
        // — see class kdoc for the full trace.
        getMeasurements().transform { all ->
            emit(all)
            val visibleIds = all.mapTo(mutableSetOf()) { it.id }
            softDeletedIds.update { current ->
                if (current.isEmpty()) current else current.intersect(visibleIds)
            }
        },
        softDeletedIds,
        pendingUndoId,
    ) { all, deleted, undoId ->
        HistoryUiState(
            items = all.filter { it.id !in deleted },
            loading = false,
            loadFailed = false,
            pendingUndoId = undoId,
        )
    }
        // Catch any upstream Room IO failure so the screen doesn't get pinned at loading=true.
        // Surface a *distinct* error state (loadFailed=true, items=empty) so the UI can branch
        // away from the empty-state CTA — emitting items=empty alone would conflate "no rows"
        // with "couldn't read the rows" and tell the user to start a new measurement when their
        // existing data is still on disk. Pair it with [HistoryUiEffect.ShowErrorSnackbar] so the
        // failure is also surfaced through the host-level feedback channel.
        .catch {
            effectChannel.trySend(HistoryUiEffect.ShowErrorSnackbar(R.string.history_load_failed))
            emit(HistoryUiState(items = emptyList(), loading = false, loadFailed = true, pendingUndoId = null))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_KEEPALIVE_MS), initialState)

    private var pendingDeleteJob: Job? = null

    // Ids whose commitDelete is currently in flight (post-timer, possibly suspended inside the
    // repository call). Tracked so commitOrphanedSoftDeletes doesn't launch a duplicate retry
    // for an id that's already being committed — softDeletedIds alone can't distinguish
    // "still in undo window" from "commit IO in progress".
    private val committingIds = mutableSetOf<Long>()

    fun onEvent(event: HistoryUiEvent) {
        when (event) {
            is HistoryUiEvent.DeleteRequested -> scheduleDelete(event.id)
            HistoryUiEvent.UndoConfirmed -> cancelPendingDelete()
        }
    }

    private fun scheduleDelete(id: Long) {
        // Idempotent re-swipe of the same id: if a timer for this id is already running we
        // just keep it — kicking off a fresh job would leave the original one orphaned (the
        // orphan-cleanup branch skips it as `except`, so cancellation never fires), and the
        // stale timer would still commit the delete even after Undo cancels the latest job.
        if (id == pendingUndoId.value && pendingDeleteJob != null) return

        // Latest-write-wins: if there is already a pending delete, commit it now (row was already
        // hidden, user already had their undo window) so we don't strand stale entries in the
        // soft-deleted set.
        commitOrphanedSoftDeletes(except = id)

        softDeletedIds.update { it + id }
        // pendingUndoId is the source of truth for the snackbar's visibility — the screen
        // observes it through state instead of a one-shot effect, so the Undo affordance
        // survives rotation/theme-change for the remainder of the commit window.
        pendingUndoId.value = id
        pendingDeleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            // Leave the "cancellable timer" phase atomically: drop our handle so a sibling
            // swipe's commitOrphanedSoftDeletes can no longer interrupt the commit, and
            // claim the id in committingIds so the orphan logic doesn't double-launch us.
            // Both writes are non-suspending and the dispatcher is single-threaded, so the
            // transition is observed as a single step by any concurrent scheduleDelete call.
            pendingDeleteJob = null
            commitDelete(id)
        }
    }

    private fun commitOrphanedSoftDeletes(except: Long) {
        // Only ids that aren't already being committed are real orphans. An id in committingIds
        // is owned by its own (now-untracked) commit coroutine and will resolve itself; relaunching
        // commitDelete for it would be a duplicate Room round-trip plus a potential duplicate
        // error snackbar if the IO fails.
        val orphans = softDeletedIds.value - except - committingIds
        if (orphans.isEmpty()) return
        // Cancelling pendingDeleteJob here only ever cancels a *timer* — once a job has crossed
        // the delay-to-commit boundary it has nulled the handle itself, so this is a no-op for
        // in-flight commits. That separation is the whole point of the two-phase design above.
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        viewModelScope.launch {
            orphans.forEach { id -> commitDelete(id) }
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

    // Suppress TooGenericExceptionCaught: Room can surface a wide range of failure shapes
    // (SQLiteException, IOException via fsync, FK CHECK violations from the LENGTH_GUARD
    // triggers, KSP-generated wrapper exceptions on schema drift). We deliberately want a
    // catch-all here so the row reappears with an error snackbar instead of vanishing
    // silently. CancellationException is explicitly re-thrown above the generic catch to
    // preserve structured cancellation; everything else is handled uniformly.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun commitDeleteInternal(id: Long) {
        // Clear pendingUndoId *before* the suspending repository call so the Undo snackbar
        // dismisses exactly at the 5 s mark, not after the Room IO completes. Otherwise a
        // slow delete (CASCADE on many samples, slow fsync) leaves the snackbar — and its
        // Undo action — clickable past the promised window, making the real undo budget
        // depend on IO latency rather than UNDO_WINDOW_MS.
        if (pendingUndoId.value == id) pendingUndoId.value = null

        // Delete the row from Room and leave the soft-delete shadow in place — the
        // reconciler in [state] will drop the id from `softDeletedIds` as soon as Room emits
        // a snapshot that no longer contains it. Clearing the shadow synchronously here would
        // race with Room's async invalidation: `combine` could re-evaluate with the id no
        // longer filtered while the upstream still holds the pre-delete list, flashing the
        // row back for a frame.
        //
        // On failure (disk full, schema mismatch, IO error) we MUST clear the shadow ourselves
        // — Room never emits a post-delete snapshot, so the reconciler would never get the
        // signal and the row would stay invisible forever (and reappear after a process
        // restart with no UX feedback in between). Clearing it here restores the row and we
        // surface an error snackbar so the user knows their swipe didn't take.
        //
        // CancellationException is re-thrown explicitly: `runCatching` would convert it into a
        // generic failure (Result.failure), which would (a) trigger a spurious
        // history_delete_failed snackbar on viewModelScope teardown and (b) break structured
        // cancellation — e.g. when `commitOrphanedSoftDeletes` cancels an in-flight commit job,
        // the cancellation should propagate, not masquerade as an IO error.
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

    private fun cancelPendingDelete() {
        val id = pendingUndoId.value ?: return
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        softDeletedIds.update { it - id }
        pendingUndoId.value = null
    }

    companion object {
        /** Window during which a soft-deleted row can be restored via Undo. Public so the
         *  screen can size its own animations / accessibility hints against the same value. */
        const val UNDO_WINDOW_MS: Long = 5_000L

        /** Keep the upstream live ~5 s after the last collector to survive config changes
         *  (rotation, theme switch) without re-querying Room. */
        private const val STATE_KEEPALIVE_MS = 5_000L
    }
}
