package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.SaveMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Phase 3 turns Save from a stub into a real two-step flow:
 *   1. SaveRequested  → ShowSaveDialog effect (or "no data" snackbar if buffer empty)
 *   2. SaveDialogConfirmed(title, note) → SaveMeasurementUseCase → snackbar + reset
 *
 * The ViewModel validates title/note lengths up-front so the user sees the error
 * synchronously rather than after a Room round-trip (defense-in-depth: the use-case
 * + DAO carry the same constraint).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelSaveFlowTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModelWith(
        audio: FakeAudioRepository,
        store: FakeMeasurementRepository,
    ): MeasureViewModel = MeasureViewModel(
        savedStateHandle = SavedStateHandle(),
        startMeasurement = StartMeasurementUseCase(audio),
        resetMeasurement = ResetMeasurementUseCase(),
        saveMeasurement = SaveMeasurementUseCase(store),
    )

    @Test
    fun `SaveRequested with empty buffer emits no_data snackbar (no dialog)`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.SaveRequested)
            val effect = awaitItem()
            assertEquals(MeasureUiEffect.ShowSnackbar(R.string.measure_save_no_data), effect)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, store.size(), "no data → no row saved")
    }

    @Test
    fun `SaveRequested with samples emits ShowSaveDialog (no state mutation)`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        val stateBefore = viewModel.state.value

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.SaveRequested)
            assertEquals(MeasureUiEffect.ShowSaveDialog, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(stateBefore, viewModel.state.value, "SaveRequested must not mutate state")
    }

    @Test
    fun `SaveDialogConfirmed with valid input calls use-case and emits saved snackbar`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        audio.emit(SoundSample(70f, 200L))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)

        viewModel.effects.test {
            // First effect from SaveRequested (already in channel since we didn't consume it).
            awaitItem() // ShowSaveDialog
            viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = "Спальня", note = "вечер"))
            val savedEffect = awaitItem()
            assertEquals(MeasureUiEffect.ShowSnackbar(R.string.measure_saved), savedEffect)
            cancelAndIgnoreRemainingEvents()
        }

        // Persistence happened with our inputs.
        assertEquals(1, store.size())
        val summary = store.observeSummaries().first().first()
        assertEquals("Спальня", summary.title)
        assertEquals("вечер", summary.note)
        val details = checkNotNull(store.getById(summary.id))
        assertNotNull(details)
        assertTrue(details.samples.isNotEmpty(), "samples must be persisted with measurement")

        // Buffer cleared + phase reset after success (FR-7 spirit: post-save, user sees a clean slate).
        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
        assertEquals(0L, viewModel.state.value.durationMs)
    }

    @Test
    fun `SaveDialogConfirmed with title longer than 80 chars emits title_too_long snackbar`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.effects.test {
            awaitItem() // drop ShowSaveDialog
            val tooLong = "a".repeat(81)
            viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = tooLong, note = null))
            assertEquals(MeasureUiEffect.ShowSnackbar(R.string.measure_save_title_too_long), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, store.size(), "validation failure must not write a row")
    }

    @Test
    fun `SaveDialogConfirmed with note longer than 200 chars emits note_too_long snackbar`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.effects.test {
            awaitItem() // drop ShowSaveDialog
            val tooLong = "n".repeat(201)
            viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = tooLong))
            assertEquals(MeasureUiEffect.ShowSnackbar(R.string.measure_save_note_too_long), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, store.size())
    }

    @Test
    fun `SaveDialogConfirmed when repository throws emits save_failed snackbar`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository().apply {
            saveError = IllegalStateException("simulated DB failure")
        }
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.effects.test {
            awaitItem() // drop ShowSaveDialog
            viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = "x", note = null))
            assertEquals(MeasureUiEffect.ShowSnackbar(R.string.measure_save_failed), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, store.size(), "failure must not persist a partial row")
    }

    @Test
    fun `SaveDialogDismissed is a no-op (no effect, no state change)`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWith(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        val stateBefore = viewModel.state.value

        viewModel.onEvent(MeasureUiEvent.SaveDialogDismissed)

        viewModel.effects.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(stateBefore, viewModel.state.value)
        assertEquals(0, store.size())
    }
}
