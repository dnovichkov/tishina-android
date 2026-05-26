package ru.dmdp.tishina.feature.history

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.ExportHistoryUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementsExporter
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule
import java.util.concurrent.atomic.AtomicInteger

/**
 * Soft-delete strategy under test:
 *  - DeleteRequested → id is hidden from the visible list (via softDeletedIds filter),
 *    `pendingUndoId` flips to that id (drives the snackbar from state, see HistoryScreen),
 *    and a 5 s timer schedules the real `delete()`;
 *  - UndoConfirmed within 5 s → timer cancelled, id reappears, repository.delete NEVER called;
 *  - past 5 s → softDeletedIds entry is dropped, `repository.delete(id)` runs once.
 *
 * Uses StandardTestDispatcher so virtual time advances explicitly via `advanceTimeBy` —
 * tests do not actually wait 5 seconds wall-clock. `runCurrent` flushes the synchronous
 * stage of the stateIn-backed combine pipeline before each assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private fun sampleMeasurement(
        createdAt: Long,
        title: String? = null,
        note: String? = null,
        db: Float = 60f,
    ): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAt,
        durationMs = 10_000L,
        avgDb = db,
        minDb = db - 5f,
        maxDb = db + 5f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
        samples = listOf(SoundSample(db = db, timestampMs = 0L)),
    )

    private fun viewModel(repo: FakeMeasurementRepository): HistoryViewModel =
        HistoryViewModel(
            getMeasurements = GetMeasurementsUseCase(repo),
            deleteMeasurement = DeleteMeasurementUseCase(repo),
            deleteMeasurements = DeleteMeasurementsUseCase(repo),
            exportHistory = ExportHistoryUseCase(FakeMeasurementsExporter()),
            nowMillisProvider = { 0L },
        )

    @Test
    fun `empty repository emits empty list with loading false`() = runTest {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)
        vm.state.test {
            // Initial value is the default HistoryUiState (loading=true). Repository emits empty
            // list → ViewModel transitions to loading=false.
            val initial = awaitItem()
            assertTrue(initial.loading, "ViewModel starts in loading state")
            val loaded = awaitItem()
            assertEquals(emptyList<Long>(), loaded.items.map { it.id })
            assertEquals(false, loaded.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seeded measurements appear in descending createdAt order`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L, title = "first"),
                sampleMeasurement(createdAt = 300L, title = "third"),
                sampleMeasurement(createdAt = 200L, title = "second"),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()
        val state = vm.state.first { !it.loading }
        assertEquals(listOf("third", "second", "first"), state.items.map { it.title })
    }

    @Test
    fun `DeleteRequested hides item from list and surfaces pendingUndoId in state`() = runTest {
        // pendingUndoId is the source of truth for the Undo snackbar — the screen observes
        // it through state so the affordance survives rotation. Previously this VM also
        // emitted a ShowUndoSnackbar one-shot effect, but that effect was consumed by the
        // previous Composition and never replayed onto a recreated screen.
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L, title = "keep"),
                sampleMeasurement(createdAt = 200L, title = "drop"),
            ),
        )
        val toDelete = ids[1]
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(toDelete))
        runCurrent()
        val state = vm.state.first { !it.loading }
        assertTrue(state.items.none { it.id == toDelete }, "soft-deleted id must be hidden")
        assertEquals(toDelete, state.pendingUndoId, "pendingUndoId must point at the soft-deleted id")
        // Soft-delete only — actual row still exists until the timer expires.
        assertEquals(2, repo.size())
    }

    @Test
    fun `UndoConfirmed within 5s restores soft-deleted item and cancels real delete`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(listOf(sampleMeasurement(createdAt = 100L, title = "drop")))
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        advanceTimeBy(1_000L)
        vm.onEvent(HistoryUiEvent.UndoConfirmed)
        advanceTimeBy(10_000L)
        runCurrent()

        // Filter to !loading because `stateIn(... initialState)` first emits the loading=true seed
        // before combine flushes the real values.
        val state = vm.state.first { !it.loading }
        assertEquals(listOf(ids[0]), state.items.map { it.id }, "soft-deleted id must reappear")
        assertEquals(1, repo.size(), "repository.delete must NOT have been called")
    }

    @Test
    fun `timer commits delete to repository after 5 seconds`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(listOf(sampleMeasurement(createdAt = 100L, title = "drop")))
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        // Just before timeout: row still present in storage.
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(1, repo.size())

        // Crossing the 5 s mark: timer fires, real delete is invoked.
        advanceTimeBy(2L)
        advanceUntilIdle()
        assertEquals(0, repo.size())

        val state = vm.state.first { !it.loading }
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `consecutive DeleteRequested cancels prior timer and tracks the latest target`() = runTest {
        // UX: the user swipes A, then immediately swipes B. The previous pending delete (A) must
        // commit early (latest write wins), and B becomes the active undo candidate. Without this
        // we'd lose the prior soft-delete or end up with two concurrent timers.
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L, title = "a"),
                sampleMeasurement(createdAt = 200L, title = "b"),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        advanceTimeBy(1_000L)
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[1]))
        runCurrent()
        // Now Undo: should restore the latest soft-delete (B); A was committed when its timer was
        // displaced.
        vm.onEvent(HistoryUiEvent.UndoConfirmed)
        advanceUntilIdle()

        val state = vm.state.first { !it.loading }
        assertEquals(listOf(ids[1]), state.items.map { it.id })
        assertEquals(1, repo.size())
    }

    @Test
    fun `DeleteRequested for unknown id is a no-op (no crash, pendingUndoId still set)`() = runTest {
        // Spec: idempotency. The user clicks delete on a card whose backing row was wiped by some
        // concurrent process. ViewModel should not crash; pendingUndoId still flips so the user
        // sees the Undo affordance (consistent feedback loop even if the underlying row is gone).
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(id = 42L))
        runCurrent()
        assertEquals(42L, vm.state.first { !it.loading }.pendingUndoId)
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertEquals(0, repo.size())
        assertNull(vm.state.first { !it.loading }.items.firstOrNull())
    }

    @Test
    fun `UndoConfirmed without a pending delete is a no-op`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.seed(listOf(sampleMeasurement(createdAt = 100L)))
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.UndoConfirmed)
        runCurrent()
        assertNull(vm.state.first { !it.loading }.pendingUndoId)
        assertEquals(1, repo.size())
    }

    @Test
    fun `upstream Room read failure surfaces loadFailed state plus error effect (not empty CTA)`() = runTest {
        // Regression: previously .catch emitted (items=empty, loading=false) so the screen
        // showed the "make first measurement" CTA on any IO failure. That conflates "nothing
        // saved" with "couldn't load saved data" — misleading the user when their data is
        // intact on disk. The fix is a distinct loadFailed flag + an explicit error effect.
        val failingRepo = object : FakeMeasurementRepository() {
            override fun observeSummaries(): Flow<List<MeasurementSummary>> = flow {
                error("simulated Room IO failure")
            }
        }
        val vm = viewModel(failingRepo)

        // Reach the post-failure state first — this also pulls a subscriber onto the stateIn
        // flow so the upstream actually runs (and the .catch block executes, emitting the effect).
        val state = vm.state.first { !it.loading }
        assertTrue(state.loadFailed, "loadFailed must be true so the UI can branch away from the empty-state CTA")
        assertEquals(emptyList<Long>(), state.items.map { it.id })
        assertFalse(state.loading)

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.ShowErrorSnackbar)
            assertEquals(R.string.history_load_failed, (effect as HistoryUiEffect.ShowErrorSnackbar).messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `commitDelete failure restores row visibility and emits error snackbar`() = runTest {
        // Regression: without runCatching around deleteMeasurement, a Room IO failure during
        // the deferred commit would leave the id stuck in softDeletedIds forever — the row
        // stayed hidden from the visible list even though the row was still on disk and would
        // pop back after a process restart, with no UX feedback in between.
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long): Unit = error("simulated Room IO failure")
        }
        val ids = repo.seed(listOf(sampleMeasurement(createdAt = 100L, title = "ghost")))
        val vm = viewModel(repo)
        runCurrent()

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
            // Cross the 5 s commit boundary — commitDelete fires and the (overridden) repo throws.
            // Undo is now state-driven (pendingUndoId), not an effect, so the only effect we
            // expect on this channel is the error snackbar from the failed commit.
            advanceTimeBy(6_000L)
            advanceUntilIdle()
            val error = awaitItem()
            assertTrue(error is HistoryUiEffect.ShowErrorSnackbar, "expected an error snackbar after delete failure")
            cancelAndIgnoreRemainingEvents()
        }
        // Row must be visible again (soft-delete shadow lifted) and still on disk.
        val state = vm.state.first { !it.loading }
        assertEquals(listOf(ids[0]), state.items.map { it.id })
        assertEquals(1, repo.size())
    }

    @Test
    fun `pendingUndoId clears at the 5s mark regardless of slow repository delete`() = runTest {
        // Regression: snackbar lifetime is gated by pendingUndoId. If pendingUndoId only flips
        // to null *after* deleteMeasurement returns, a slow IO call stretches the visible Undo
        // window past UNDO_WINDOW_MS — the real undo budget becomes "5 s + IO duration". Here we
        // hold the repository's delete suspended indefinitely and assert that pendingUndoId is
        // already null right after the 5 s timer fires, before the IO unblocks.
        //
        // We hold a continuous Turbine subscription throughout so the stateIn-backed combine
        // stays alive across the entire test (otherwise WhileSubscribed(5_000ms) tears down
        // the upstream after our first short read and we miss the mid-window flip to null).
        val gate = CompletableDeferred<Unit>()
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                gate.await()
                super.delete(id)
            }
        }
        val ids = repo.seed(listOf(sampleMeasurement(createdAt = 100L, title = "slow")))
        val vm = viewModel(repo)

        vm.state.test {
            // Drain initial loading=true emission then wait for the first real state (loading=false).
            skipItems(1)
            val ready = awaitItem()
            assertFalse(ready.loading)
            assertNull(ready.pendingUndoId)

            vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
            val pending = awaitItem()
            assertEquals(ids[0], pending.pendingUndoId, "pendingUndoId must flip to the soft-deleted id")
            assertTrue(pending.items.none { it.id == ids[0] }, "soft-deleted id must be hidden")

            // Cross the 5 s commit boundary — the suspended delete keeps the coroutine parked
            // inside deleteMeasurement. The very next emission must be the pendingUndoId→null
            // flip (snackbar dismissal), not a side-effect of the unblocked IO.
            advanceTimeBy(6_000L)
            val afterTimer = awaitItem()
            assertNull(
                afterTimer.pendingUndoId,
                "pendingUndoId must be cleared synchronously with the 5 s timer, not after delete IO completes",
            )
            // Soft-delete shadow stays in place until Room actually deletes (avoids the flash
            // where the row reappears before Room's Flow re-emits the post-delete list).
            assertTrue(afterTimer.items.none { it.id == ids[0] })

            // Unblock the IO and let commitDelete finish — the row drops out of the underlying
            // list via Room's Flow, which propagates as another state emission.
            gate.complete(Unit)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repo.size())
    }

    @Test
    fun `later swipe does not cancel an in-flight commitDelete (single Room call, no error)`() = runTest {
        // Two-phase pendingDeleteJob design under test:
        //  - the *timer* phase is the only thing pendingDeleteJob holds; the moment delay()
        //    elapses, the job nulls its own handle and adds the id to committingIds before
        //    entering commitDelete;
        //  - a sibling swipe arriving while A is in-flight runs commitOrphanedSoftDeletes,
        //    which sees pendingDeleteJob == null and A ∈ committingIds, so it does NOT
        //    cancel A and does NOT relaunch a retry — A's original commit runs to completion.
        //
        // Regressions this guards against:
        //   1. Old code stored timer+commit in the same job and called pendingDeleteJob.cancel()
        //      from a sibling swipe, killing the in-flight Room transaction mid-IO. The orphan
        //      retry would then call deleteMeasurement(A) a second time — wasted IO and a tiny
        //      window where a process kill between cancel and retry could resurrect A.
        //   2. runCatching used to swallow CancellationException → spurious error snackbar
        //      on the previous design's structured cancellation. The explicit re-throw keeps
        //      that property too, but the more important invariant now is that no cancellation
        //      happens in the first place.
        val deleteCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                deleteCalls.incrementAndGet()
                gate.await()
                super.delete(id)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L, title = "a"),
                sampleMeasurement(createdAt = 200L, title = "b"),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.effects.test {
            // Park A's commit inside deleteMeasurement. After advanceTimeBy(6 s), A has crossed
            // the timer→commit boundary, pendingDeleteJob is null, and committingIds = {A}.
            vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
            advanceTimeBy(6_000L)
            runCurrent()
            assertEquals(1, deleteCalls.get(), "A's delete must have been invoked once when timer fired")

            // Swiping B must not cancel A — commitOrphanedSoftDeletes sees A ∈ committingIds
            // and skips it. Without the committingIds guard, the orphan branch would relaunch
            // a second commitDelete(A) — and deleteCalls would tick to 2.
            vm.onEvent(HistoryUiEvent.DeleteRequested(ids[1]))
            runCurrent()
            assertEquals(1, deleteCalls.get(), "swiping B must not trigger a second delete(A) call")

            // Unblock A's parked IO. The original commit completes; any incorrect failure path
            // (e.g. a spurious CancellationException leaking into the error branch) would emit
            // here. We also expect B's timer to fire and commit B once its 5 s elapse.
            gate.complete(Unit)
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        // Final accounting: A deleted exactly once, B deleted exactly once — no retries.
        assertEquals(2, deleteCalls.get(), "expected one delete call per swipe, with no cancel-and-retry duplicates")
        assertEquals(0, repo.size(), "both rows must be gone from the repository")
    }

    @Test
    fun `re-swipe of same id keeps original timer and Undo cancels the actual delete`() = runTest {
        // Regression: previously, scheduleDelete(A) followed by another scheduleDelete(A)
        // before the 5 s timer expired left two timers running.
        //
        //  - First scheduleDelete(A): pendingDeleteJob = job1, softDeletedIds = {A}.
        //  - Second scheduleDelete(A): commitOrphanedSoftDeletes(except = A) computes
        //    orphans = {A} - {A} - {} = {}, returns without cancelling anything. Then
        //    pendingDeleteJob is overwritten with a brand-new job2. job1 is now orphaned
        //    but very much alive.
        //  - UndoConfirmed cancels only the latest job (job2). job1's delay keeps ticking
        //    and ends up committing the delete to the repository despite the Undo.
        //
        // The fix is an idempotent guard in scheduleDelete: if a timer for this same id is
        // already pending, just keep it. The undo window is the original one — re-swiping
        // doesn't extend it and doesn't spawn duplicates.
        val deleteCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                deleteCalls.incrementAndGet()
                super.delete(id)
            }
        }
        val ids = repo.seed(listOf(sampleMeasurement(createdAt = 100L, title = "double-swipe")))
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        advanceTimeBy(1_000L)
        // Re-swipe the SAME id — must be a no-op (no second timer, no overwritten handle).
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        advanceTimeBy(1_000L)

        vm.onEvent(HistoryUiEvent.UndoConfirmed)
        // Run well past 5 s — if the original timer were still alive (bug), it would fire
        // here and commit the delete despite the user's Undo.
        advanceTimeBy(10_000L)
        advanceUntilIdle()

        assertEquals(0, deleteCalls.get(), "Undo must prevent any repository.delete call, even after a duplicate swipe")
        assertEquals(1, repo.size(), "row must remain on disk")
        val state = vm.state.first { !it.loading }
        assertEquals(listOf(ids[0]), state.items.map { it.id }, "row must be visible again after Undo")
        assertNull(state.pendingUndoId, "pendingUndoId must clear after Undo")
    }

    @Test
    fun `softDeletedIds shrinks only when upstream emits post-delete snapshot`() = runTest {
        // Regression for the real-Room async-invalidation race:
        //
        // Room's `InvalidationTracker` re-emits observed Flows on its background executor.
        // That means `deleteMeasurement(id)` can return synchronously (SQLite transaction
        // committed) BEFORE the upstream Flow has caught up. The old code cleared
        // `softDeletedIds.update { it - id }` right after deleteMeasurement returned, so
        // `combine(getMeasurements(), softDeletedIds, pendingUndoId)` would re-evaluate with
        // `all = pre-delete list` AND `deleted = {}` — the row would flash back into the
        // visible list for a frame until Room finally emitted the post-delete list.
        //
        // The FakeMeasurementRepository default delete() mutates its in-memory store
        // synchronously, so the fake's observeSummaries Flow emits the post-delete snapshot
        // before deleteMeasurement returns — the race never appears in the default fake.
        // Here we model the real Room behavior by:
        //  - swapping observeSummaries() for a hand-controlled MutableStateFlow, and
        //  - overriding delete() to return immediately WITHOUT touching that flow.
        // The test then asserts that after the 5 s timer fires (commit completes, deleteMeasurement
        // returned), the row is still hidden from `items` because softDeletedIds is now
        // reconciled lazily against the upstream snapshot rather than being cleared eagerly.
        val seeded = ru.dmdp.tishina.core.domain.model.MeasurementSummary(
            id = 1L,
            createdAtEpochMs = 100L,
            durationMs = 10_000L,
            avgDb = 60f,
            minDb = 55f,
            maxDb = 65f,
            title = "race",
            note = null,
            sparklinePreview = emptyList(),
        )
        val upstream = MutableStateFlow(listOf(seeded))
        val repo = object : FakeMeasurementRepository() {
            override fun observeSummaries(): Flow<List<ru.dmdp.tishina.core.domain.model.MeasurementSummary>> =
                upstream
            override suspend fun delete(id: Long) {
                // Simulate Room's gap: return immediately; upstream still holds the pre-delete list.
            }
        }
        val vm = viewModel(repo)

        vm.state.test {
            // First emission is the loading=true initial seed; second is the first real combine
            // result with the seeded row visible.
            skipItems(1)
            val ready = awaitItem()
            assertFalse(ready.loading)
            assertEquals(listOf(1L), ready.items.map { it.id })

            vm.onEvent(HistoryUiEvent.DeleteRequested(1L))
            val pending = awaitItem()
            assertEquals(1L, pending.pendingUndoId)
            assertTrue(pending.items.isEmpty(), "row must be hidden behind the soft-delete shadow")

            // Cross the 5 s timer boundary. The next emission is the pendingUndoId→null flip
            // from commitDeleteInternal (cleared before the suspending repo call). Crucially,
            // softDeletedIds must NOT have been cleared yet — the upstream is still
            // [seeded], so a buggy version would re-emit the row here.
            advanceTimeBy(6_000L)
            val afterTimer = awaitItem()
            assertNull(afterTimer.pendingUndoId)
            assertTrue(
                afterTimer.items.none { it.id == 1L },
                "row must stay hidden while upstream still holds the pre-delete snapshot",
            )

            // Now simulate Room's delayed invalidation: upstream finally emits the post-delete
            // snapshot. The reconciler in [HistoryViewModel.state] drops the id from
            // softDeletedIds; combine re-evaluates with `all = []` and items stays empty —
            // but since the previous state was *already* (items=[], pendingUndoId=null),
            // StateFlow dedupes the equal value and no new emission reaches Turbine.
            //
            // Regression guard for the Main.immediate reentrancy hole: if the reconciler
            // were written as `getMeasurements().onEach { softDeletedIds.update(...) }` (the
            // side effect runs BEFORE forwarding `all` downstream), then on a dispatcher
            // that supports reentrant resumption combine could emit a transient
            // `[OLD all, deleted=∅]` state where the just-deleted row reappears for one
            // frame. With the current `transform { emit; softDeletedIds.update }` ordering
            // the new `all` reaches combine BEFORE the soft-delete shadow shrinks, so the
            // two combine emissions both produce `items=[]` and StateFlow dedupes them away.
            // expectNoEvents() asserts exactly that: zero post-invalidation emissions reach
            // the UI, which means no "row flashes back" frame can ever happen.
            upstream.value = emptyList()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        val finalState = vm.state.first { !it.loading }
        assertTrue(finalState.items.isEmpty(), "list must be empty after upstream catches up")
    }
}
