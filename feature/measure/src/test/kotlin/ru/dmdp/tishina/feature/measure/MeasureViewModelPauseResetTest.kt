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
import ru.dmdp.tishina.core.domain.usecase.SaveMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
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
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
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
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
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
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
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
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        viewModel.onEvent(MeasureUiEvent.StartRequested)

        assertEquals(MeasurementPhase.Running, viewModel.state.value.phase)
    }

    @Test
    fun `Resume from Paused preserves session min, max and avg across the upstream restart`() = runTest {
        // Regression: before SessionSeed, Pause cancelled the upstream and Resume launched a fresh
        // runningFold from Empty, so the first post-resume sample overwrote the pre-pause aggregate.
        // This test scripts a session that peaked at 80 dB and dipped to 40 dB, pauses, then resumes
        // with a single 60 dB sample. The post-resume `min` must remain 40, `max` must remain 80,
        // `avg` must include the pre-pause samples — none of which a from-scratch fold would do.
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(40f, 0L))
        repo.emit(SoundSample(80f, 100L))
        repo.emit(SoundSample(60f, 200L))
        // Pre-pause aggregate: min=40, max=80, avg=60.
        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        // Clear replay so the post-resume collector does not resurrect the pre-pause samples
        // (which would mask the missing-seed bug — the buggy fold-from-Empty would land on the
        // same min/max anyway just by replaying the buffer).
        repo.resetReplayCache()

        viewModel.onEvent(MeasureUiEvent.StartRequested)
        // Only ONE sample arrives at the resumed collector. Without a seed, the new fold starts
        // from `Accumulator.Empty` and a single 60 dB sample collapses min/max/avg all to 60.
        // With the SessionSeed wired, min stays 40, max stays 80, avg stays 60 ((180+60)/4=60).
        repo.emit(SoundSample(60f, 0L))

        val resumed = viewModel.state.value
        assertEquals(MeasurementPhase.Running, resumed.phase)
        assertEquals(40f, resumed.min)
        assertEquals(80f, resumed.max)
        assertEquals(60f, resumed.avg, 0.001f)
    }

    @Test
    fun `PauseRequested from Idle is a no-op so a background event does not promote Idle to Paused`() = runTest {
        // LifecycleEventEffect(ON_STOP) in MeasureScreen unconditionally sends PauseRequested; this
        // test asserts the ViewModel guards against that path so backgrounding an Idle screen does
        // not display "Paused 00:00 / 0.0 dB" with a disabled Reset button on return.
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
        )

        // Initial phase is Idle; no permission grant, no start.
        viewModel.onEvent(MeasureUiEvent.PauseRequested)

        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun `Reset clears the side-band sumDb-count so a fresh session after Reset does not see stale aggregate`() = runTest {
        // Without the Reset clearing sessionSumDb/sessionCount, a Reset → StartRequested cycle would
        // hand a non-empty seed (carrying the old sum/count) into the new fold, and the very first
        // sample of the "fresh" session would compute avg using stale terms.
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(80f, 0L))
        repo.emit(SoundSample(40f, 100L))

        viewModel.onEvent(MeasureUiEvent.ResetRequested)
        // Clear replay so the post-Reset collector starts on an empty upstream and only sees the
        // single new 50 dB sample emitted below. Otherwise the replay buffer would re-deliver
        // 80/40 to the new fold, masking whether sessionSumDb/sessionCount were actually cleared.
        repo.resetReplayCache()
        viewModel.onEvent(MeasureUiEvent.StartRequested)
        repo.emit(SoundSample(50f, 0L))

        val afterReset = viewModel.state.value
        // Single 50 dB sample after Reset → min/max/avg all 50, not influenced by the pre-reset 80/40.
        assertEquals(50f, afterReset.min)
        assertEquals(50f, afterReset.max)
        assertEquals(50f, afterReset.avg, 0.001f)
    }
}
