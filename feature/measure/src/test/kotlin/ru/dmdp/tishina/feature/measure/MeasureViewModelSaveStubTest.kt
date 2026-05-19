package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
 * Phase 2 ships the Save FAB visually but persistence comes in Phase 3.
 * The ViewModel must:
 *  - emit a `ShowSnackbar` effect pointing at the "history coming in next
 *    phase" string;
 *  - NOT mutate state (no spurious "saving" indicator);
 *  - emit the effect exactly once per tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelSaveStubTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `SaveRequested emits exactly one ShowSnackbar with the phase 2 stub string`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        repo.emit(SoundSample(60f, 0L))
        val stateBefore = viewModel.state.value

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.SaveRequested)
            val effect = awaitItem()
            assertEquals(
                MeasureUiEffect.ShowSnackbar(R.string.measure_save_unavailable_phase2),
                effect,
            )
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // State unchanged.
        assertEquals(stateBefore, viewModel.state.value)
    }
}
