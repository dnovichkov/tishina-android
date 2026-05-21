package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.SaveMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeAudioRepository
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.fakes.FakeSettingsRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Phase 4 Task 8: `MeasureViewModel` must consume [MeasurementConfig] from
 * `SettingsRepository` instead of the Phase 2 hardcoded default. The config is
 * snapshotted at `Start` time — changes made mid-session do not interrupt the
 * active recording; they apply on the *next* Start. This matches the
 * "immutable seed of a session" semantics also used by [SessionSeed].
 *
 * The hardcoded `weighting` / `timeWeighting` / `calibrationOffsetDb` fields
 * on `NewMeasurement` (set in `handleSaveDialogConfirmed`) must likewise come
 * from the same snapshot — Save persists the config that was active when the
 * session started, so historical rows are immune to later setting changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasureViewModelSettingsIntegrationTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModelWith(
        audio: FakeAudioRepository = FakeAudioRepository(),
        store: FakeMeasurementRepository = FakeMeasurementRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): MeasureViewModel = MeasureViewModel(
        savedStateHandle = SavedStateHandle(),
        startMeasurement = StartMeasurementUseCase(audio),
        resetMeasurement = ResetMeasurementUseCase(),
        saveMeasurement = SaveMeasurementUseCase(store),
        settingsRepository = settings,
    )

    @Test
    fun `Start uses calibration offset and time weighting from SettingsRepository`() = runTest {
        val audio = FakeAudioRepository()
        val settings = FakeSettingsRepository(
            initialConfig = MeasurementConfig(
                frequencyWeighting = FrequencyWeighting.A,
                timeWeighting = TimeWeighting.SLOW,
                calibrationOffsetDb = 5f,
            ),
        )
        val viewModel = viewModelWith(audio = audio, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        // FakeAudioRepository records the config handed to `samples(config)` — verify the snapshot
        // pulled from SettingsRepository (not the Phase-2 `MeasurementConfig()` default) flowed in.
        val applied = audio.lastConfig
        assertNotNull(applied, "Start must invoke samples() with a config")
        assertEquals(5f, applied!!.calibrationOffsetDb)
        assertEquals(TimeWeighting.SLOW, applied.timeWeighting)
        assertEquals(FrequencyWeighting.A, applied.frequencyWeighting)
    }

    @Test
    fun `default settings produce a default MeasurementConfig (parity with Phase 2 behaviour)`() = runTest {
        val audio = FakeAudioRepository()
        val viewModel = viewModelWith(audio = audio)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))

        assertEquals(MeasurementConfig(), audio.lastConfig)
    }

    @Test
    fun `config change during active session does not restart upstream`() = runTest {
        // Hot-reload of an in-flight session is deferred to v1.2 (plan "Известные ограничения").
        // The contract for Phase 4 is: changes apply at the next Start, not retroactively.
        val audio = FakeAudioRepository()
        val settings = FakeSettingsRepository()
        val viewModel = viewModelWith(audio = audio, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        val configAtStart = audio.lastConfig

        // Mutate calibration mid-session.
        settings.seed(config = MeasurementConfig(calibrationOffsetDb = 10f, timeWeighting = TimeWeighting.SLOW))

        // The active samples() flow must still be the original one — no new lastConfig recorded.
        // (`lastConfig` is set in `onStart`; a re-subscribe would overwrite it.)
        audio.emit(SoundSample(70f, 200L))
        assertEquals(configAtStart, audio.lastConfig, "active session must not pick up new config")
        assertEquals(MeasurementPhase.Running, viewModel.state.value.phase)
    }

    @Test
    fun `Resume after Pause keeps the original session config (no mid-session reframing)`() = runTest {
        // Contract: a session has exactly one config from Start to Save / Reset. Resume continues
        // the same session — it must NOT silently swap calibration mid-recording, because Save
        // persists only one `calibrationOffsetDb` per row and pre-pause samples would no longer
        // match it. The "next config wins" semantics apply only after Reset (see next test).
        val audio = FakeAudioRepository()
        val settings = FakeSettingsRepository(
            initialConfig = MeasurementConfig(calibrationOffsetDb = 2f),
        )
        val viewModel = viewModelWith(audio = audio, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        viewModel.onEvent(MeasureUiEvent.PauseRequested)

        settings.seed(config = MeasurementConfig(calibrationOffsetDb = 7.5f, timeWeighting = TimeWeighting.SLOW))
        audio.resetReplayCache()
        viewModel.onEvent(MeasureUiEvent.StartRequested)

        val configAtResume = audio.lastConfig
        assertNotNull(configAtResume)
        assertEquals(2f, configAtResume!!.calibrationOffsetDb, "Resume must preserve original session config")
    }

    @Test
    fun `fresh Start after Reset picks up updated config from settings`() = runTest {
        // After Reset, the next Start is a *new* session, so it must re-sample SettingsRepository.
        // Without the Reset clearing `activeSessionConfig`, the new session would silently inherit
        // the previous session's calibration even though the user has tweaked the slider in between.
        val audio = FakeAudioRepository()
        val settings = FakeSettingsRepository(
            initialConfig = MeasurementConfig(calibrationOffsetDb = 2f),
        )
        val viewModel = viewModelWith(audio = audio, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        viewModel.onEvent(MeasureUiEvent.ResetRequested)

        settings.seed(config = MeasurementConfig(calibrationOffsetDb = 7.5f, timeWeighting = TimeWeighting.SLOW))
        audio.resetReplayCache()
        viewModel.onEvent(MeasureUiEvent.StartRequested)

        val configAtFreshStart = audio.lastConfig
        assertNotNull(configAtFreshStart)
        assertEquals(7.5f, configAtFreshStart!!.calibrationOffsetDb)
        assertEquals(TimeWeighting.SLOW, configAtFreshStart.timeWeighting)
    }

    @Test
    fun `Save persists calibration offset and time weighting from active session config`() = runTest {
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val settings = FakeSettingsRepository(
            initialConfig = MeasurementConfig(
                frequencyWeighting = FrequencyWeighting.A,
                timeWeighting = TimeWeighting.SLOW,
                calibrationOffsetDb = -3.5f,
            ),
        )
        val viewModel = viewModelWith(audio = audio, store = store, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))
        audio.emit(SoundSample(62f, 200L))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = "kitchen", note = null))

        assertEquals(1, store.size())
        val summary = store.observeSummaries().first().first()
        val saved = checkNotNull(store.getById(summary.id))
        assertEquals(-3.5f, saved.calibrationOffsetDb, "Save must persist the offset active when session started")
        assertEquals(TimeWeighting.SLOW, saved.timeWeighting)
        assertEquals(FrequencyWeighting.A, saved.weighting)
    }

    @Test
    fun `Save persists config snapshot from session Start, not later setting changes`() = runTest {
        // Regression guard: if Save read `settings.config` at Save time instead of holding the
        // Start-time snapshot, this assertion would fail with offset=8f (the post-start mutation).
        val audio = FakeAudioRepository()
        val store = FakeMeasurementRepository()
        val settings = FakeSettingsRepository(
            initialConfig = MeasurementConfig(calibrationOffsetDb = 2f),
        )
        val viewModel = viewModelWith(audio = audio, store = store, settings = settings)

        viewModel.onEvent(MeasureUiEvent.PermissionResult(granted = true, shouldShowRationale = false))
        audio.emit(SoundSample(60f, 0L))

        // Mutate calibration mid-session — should NOT influence the row written by Save.
        settings.seed(config = MeasurementConfig(calibrationOffsetDb = 8f))

        viewModel.onEvent(MeasureUiEvent.SaveRequested)
        viewModel.onEvent(MeasureUiEvent.SaveDialogConfirmed(title = null, note = null))

        val summary = store.observeSummaries().first().first()
        val saved = checkNotNull(store.getById(summary.id))
        assertEquals(2f, saved.calibrationOffsetDb, "Save must not retroactively pick up mid-session config changes")
    }
}
