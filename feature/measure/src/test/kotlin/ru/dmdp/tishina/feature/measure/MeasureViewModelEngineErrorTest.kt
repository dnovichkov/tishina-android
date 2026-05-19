package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.repository.AudioRepository
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Verifies the `.catch { }` operator added to `startCollecting` so that an
 * AudioRecord acquisition failure (no usable source, permission revoked
 * mid-session, dead device object) is surfaced to the user via a snackbar
 * effect and the UI returns to `Idle` — rather than crashing the
 * viewModelScope and freezing the readout at "Running, 0.0 dB".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelEngineErrorTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private class FailingAudioRepository(private val cause: Throwable) : AudioRepository {
        override fun samples(config: MeasurementConfig): Flow<SoundSample> = flow { throw cause }
        override suspend fun isAvailable(): Boolean = true
    }

    @Test
    fun `engine error surfaces ShowSnackbar effect and transitions phase back to Idle`() = runTest {
        val repo = FailingAudioRepository(IllegalStateException("AudioRecord could not be acquired"))
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
        )

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

            val effect = awaitItem()
            assertTrue(effect is MeasureUiEffect.ShowSnackbar, "expected ShowSnackbar, got $effect")
            assertEquals(R.string.measure_engine_error, (effect as MeasureUiEffect.ShowSnackbar).messageRes)
            cancelAndIgnoreRemainingEvents()
        }

        // The UI must not be stuck in Running.
        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
    }
}
