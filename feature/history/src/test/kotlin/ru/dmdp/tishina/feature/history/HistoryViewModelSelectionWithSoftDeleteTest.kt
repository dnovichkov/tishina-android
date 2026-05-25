package ru.dmdp.tishina.feature.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

/**
 * Selection-set ↔ soft-delete shadow interaction.
 *
 * The combine pipeline filters soft-deleted ids out of [HistoryUiState.items]. The selection
 * set ([HistoryUiState.selectedIds]) must also never contain a soft-deleted id — otherwise the
 * action bar would show a count the user cannot reconcile with the visible cards, and bulk
 * delete would silently include an id the user no longer sees as selectable.
 *
 * UI-level defenses (ignore long-press on items in mid-swipe) protect the happy path, but the
 * VM enforces the invariant defensively: any soft-delete event filters its ids out of
 * `selectedIds` if they were selected before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelSelectionWithSoftDeleteTest {

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
    fun `items size equals visible-after-soft-delete count`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        // No soft-deletes yet: 3 visible.
        var state = vm.state.first { !it.loading }
        assertEquals(3, state.items.size)

        // Soft-delete one: 2 visible.
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        state = vm.state.first { !it.loading }
        assertEquals(2, state.items.size)
        assertFalse(state.items.any { it.id == ids[0] })
    }

    @Test
    fun `single soft-delete of a selected id removes it from selectedIds`() = runTest {
        // Defensive contract: UI never allows mid-swipe + tap on the same card, but if a race
        // sneaks one through the VM filters it out so the action bar count matches the
        // visible cards.
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[0]))
        vm.onEvent(HistoryUiEvent.ToggleSelection(ids[1]))
        runCurrent()
        var state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[0], ids[1]), state.selectedIds)

        // Race: single-swipe ids[0] arrives while selection mode is on.
        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[1]), state.selectedIds, "soft-deleted id must drop out of selection")
    }

    @Test
    fun `SelectAll after partial soft-delete picks only visible ids`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
                sampleMeasurement(createdAt = 300L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[1]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.EnterSelectionMode())
        vm.onEvent(HistoryUiEvent.SelectAll)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertEquals(setOf(ids[0], ids[2]), state.selectedIds)
    }

    @Test
    fun `entering selection mode does not affect a pending single soft-delete`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        runCurrent()

        val state = vm.state.first { !it.loading }
        // Single delete unaffected.
        assertEquals(ids[0], state.pendingUndoId)
        assertFalse(state.items.any { it.id == ids[0] })
        // Selection mode active.
        assertEquals(setOf(ids[1]), state.selectedIds)
    }

    @Test
    fun `ExitSelectionMode does not affect pending single soft-delete`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                sampleMeasurement(createdAt = 100L),
                sampleMeasurement(createdAt = 200L),
            ),
        )
        val vm = viewModel(repo)
        runCurrent()

        vm.onEvent(HistoryUiEvent.DeleteRequested(ids[0]))
        runCurrent()
        vm.onEvent(HistoryUiEvent.EnterSelectionMode(initialId = ids[1]))
        vm.onEvent(HistoryUiEvent.ExitSelectionMode)
        runCurrent()

        val state = vm.state.first { !it.loading }
        assertFalse(state.selectionMode)
        assertEquals(emptySet<Long>(), state.selectedIds)
        // The pending single soft-delete continues unaffected.
        assertEquals(ids[0], state.pendingUndoId)
        assertFalse(state.items.any { it.id == ids[0] })
    }
}
