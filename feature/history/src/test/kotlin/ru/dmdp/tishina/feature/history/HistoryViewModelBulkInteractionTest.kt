package ru.dmdp.tishina.feature.history

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
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
 * Orphan-commit semantics between single (FR-11) and bulk (FR-12) soft-deletes.
 *
 * Rule: each new soft-delete request *of any kind* eagerly commits any pending soft-delete
 * (single or bulk) of the OPPOSITE/OTHER kind, mirroring the existing rule that consecutive
 * single swipes commit the prior single. This keeps exactly one timer running at a time and
 * never strands stale entries in the soft-delete shadow.
 *
 * Invariants under test:
 *   1. single pending + bulk requested  → commit single immediately + start bulk timer.
 *   2. bulk pending + single swipe      → commit bulk immediately + start single timer.
 *   3. bulk pending + new bulk request  → commit prev bulk immediately + start new bulk timer.
 *
 * In every case the "orphan committed" delete must reach the repository exactly ONCE
 * (no cancel-and-retry duplicates).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelBulkInteractionTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private fun sampleMeasurement(createdAt: Long): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAt,
        durationMs = 10_000L,
        avgDb = 60f,
        minDb = 55f,
        maxDb = 65f,
        title = "m$createdAt",
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

    @Test
    fun `single pending then bulk requested orphan-commits single before bulk timer starts`() = runTest {
        val singleCalls = AtomicInteger(0)
        val bulkCalls = AtomicInteger(0)
        var lastBulkBatch: Set<Long>? = null
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                singleCalls.incrementAndGet()
                super.delete(id)
            }
            override suspend fun deleteAll(ids: Set<Long>) {
                bulkCalls.incrementAndGet()
                lastBulkBatch = ids
                super.deleteAll(ids)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
                sampleMeasurement(createdAt = 400L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // Step 1: soft-delete single id[0].
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, singleCalls.get(), "single not yet committed")

        // Step 2: bulk-delete ids[1], ids[2] — must orphan-commit single id[0] right now.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[2]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        runCurrent()

        assertEquals(1, singleCalls.get(), "single must be orphan-committed when bulk arrives")
        assertEquals(0, bulkCalls.get(), "bulk timer must not have fired yet")

        // Step 3: cross the 5 s mark to commit the bulk.
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertEquals(1, singleCalls.get(), "single must not double-commit")
        assertEquals(1, bulkCalls.get())
        assertEquals(setOf(ids[1], ids[2]), lastBulkBatch)
        assertEquals(1, repo.size())
        assertNotNull(vm.state.first { !it.loading }.items.firstOrNull { it.id == ids[3] })
    }

    @Test
    fun `bulk pending then single swipe orphan-commits bulk before single timer starts`() = runTest {
        val singleCalls = AtomicInteger(0)
        val bulkCalls = AtomicInteger(0)
        var lastBulkBatch: Set<Long>? = null
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                singleCalls.incrementAndGet()
                super.delete(id)
            }
            override suspend fun deleteAll(ids: Set<Long>) {
                bulkCalls.incrementAndGet()
                lastBulkBatch = ids
                super.deleteAll(ids)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
                sampleMeasurement(createdAt = 400L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // Step 1: schedule bulk-delete {ids[0], ids[1]}.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, bulkCalls.get(), "bulk not yet committed")

        // Step 2: swipe single ids[2] — orphan-commits the bulk immediately.
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[2]))
        runCurrent()
        assertEquals(1, bulkCalls.get(), "bulk must be orphan-committed when single arrives")
        assertEquals(setOf(ids[0], ids[1]), lastBulkBatch)
        assertEquals(0, singleCalls.get(), "single timer hasn't fired yet")

        // Step 3: cross the 5 s mark for the single timer.
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertEquals(1, singleCalls.get())
        assertEquals(1, repo.size())
    }

    @Test
    fun `consecutive bulk requests orphan-commit the previous bulk`() = runTest {
        val deleteAllInvocations = mutableListOf<Set<Long>>()
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>) {
                deleteAllInvocations.add(ids)
                super.deleteAll(ids)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
                sampleMeasurement(createdAt = 400L),
                sampleMeasurement(createdAt = 500L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // First bulk: {ids[0], ids[1]}.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(1_000L)

        // Second bulk: {ids[2], ids[3]} — orphan-commits the first one.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[2]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[3]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        runCurrent()

        assertEquals(1, deleteAllInvocations.size, "first bulk must be orphan-committed")
        assertEquals(setOf(ids[0], ids[1]), deleteAllInvocations[0])

        // Cross the 5 s mark for the second bulk.
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertEquals(2, deleteAllInvocations.size)
        assertEquals(setOf(ids[2], ids[3]), deleteAllInvocations[1])
        assertEquals(1, repo.size())
        assertNotNull(vm.state.first { !it.loading }.items.firstOrNull { it.id == ids[4] })
    }

    @Test
    fun `BulkUndoConfirmed only cancels the latest bulk - prior single still commits`() = runTest {
        // After a bulk request orphan-commits a pending single, Undo on the bulk snackbar
        // must NOT resurrect the single — it was already committed to the repo. This is the
        // analogue of "consecutive single swipes commit the prior — Undo restores only the
        // latest" from FR-11.
        val singleCalls = AtomicInteger(0)
        val bulkCalls = AtomicInteger(0)
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long) {
                singleCalls.incrementAndGet()
                super.delete(id)
            }
            override suspend fun deleteAll(ids: Set<Long>) {
                bulkCalls.incrementAndGet()
                super.deleteAll(ids)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // Single pending then bulk request.
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        runCurrent()

        // Undo only the bulk → ids[1] reappears. ids[0] is gone (single was orphan-committed).
        vm.onEvent(HistoryUiEvent.BulkUndoConfirmed)
        advanceTimeBy(10_000L)
        advanceUntilIdle()

        assertEquals(1, singleCalls.get(), "orphaned single must remain committed despite bulk Undo")
        assertEquals(0, bulkCalls.get(), "bulk timer must be cancelled by Undo")
        val state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[1], ids[2]), state.items.map { it.id }.toSet())
        assertEquals(2, repo.size())
    }

    @Test
    fun `later bulk request does not cancel an in-flight commit of earlier bulk`() = runTest {
        // Two-phase pendingBulkJob invariant — the timer is cancellable, the commit is NOT.
        // After the timer fires, the bulk job nulls its own handle and adds its ids to
        // committingBulkIds. A new bulk request must NOT cancel the in-flight commit and
        // must NOT relaunch a duplicate deleteAll for those ids.
        val deleteAllCalls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val repo = object : FakeMeasurementRepository() {
            override suspend fun deleteAll(ids: Set<Long>) {
                deleteAllCalls.incrementAndGet()
                gate.await()
                super.deleteAll(ids)
            }
        }
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
                sampleMeasurement(createdAt = 400L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // First bulk → park commit inside deleteAll.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(1, deleteAllCalls.get(), "first bulk's deleteAll must be invoked once when timer fires")

        // Second bulk while first is parked.
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.BulkDeleteRequested)
        runCurrent()
        assertEquals(1, deleteAllCalls.get(), "second bulk must NOT relaunch a deleteAll for the in-flight ids")

        // Unblock the first commit. The second bulk timer then fires after its own 5 s.
        gate.complete(Unit)
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        assertEquals(2, deleteAllCalls.get(), "second bulk commits exactly once on its own timer")
        assertEquals(2, repo.size(), "ids[2] and ids[3] remain")
    }
}
