package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Pause must freeze the running accumulator: min / avg / max stay visible to
 * the user but subsequent upstream emissions are ignored. Reset zeroes
 * everything and returns to `Idle`.
 *
 * Note: the test uses `FakeAudioRepository.emit` even **after** Pause is
 * requested to assert ignoring — without the ignore, the SharedFlow's replay
 * buffer would resurface the values when the collect-job is re-attached after
 * `cancelAndJoin`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelPauseResetTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `Pause freezes state - subsequent emissions are ignored`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(60f, 0L))
        repo.emit(SoundSample(70f, 100L))
        val beforePause = viewModel.state.value

        viewModel.onEvent(MeasureUiEvent.PauseRequested)

        // emit after pause — must NOT affect the state
        repo.emit(SoundSample(120f, 200L))

        val afterPause = viewModel.state.value
        assertEquals(MeasurementPhase.Paused, afterPause.phase)
        assertEquals(beforePause.current, afterPause.current)
        assertEquals(beforePause.max, afterPause.max)
        assertEquals(beforePause.min, afterPause.min)
        assertEquals(beforePause.avg, afterPause.avg)
    }

    @Test
    fun `Reset clears all metrics and returns to Idle phase`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(80f, 100L))
        viewModel.onEvent(MeasureUiEvent.PauseRequested)

        viewModel.onEvent(MeasureUiEvent.ResetRequested)

        val state = viewModel.state.value
        assertEquals(MeasurementPhase.Idle, state.phase)
        assertEquals(0f, state.current)
        assertEquals(Float.POSITIVE_INFINITY, state.min)
        assertEquals(Float.NEGATIVE_INFINITY, state.max)
        assertEquals(0f, state.avg)
        assertEquals(0L, state.durationMs)
        assertEquals(emptyList<SoundSample>(), state.recent)
        // permission stays granted after reset — user already paid that cost
        assertEquals(PermissionState.Granted, state.permissionState)
    }

    @Test
    fun `Reset clears persisted SavedStateHandle scalars so process death after Reset starts fresh`() = runTest {
        val repo = FakeAudioRepository()
        val handle = SavedStateHandle()
        val viewModel = MeasureViewModel(
            savedStateHandle = handle,
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(80f, 100L))
        viewModel.onEvent(MeasureUiEvent.PauseRequested)

        // Persisted between Pause and Reset.
        assertEquals(80f, handle.get<Float>(MeasureViewModel.KEY_CURRENT))

        viewModel.onEvent(MeasureUiEvent.ResetRequested)

        // After Reset, the five headline keys must be gone — otherwise a process kill would
        // resurrect the cleared session as Paused with stale max/avg.
        assertEquals(null, handle.get<Float>(MeasureViewModel.KEY_CURRENT))
        assertEquals(null, handle.get<Float>(MeasureViewModel.KEY_MIN))
        assertEquals(null, handle.get<Float>(MeasureViewModel.KEY_MAX))
        assertEquals(null, handle.get<Float>(MeasureViewModel.KEY_AVG))
        assertEquals(null, handle.get<Long>(MeasureViewModel.KEY_DURATION))
    }

    @Test
    fun `Resume from Paused with new StartRequested transitions to Running`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        viewModel.onEvent(MeasureUiEvent.StartRequested)

        assertEquals(MeasurementPhase.Running, viewModel.state.value.phase)
    }
}
