package ru.dmdp.tishina.feature.history

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Soft-delete strategy under test:
 *  - DeleteRequested → id is hidden from the visible list (via softDeletedIds filter), a
 *    ShowUndoSnackbar effect fires, and a 5 s timer schedules the real `delete()`;
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
    fun `DeleteRequested hides item from list and emits ShowUndoSnackbar`() = runTest {
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

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.DeleteRequested(toDelete))
            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.ShowUndoSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        val state = vm.state.first { !it.loading }
        assertTrue(state.items.none { it.id == toDelete }, "soft-deleted id must be hidden")
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
    fun `DeleteRequested for unknown id is a no-op (no crash, snackbar still fires)`() = runTest {
        // Spec: idempotency. The user clicks delete on a card whose backing row was wiped by some
        // concurrent process. ViewModel should not crash; the snackbar still fires because the
        // user already saw the swipe and deserves the feedback loop.
        val repo = FakeMeasurementRepository()
        val vm = viewModel(repo)
        runCurrent()

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.DeleteRequested(id = 42L))
            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.ShowUndoSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
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

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.UndoConfirmed)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repo.size())
    }
}
