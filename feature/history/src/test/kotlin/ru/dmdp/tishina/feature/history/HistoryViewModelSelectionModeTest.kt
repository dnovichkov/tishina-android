package ru.dmdp.tishina.feature.history

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule
import java.util.concurrent.atomic.AtomicInteger

/**
 * Selection-mode + bulk soft-delete state machine for FR-12.
 *
 * Two complementary undo channels coexist on this VM:
 *  - `pendingUndoId: Long?` for single swipe-delete (FR-11).
 *  - `pendingBulkUndoCount: Int` (size of an internal bulk-id set) for multi-select bulk-delete.
 *
 * Both share the same `softDeletedIds` shadow so the visible list filter has a single source
 * of truth, but each has its own timer + Undo callback. The cross-interaction (single↔bulk
 * orphan-commit) is exercised separately in [HistoryViewModelBulkInteractionTest].
 *
 * Tests use StandardTestDispatcher so virtual time advances explicitly via `advanceTimeBy` —
 * we never wait 5 seconds wall-clock. `runCurrent` flushes the synchronous stage of the
 * stateIn-backed combine pipeline before each assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelSelectionModeTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private fun sampleMeasurement(createdAt: Long, title: String? = null): NewMeasurement =
        NewMeasurement(
            createdAtEpochMs = createdAt,
            durationMs = 10_000L,
            avgDb = 60f,
            minDb = 55f,
            maxDb = 65f,
            title = title,
            note = null,
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.FAST,
            calibrationOffsetDb = 0f,
            sampleRateHz = 48_000,
            samples = listOf(SoundSample(db = 60f, timestampMs = 0L)),
        )

    private fun viewModel(repo: FakeMeasurementRepository): HistoryViewModel =
        HistoryViewModel(
            getMeasurements = GetMeasurementsUseCase(repo),
            deleteMeasurement = DeleteMeasurementUseCase(repo),
            deleteMeasurements = DeleteMeasurementsUseCase(repo),
        )

    private fun seedThree(repo: FakeMeasurementRepository): List<Long> = repo.seed(
        listOf(
            sampleMeasurement(createdAt = 100L, title = "a"),
            sampleMeasurement(createdAt = 200L, title = "b"),
            sampleMeasurement(createdAt = 300L, title = "c"),
        ),
    )

    @Test
    fun `initial state has selectionMode=false and empty selectedIds`() = runTest {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)
        runCurrent()
        val state = vm.state.first { !it.loading }
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
        assertEquals(0, state.pendingBulkUndoCount)
    }

    @Test
    fun `EnterSelectionMode with no initialId enables mode with empty selection`() = runTest {
        val repo = FakeMeasurementRepository()
        seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertTrue(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `EnterSelectionMode with initialId pre-selects that id`() = runTest {
        // UX: long-pressing a card enters selection mode AND selects the long-pressed card in
        // one round-trip — so the action bar shows "1 selected" immediately rather than "0".
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertTrue(state.selectionMode)
        assertEquals(setOf(ids[1]), state.selectedIds)
    }

    @Test
    fun `ToggleSelection in selection mode adds and removes the id`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        var state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[0], ids[1]), state.selectedIds)

        // Toggling an already-selected id removes it.
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[0]))
        runCurrent()
        state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[1]), state.selectedIds)
    }

    @Test
    fun `ToggleSelection outside selection mode is a no-op`() = runTest {
        // Defense-in-depth: the UI shouldn't fire ToggleSelection when selection mode is off,
        // but if a race delivers one (e.g. concurrent tap during ExitSelectionMode commit),
        // the VM must ignore it rather than create a "phantom selection".
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[0]))
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `SelectAll selects every visible id`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.SelectAll)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertEquals(ids.toSet(), state.selectedIds)
    }

    @Test
    fun `SelectAll excludes soft-deleted ids (visible-only contract)`() = runTest {
        // Single-pending soft-delete hides one card from `items` — SelectAll should reflect the
        // visible list, not the underlying repo set. Otherwise the user would bulk-delete an id
        // they can no longer see.
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.SelectAll)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[1], ids[2]), state.selectedIds)
    }

    @Test
    fun `SelectAll surfaces error snackbar instead of crashing when upstream throws`() = runTest {
        // Regression: SelectAll launches an isolated child coroutine that reads
        // getMeasurements().first(). The main state pipeline catches upstream failures via
        // .catch, but this child coroutine is separate — a throwing Flow used to propagate
        // to CoroutineExceptionHandler and crash. Fix wraps the read in try/catch and emits
        // the same ShowErrorSnackbar(history_load_failed) that the main pipeline uses, so
        // the UI degrades gracefully into the loadFailed state instead.
        val failingRepo = object : FakeMeasurementRepository() {
            override fun observeSummaries(): Flow<List<MeasurementSummary>> = flow {
                error("simulated Room IO failure")
            }
        }
        val vm = viewModel(failingRepo)
        // Drain the upstream-failure snackbar emitted by the main pipeline first so the
        // assertion below only inspects the SelectAll-driven emission.
        vm.state.first { !it.loading }
        vm.effects.test {
            assertTrue(awaitItem() is HistoryUiEffect.ShowErrorSnackbar)

            vm.onEvent(HistoryUiEvent.EnterSelectionMode())
            vm.onEvent(HistoryUiEvent.SelectAll)
            runCurrent()

            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.ShowErrorSnackbar)
            assertEquals(
                R.string.history_load_failed,
                (effect as HistoryUiEffect.ShowErrorSnackbar).messageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ClearSelection empties selectedIds but stays in selection mode`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.ClearSelection)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertTrue(state.selectionMode, "ClearSelection must NOT exit selection mode")
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `ExitSelectionMode resets both selectionMode and selectedIds`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ExitSelectionMode)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `ToggleSelection of a soft-deleted id is a no-op`() = runTest {
        // Soft-deleted ids are filtered out of `items` — selecting one would create an invisible
        // checkbox the user can't toggle off. The VM ignores ToggleSelection for any id in
        // softDeletedIds.
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[0]))
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertEquals(emptySet<Long>(), state.selectedIds)
    }

    @Test
    fun `BulkDeleteRequested with empty selection emits error effect and is a no-op`() = runTest {
        // Contract: empty bulk-delete is a programming error — UI must hide the Delete N button
        // when nothing is selected. Defense-in-depth here surfaces a localized error toast.
        val repo = FakeMeasurementRepository()
        seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.EnterSelectionMode())
            vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
            runCurrent()

            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.ShowErrorSnackbar)
            assertEquals(
                R.string.history_bulk_no_selection,
                (effect as HistoryUiEffect.ShowErrorSnackbar).messageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // No soft-delete, no timer, repo intact.
        val state = vm.state.first { !it.loading }
        assertEquals(0, state.pendingBulkUndoCount)
        assertEquals(3, repo.size())
    }

    @Test
    fun `BulkDeleteRequested with non-empty selection schedules bulk soft-delete and exits selection`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        runCurrent()

        val state = vm.state.first { !it.loading }
        // Selection mode auto-exits — the action bar would otherwise show "0 selected" while
        // the snackbar is pending Undo, which is confusing UX.
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
        // pendingBulkUndoCount drives the snackbar (state-driven Undo mirrors single FR-11).
        assertEquals(2, state.pendingBulkUndoCount)
        // Soft-deleted ids hidden from list; row still on disk until 5 s timer fires.
        assertTrue(state.items.none { it.id == ids[0] || it.id == ids[1] })
        assertEquals(3, repo.size())
    }

    @Test
    fun `BulkUndoConfirmed within 5s restores all bulk-deleted ids and cancels real delete`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[2]))
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(2_000L)
        vm.onEvent(HistoryUiEvent.BulkUndoConfirmed)
        advanceTimeBy(10_000L)
        advanceUntilIdle()

        val state = vm.state.first { !it.loading }
        // All three rows visible again; repo intact (no deleteAll call).
        assertEquals(ids.toSet(), state.items.map { it.id }.toSet())
        assertEquals(0, state.pendingBulkUndoCount)
        assertEquals(3, repo.size())
    }

    @Test
    fun `bulk timer commits deleteAll to repository after 5 seconds`() = runTest {
        val deleteAllCalls = AtomicInteger(0)
        var lastBatch: Set<Long>? = null
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>) {
                deleteAllCalls.incrementAndGet()
                lastBatch = ids
                super.deleteAll(ids)
            }
        }
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(0, deleteAllCalls.get(), "deleteAll must NOT fire before 5 s")
        assertEquals(3, repo.size())

        advanceTimeBy(2L)
        advanceUntilIdle()
        assertEquals(1, deleteAllCalls.get())
        assertEquals(setOf(ids[0], ids[1]), lastBatch)
        assertEquals(1, repo.size())

        val state = vm.state.first { !it.loading }
        assertEquals(0, state.pendingBulkUndoCount)
        assertEquals(listOf(ids[2]), state.items.map { it.id })
    }

    @Test
    fun `BulkUndoConfirmed without a pending bulk-delete is a no-op`() = runTest {
        val repo = FakeMeasurementRepository()
        seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.BulkUndoConfirmed)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertEquals(0, state.pendingBulkUndoCount)
        assertEquals(3, repo.size())
    }

    @Test
    fun `bulk delete failure restores rows and emits error snackbar`() = runTest {
        // Mirrors the single-delete failure path: if the repository deleteAll throws, the
        // soft-delete shadow must be lifted (rows reappear) and an error effect surfaces so
        // the user sees their bulk swipe didn't take.
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>): Unit = error("simulated Room IO failure")
        }
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
            vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
            vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
            advanceTimeBy(6_000L)
            advanceUntilIdle()

            val error = awaitItem()
            assertTrue(error is HistoryUiEffect.ShowErrorSnackbar)
            assertEquals(
                R.string.history_bulk_delete_failed,
                (error as HistoryUiEffect.ShowErrorSnackbar).messageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }

        val state = vm.state.first { !it.loading }
        assertEquals(ids.toSet(), state.items.map { it.id }.toSet())
        assertEquals(0, state.pendingBulkUndoCount)
        assertEquals(3, repo.size())
    }

    @Test
    fun `pendingBulkUndoCount clears at the 5s mark regardless of slow repository deleteAll`() = runTest {
        // Mirrors `pendingUndoId clears at the 5s mark regardless of slow repository delete`
        // for the bulk path: snackbar lifetime is gated by pendingBulkUndoCount. If we only
        // flipped it to 0 after deleteAll returned, slow IO would stretch the visible Undo
        // window past 5 s.
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>) {
                gate.await()
                super.deleteAll(ids)
            }
        }
        val ids = seedThree(repo)
        val vm = viewModel(repo)

        vm.state.test {
            skipItems(1)
            val ready = awaitItem()
            assertFalse(ready.loading)
            assertEquals(0, ready.pendingBulkUndoCount)

            vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
            // Skip the selection-mode flip (selectionMode=true, selectedIds={ids[0]}).
            skipItems(1)
            vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
            val pending = awaitItem()
            assertEquals(1, pending.pendingBulkUndoCount)
            assertFalse(pending.selectionMode, "selection mode must auto-exit on bulk request")

            // Cross the 5 s commit boundary while deleteAll is parked. The very next emission
            // must be the pendingBulkUndoCount→0 flip, not a side-effect of unblocked IO.
            advanceTimeBy(6_000L)
            val afterTimer = awaitItem()
            assertEquals(
                0,
                afterTimer.pendingBulkUndoCount,
                "pendingBulkUndoCount must clear synchronously with the 5 s timer",
            )

            gate.complete(Unit)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, repo.size())
    }

    @Test
    fun `EnterSelectionMode for soft-deleted initialId ignores the pre-selection`() = runTest {
        // Edge case: long-press race — the card is mid-swipe-delete when the long-press lands.
        // EnterSelectionMode(initialId=softDeleted) must not seed the selection with an
        // invisible id; mode flips on with empty selectedIds.
        val repo = FakeMeasurementRepository()
        val ids = seedThree(repo)
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertTrue(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
        // The original single-delete pending is unaffected.
        assertEquals(ids[0], state.pendingUndoId)
    }

    @Test
    fun `bulk delete with 1000 ids commits in a single repository call`() = runTest {
        // SQLITE_MAX_VARIABLE_NUMBER chunking is the Data layer's responsibility (Room ≥ 2.5
        // chunks automatically). VM must just pass the set through unmodified.
        var observedBatchSize: Int? = null
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>) {
                observedBatchSize = ids.size
                super.deleteAll(ids)
            }
        }
        val seeded = repo.seed(List(1000) { sampleMeasurement(createdAt = it.toLong()) })
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.SelectAll)
        // SelectAll resolves the visible set inside a launched coroutine (so it can read
        // upstream without depending on a `state` collector being present). runCurrent flushes
        // that launch before we dispatch BulkDeleteRequested — realistic UI naturally inserts
        // a dispatcher cycle between user-driven events.
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(6_000L)
        advanceUntilIdle()

        assertEquals(1000, observedBatchSize)
        assertEquals(0, repo.size())
        // Sanity: VM didn't accidentally split into chunks.
        assertNull(vm.state.first { !it.loading }.pendingUndoId)
        assertEquals(0, vm.state.first { !it.loading }.pendingBulkUndoCount)
        assertEquals(seeded.size, 1000)
    }
}
