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
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Covers the RECORD_AUDIO permission state machine driven by `PermissionResult`.
 *
 * Android's contract: after `requestPermission` returns, `shouldShowRationale`
 * is true iff the user denied once without "Don't ask again". `(false, false)`
 * therefore means **permanently denied** — we must route to system settings
 * because subsequent in-app requests would silently no-op.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelPermissionFlowTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun buildViewModel(repo: FakeAudioRepository = FakeAudioRepository()) = MeasureViewModel(
        savedStateHandle = SavedStateHandle(),
        startMeasurement = StartMeasurementUseCase(repo),
        resetMeasurement = ResetMeasurementUseCase(),
    )

    @Test
    fun `StartRequested with Unknown permission emits RequestPermission effect`() = runTest {
        val viewModel = buildViewModel()

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.StartRequested)
            assertEquals(MeasureUiEffect.RequestPermission, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun `PermissionResult granted transitions to Granted and auto-starts measurement`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = buildViewModel(repo)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        assertEquals(PermissionState.Granted, viewModel.state.value.permissionState)
        assertEquals(MeasurementPhase.Running, viewModel.state.value.phase)

        // sanity: subscription was actually opened on the repo
        repo.emit(SoundSample(50f, 0L))
        assertEquals(50f, viewModel.state.value.current)
    }

    @Test
    fun `PermissionResult denied with rationale transitions to Denied and emits Snackbar`() = runTest {
        val viewModel = buildViewModel()

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = false, shouldShowRationale = true))
            val effect = awaitItem()
            assertTrue(effect is MeasureUiEffect.ShowSnackbar, "expected ShowSnackbar but got $effect")
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(PermissionState.Denied, viewModel.state.value.permissionState)
        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun `PermissionResult denied without rationale transitions to PermanentlyDenied and emits OpenAppSettings`() = runTest {
        val viewModel = buildViewModel()

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = false, shouldShowRationale = false))
            assertEquals(MeasureUiEffect.OpenAppSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permissionState)
    }

    @Test
    fun `PermissionRefreshed granted upgrades PermanentlyDenied to Granted without auto-start`() = runTest {
        val viewModel = buildViewModel()

        viewModel.effects.test {
            // Land in PermanentlyDenied first — this fires an OpenAppSettings effect we consume.
            viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = false, shouldShowRationale = false))
            assertEquals(MeasureUiEffect.OpenAppSettings, awaitItem())
            assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permissionState)

            // User went to Settings and granted; the lifecycle observer fires this on ON_RESUME.
            viewModel.onEvent(MeasureUiEvent.PermissionRefreshed(granted = true))
            // Must NOT auto-start (the user did not tap Start) — no further effects.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(PermissionState.Granted, viewModel.state.value.permissionState)
        assertEquals(MeasurementPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun `PermissionRefreshed granted is a no-op when already Granted`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = buildViewModel(repo)
        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        val phaseBefore = viewModel.state.value.phase

        viewModel.onEvent(MeasureUiEvent.PermissionRefreshed(granted = true))

        // Phase must not change — in particular the refresh must not double-launch the collect job.
        assertEquals(phaseBefore, viewModel.state.value.phase)
        assertEquals(PermissionState.Granted, viewModel.state.value.permissionState)
    }

    @Test
    fun `PermissionRefreshed not-granted does not downgrade Granted state`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        viewModel.onEvent(MeasureUiEvent.PermissionRefreshed(granted = false))

        // A passive probe never overrides authoritative state from PermissionResult.
        assertEquals(PermissionState.Granted, viewModel.state.value.permissionState)
    }

    @Test
    fun `StartRequested with already Granted permission does not re-emit RequestPermission`() = runTest {
        val repo = FakeAudioRepository()
        val viewModel = buildViewModel(repo)

        // Move to Granted first.
        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        viewModel.effects.test {
            viewModel.onEvent(MeasureUiEvent.StartRequested)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
