package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Phase 3 introduces a RAM-resident sample buffer in `MeasureViewModel` that the
 * Save flow ships to Room in a single transaction. The buffer must:
 *  - downsample arbitrary upstream rates to ~5 Hz (one sample per 200 ms);
 *  - reset on `ResetRequested`;
 *  - freeze on `PauseRequested` (no growth, no clear) and continue on Resume.
 *
 * The buffer is private; we observe it indirectly by triggering Save with the
 * `FakeMeasurementRepository` and inspecting the persisted samples.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelSampleBufferTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModelWithFakes(
        audio: FakeAudioRepository,
        measurements: FakeMeasurementRepository,
    ): MeasureViewModel = MeasureViewModel(
        savedStateHandle = SavedStateHandle(),
        startMeasurement = StartMeasurementUseCase(audio),
        resetMeasurement = ResetMeasurementUseCase(),
        saveMeasurement = SaveMeasurementUseCase(measurements),
        settingsRepository = FakeSettingsRepository(),
    )

    private suspend fun firstSavedSamples(store: FakeMeasurementRepository): List<SoundSample> {
        val id = store.observeSummaries().first().first().id
        return checkNotNull(store.getById(id)).samples
    }

    @Test
    fun `10 Hz upstream is downsampled to roughly 5 Hz - one sample every 200ms`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWithFakes(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        // 10 Hz = every 100 ms. After 1000 ms we expect ~5 buffered samples (200ms cadence).
        val emitted = (0..9).map { i -> SoundSample(db = 60f + i, timestampMs = i * 100L) }
        audio.emitAll(emitted)

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = "buffer-test", note = null))

        assertEquals(1, store.size(), "save should have written exactly one row")
        val buffered = firstSavedSamples(store)
        // 10 emissions every 100 ms → 5 buffered points (t=0, 200, 400, 600, 800).
        assertEquals(5, buffered.size, "expected 5 downsampled samples, got ${buffered.size}")
        assertEquals(0L, buffered.first().timestampMs)
        buffered.zipWithNext().forEach { (a, b) ->
            assertTrue(b.timestampMs - a.timestampMs >= 200L, "buffer cadence broken: $a → $b")
        }
    }

    @Test
    fun `20 Hz upstream is downsampled - every fourth sample makes it into the buffer`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWithFakes(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        // 20 Hz = every 50 ms. Over 1000 ms → 20 upstream → 5 buffered (200 ms spacing).
        val emitted = (0..19).map { i -> SoundSample(db = 50f, timestampMs = i * 50L) }
        audio.emitAll(emitted)

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = null))

        assertEquals(5, firstSavedSamples(store).size)
    }

    @Test
    fun `5 Hz upstream is preserved one-to-one in the buffer`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWithFakes(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        // 5 Hz = every 200 ms. All four samples should land in the buffer.
        val emitted = (0..3).map { i -> SoundSample(db = 70f, timestampMs = i * 200L) }
        audio.emitAll(emitted)

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = null))

        assertEquals(emitted, firstSavedSamples(store))
    }

    @Test
    fun `Reset clears the sample buffer - Save afterwards persists nothing`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWithFakes(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        audio.emit(SoundSample(70f, 200L))

        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        viewModel.onEvent(MeasureUiEvent.ResetRequested)

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        // No data → ShowSaveDialog should NOT be emitted; even if the test layer naively confirmed,
        // SaveMeasurementUseCase would reject empty samples and the store would stay empty.
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = null))

        assertEquals(0, store.size(), "Reset must clear the buffer so save persists nothing")
    }

    @Test
    fun `Pause freezes the buffer - Resume keeps growing without clearing`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val viewModel = viewModelWithFakes(audio, store)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        audio.emit(SoundSample(60f, 200L))
        // Pre-pause: two buffered samples.

        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        // Emit while paused — must NOT be added.
        audio.emit(SoundSample(99f, 400L))

        audio.resetReplayCache()
        viewModel.onEvent(MeasureUiEvent.StartRequested)
        // Post-resume sample — buffer continues, does NOT restart from empty.
        audio.emit(SoundSample(60f, 600L))

        viewModel.onEvent(MeasureUiEvent.PauseRequested)
        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = null))

        val buffered = firstSavedSamples(store)
        // Pre-pause 2 + post-resume 1 = 3; the 99f sample during pause must be absent.
        assertEquals(3, buffered.size)
        assertTrue(buffered.none { it.db == 99f }, "samples emitted during Pause leaked into buffer")
    }
}
