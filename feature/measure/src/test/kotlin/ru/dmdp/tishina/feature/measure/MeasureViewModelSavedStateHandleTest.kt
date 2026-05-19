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
 * NFR-5: a process-death-and-restore must preserve `min`, `max`, `avg`,
 * `durationMs` so the user does not lose the headline numbers of the session
 * they just paused. The transient `recent` chart series is *not* restored
 * (would balloon the saved bundle) — the user is expected to know they paused
 * and resumed.
 *
 * The restored phase is `Paused` rather than `Running` so the engine does
 * not silently re-attach to the microphone after a system kill.
 *
 * `SavedStateHandle` accepts primitive keys directly in unit tests — no
 * Robolectric needed because we only stash `Float` / `Long`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelSavedStateHandleTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `state is persisted to SavedStateHandle after each sample`() = runTest {
        val repo = FakeAudioRepository()
        val handle = SavedStateHandle()
        val viewModel = MeasureViewModel(
            savedStateHandle = handle,
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(40f, 100L))
        repo.emit(SoundSample(80f, 200L))

        // Headline metrics must have made it into the handle so process-death restores them.
        assertEquals(80f, handle.get<Float>(MeasureViewModel.KEY_CURRENT))
        assertEquals(40f, handle.get<Float>(MeasureViewModel.KEY_MIN))
        assertEquals(80f, handle.get<Float>(MeasureViewModel.KEY_MAX))
        assertEquals(60f, handle.get<Float>(MeasureViewModel.KEY_AVG))
        assertEquals(200L, handle.get<Long>(MeasureViewModel.KEY_DURATION))
    }

    @Test
    fun `ViewModel restores headline metrics on init and starts in Paused phase`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                MeasureViewModel.KEY_CURRENT to 72f,
                MeasureViewModel.KEY_MIN to 35f,
                MeasureViewModel.KEY_MAX to 95f,
                MeasureViewModel.KEY_AVG to 65f,
                MeasureViewModel.KEY_DURATION to 12_345L,
            ),
        )
        val viewModel = MeasureViewModel(
            savedStateHandle = handle,
            startMeasurement = StartMeasurementUseCase(FakeAudioRepository()),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        val state = viewModel.state.value
        assertEquals(72f, state.current)
        assertEquals(35f, state.min)
        assertEquals(95f, state.max)
        assertEquals(65f, state.avg)
        assertEquals(12_345L, state.durationMs)
        // Restored sessions resume as Paused — the user must explicitly tap Start to re-arm the mic.
        assertEquals(MeasurementPhase.Paused, state.phase)
    }
}
