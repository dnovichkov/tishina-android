package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.SaveMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.fakes.FakeSettingsRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Drives `MeasureViewModel` through the happy-path "permission granted → start →
 * receive samples" sequence and asserts that accumulator state matches the
 * expectations from the use-case (min / avg / max).
 *
 * Uses an [UnconfinedTestDispatcher] so the `viewModelScope` coroutine that
 * collects from `StartMeasurementUseCase` is eagerly resumed every time the
 * fake repository emits — that keeps the test free of `advanceUntilIdle()`
 * book-keeping while still allowing Turbine to observe each intermediate state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelStartTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `start with granted permission updates current min max avg from emitted samples`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
            settingsRepository = FakeSettingsRepository(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        repo.emit(SoundSample(db = 60f, timestampMs = 0L))

        viewModel.state.test {
            // collect the most recent emission rather than every transient state
            val current = expectMostRecentItem()
            assertEquals(60f, current.current)
            assertEquals(60f, current.min)
            assertEquals(60f, current.max)
            assertEquals(60f, current.avg)
            assertEquals(MeasurementPhase.Running, current.phase)
        }
    }

    @Test
    fun `sequence 40 60 80 60 40 produces min 40 max 80 avg 56`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
            settingsRepository = FakeSettingsRepository(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        repo.emitAll(
            listOf(
                SoundSample(40f, 100L),
                SoundSample(60f, 200L),
                SoundSample(80f, 300L),
                SoundSample(60f, 400L),
                SoundSample(40f, 500L),
            ),
        )

        viewModel.state.test {
            val current = expectMostRecentItem()
            assertEquals(40f, current.current)
            assertEquals(40f, current.min)
            assertEquals(80f, current.max)
            assertEquals(56f, current.avg)
            assertEquals(500L, current.durationMs)
            assertTrue(current.recent.size == 5, "expected 5 samples in recent, got ${current.recent.size}")
        }
    }

    @Test
    fun `Start with already granted permission transitions to Running phase`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = MeasureViewModel(
            savedStateHandle = SavedStateHandle(),
            startMeasurement = StartMeasurementUseCase(repo),
            resetMeasurement = ResetMeasurementUseCase(),
            saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository()),
            settingsRepository = FakeSettingsRepository(),
        )

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        // PermissionResult(granted=true) already auto-starts; an explicit StartRequested
        // afterwards must be idempotent and leave phase = Running.
        viewModel.onEvent(MeasureUiEvent.StartRequested)

        assertEquals(MeasurementPhase.Running, viewModel.state.value.phase)
        assertEquals(PermissionState.Granted, viewModel.state.value.permissionState)
    }
}
