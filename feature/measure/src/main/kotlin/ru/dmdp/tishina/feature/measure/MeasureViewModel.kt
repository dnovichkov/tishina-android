package ru.dmdp.tishina.feature.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SessionSeed
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.SaveMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import javax.inject.Inject

/**
 * Single source of truth for the Measure screen. Implements MVI-lite:
 *  - [state] — immutable [MeasureUiState] snapshot consumed by Compose;
 *  - [effects] — one-shot side effects (snackbar / permission request /
 *    settings intent) delivered through a buffered channel so they survive
 *    rotation without being replayed;
 *  - [onEvent] — entry point for UI intents.
 *
 * State persistence (NFR-5): every snapshot writes a small set of scalars to
 * [savedStateHandle]; on restore, the headline metrics come back and the
 * session resumes as [MeasurementPhase.Paused] rather than auto-rearming the
 * microphone after a system kill.
 *
 * Phase 2 ignores `MeasurementConfig` settings — uses defaults (A-weighting,
 * FAST time-weighting). Phase 4 will inject [SettingsRepository] and observe
 * config changes here.
 */
@HiltViewModel
class MeasureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val startMeasurement: StartMeasurementUseCase,
    private val resetMeasurement: ResetMeasurementUseCase,
    private val saveMeasurement: SaveMeasurementUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(restoreInitialState())

    /** Compose collects this as the source of truth for what to render. */
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    /**
     * Buffered so a tap immediately followed by a rotation never drops a
     * snackbar / permission request. `Channel.BUFFERED` gives us 64 slots —
     * more than enough headroom for the bursty user-driven cadence here.
     */
    private val effectChannel = Channel<MeasureUiEffect>(Channel.BUFFERED)
    val effects: Flow<MeasureUiEffect> = effectChannel.receiveAsFlow()

    /** Active subscription to the audio engine. Cancelled on Pause / Reset. */
    private var collectJob: Job? = null

    /**
     * Side-band accumulator state kept by the ViewModel so a Pause → Resume cycle continues the
     * session min / max / avg / duration / recent window instead of restarting from zero.
     *
     * `sumDb`/`count` are not part of the public `MeasurementSnapshot`, so they cannot be
     * recovered from the UI state alone — the ViewModel tracks them here, increments them on each
     * snapshot, and hands them back as a [SessionSeed] when Resume relaunches the upstream.
     *
     * Reset zeroes them; process death drops them (a restored session lands on Paused and only the
     * scalar snapshot is preserved — see plan "Известные ограничения" line 482).
     */
    private var sessionSumDb: Double = 0.0
    private var sessionCount: Long = 0L

    /**
     * 5 Hz RAM buffer of the on-going session. Drained into Room as part of `Save`.
     * `lastBufferedMs = -BUFFER_PERIOD_MS` so the very first snapshot always passes
     * the cadence test and produces a `t=0` entry.
     */
    private val sampleBuffer = mutableListOf<SoundSample>()
    private var lastBufferedMs: Long = -BUFFER_PERIOD_MS

    fun onEvent(event: MeasureUiEvent) {
        when (event) {
            is MeasureUiEvent.StartRequested -> handleStartRequested()
            is MeasureUiEvent.PauseRequested -> handlePauseRequested()
            is MeasureUiEvent.ResetRequested -> handleResetRequested()
            is MeasureUiEvent.SaveRequested -> handleSaveRequested()
            is MeasureUiEvent.SaveDialogConfirmed -> handleSaveDialogConfirmed(event)
            is MeasureUiEvent.SaveDialogDismissed -> Unit
            is MeasureUiEvent.PermissionResult -> handlePermissionResult(event)
            is MeasureUiEvent.PermissionRefreshed -> handlePermissionRefreshed(event)
        }
    }

    private fun handleStartRequested() {
        when (_state.value.permissionState) {
            PermissionState.Granted -> startCollecting()
            PermissionState.PermanentlyDenied -> effectChannel.trySend(MeasureUiEffect.OpenAppSettings)
            PermissionState.Unknown,
            PermissionState.Denied,
            -> effectChannel.trySend(MeasureUiEffect.RequestPermission)
        }
    }

    private fun handlePauseRequested() {
        // Guarded so ON_STOP-driven Pause cannot promote an Idle session to Paused. Idle has no
        // captured aggregate; landing in Paused there would show "Paused 00:00 / 0.0 dB" and a
        // disabled Reset button, which is confusing.
        if (_state.value.phase != MeasurementPhase.Running) return
        collectJob?.cancel()
        collectJob = null
        _state.update { it.copy(phase = MeasurementPhase.Paused) }
    }

    private fun handleResetRequested() {
        collectJob?.cancel()
        collectJob = null
        sessionSumDb = 0.0
        sessionCount = 0L
        sampleBuffer.clear()
        lastBufferedMs = -BUFFER_PERIOD_MS
        val empty = resetMeasurement()
        _state.update {
            // Preserve permissionState — the user already accepted RECORD_AUDIO.
            MeasureUiState(
                current = empty.currentDb,
                min = empty.minDb,
                max = empty.maxDb,
                avg = empty.avgDb,
                durationMs = empty.durationMs,
                recent = empty.recent,
                phase = MeasurementPhase.Idle,
                permissionState = it.permissionState,
            )
        }
        // Without this, a process kill *after* Reset would restore the pre-Reset scalars
        // (restoreInitialState returns Paused as soon as any KEY_* is present), so the user
        // would see "Paused, max=78.3" right after they explicitly cleared everything.
        clearPersistedScalars()
    }

    private fun handleSaveRequested() {
        if (sampleBuffer.isEmpty()) {
            effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_save_no_data))
            return
        }
        effectChannel.trySend(MeasureUiEffect.ShowSaveDialog)
    }

    private fun handleSaveDialogConfirmed(event: MeasureUiEvent.SaveDialogConfirmed) {
        // Whitespace-only title/note → null persisted. Matches DetailViewModel.saveNote so that
        // a draft of "   " typed on the Save dialog cannot survive as an invisible truthy value
        // in Room (which History card / Detail's NoteReadOnly would render as empty).
        val title = event.title?.takeIf { it.isNotBlank() }
        val note = event.note?.takeIf { it.isNotBlank() }
        // Defense in depth: the use-case and DAO both validate, but we report up-front so the user
        // does not eat a Room round-trip just to be told their title was one character too long.
        if (title != null && title.length > NewMeasurement.MAX_TITLE_LENGTH) {
            effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_save_title_too_long))
            return
        }
        if (note != null && note.length > NewMeasurement.MAX_NOTE_LENGTH) {
            effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_save_note_too_long))
            return
        }
        if (sampleBuffer.isEmpty()) {
            effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_save_no_data))
            return
        }
        val snapshot = _state.value
        val newMeasurement = NewMeasurement(
            createdAtEpochMs = System.currentTimeMillis(),
            durationMs = snapshot.durationMs,
            avgDb = snapshot.avg,
            // Sentinel infinities mean "no samples ever flowed in"; that case is blocked by the
            // empty-buffer guard above, so this branch only protects against future regressions
            // (e.g. a sample with NaN db landing in min/max and skipping the buffer guard).
            minDb = snapshot.min.takeIf { it.isFinite() } ?: snapshot.avg,
            maxDb = snapshot.max.takeIf { it.isFinite() } ?: snapshot.avg,
            title = title,
            note = note,
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.FAST,
            calibrationOffsetDb = 0f,
            sampleRateHz = AUDIO_SAMPLE_RATE_HZ,
            samples = sampleBuffer.toList(),
        )
        viewModelScope.launch {
            val result = saveMeasurement(newMeasurement)
            if (result.isSuccess) {
                effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_saved))
                handleResetRequested()
            } else {
                effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_save_failed))
            }
        }
    }

    private fun handlePermissionRefreshed(event: MeasureUiEvent.PermissionRefreshed) {
        // Only upgrade — never downgrade. A passive probe may report "not granted" before the
        // user has even seen the system dialog, which would erase a real PermanentlyDenied flag
        // captured during a prior request cycle. Downgrades go through PermissionResult only.
        if (event.granted && _state.value.permissionState != PermissionState.Granted) {
            _state.update { it.copy(permissionState = PermissionState.Granted) }
        }
    }

    private fun handlePermissionResult(event: MeasureUiEvent.PermissionResult) {
        when {
            event.granted -> {
                _state.update { it.copy(permissionState = PermissionState.Granted) }
                startCollecting()
            }
            event.shouldShowRationale -> {
                _state.update { it.copy(permissionState = PermissionState.Denied) }
                effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_permission_denied))
            }
            else -> {
                _state.update { it.copy(permissionState = PermissionState.PermanentlyDenied) }
                effectChannel.trySend(MeasureUiEffect.OpenAppSettings)
            }
        }
    }

    private fun startCollecting() {
        if (collectJob?.isActive == true) return
        // Resume = fresh upstream + seeded accumulator. Empty session = empty seed (sessionCount
        // stays 0). The seed carries the side-band sumDb/count plus the visible scalars so the
        // use-case's fold continues from the pre-pause aggregate instead of overwriting it on the
        // first new sample.
        val current = _state.value
        val seed = if (sessionCount == 0L) {
            SessionSeed.empty
        } else {
            SessionSeed(
                minDb = current.min,
                maxDb = current.max,
                sumDb = sessionSumDb,
                count = sessionCount,
                durationOffsetMs = current.durationMs,
                recent = current.recent,
            )
        }
        _state.update { it.copy(phase = MeasurementPhase.Running) }
        collectJob = viewModelScope.launch {
            startMeasurement(MeasurementConfig(), seed)
                .onEach { snapshot ->
                    // A SharedFlow-backed upstream may still flush a buffered item between
                    // `cancel()` and the subscriber unhooking. Guarding here keeps Pause atomic
                    // from the UI's perspective.
                    if (_state.value.phase != MeasurementPhase.Running) return@onEach
                    sessionSumDb += snapshot.currentDb.toDouble()
                    sessionCount += 1L
                    val elapsed = snapshot.durationMs - lastBufferedMs
                    if (elapsed >= BUFFER_PERIOD_MS) {
                        sampleBuffer += SoundSample(
                            db = snapshot.currentDb,
                            timestampMs = snapshot.durationMs,
                        )
                        lastBufferedMs = snapshot.durationMs
                    }
                    _state.update {
                        it.copy(
                            current = snapshot.currentDb,
                            min = snapshot.minDb,
                            max = snapshot.maxDb,
                            avg = snapshot.avgDb,
                            durationMs = snapshot.durationMs,
                            recent = snapshot.recent,
                        )
                    }
                    persistScalars(snapshot.currentDb, snapshot.minDb, snapshot.maxDb, snapshot.avgDb, snapshot.durationMs)
                }
                // Without this, an AudioRecord acquisition failure (no usable source / permission
                // revoked mid-session / dead device object) propagates to viewModelScope's
                // UncaughtExceptionHandler and the UI freezes at "Running, 0.0 dB" forever.
                // Re-throw CancellationException so structured concurrency keeps working.
                .catch { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(phase = MeasurementPhase.Idle) }
                    effectChannel.trySend(MeasureUiEffect.ShowSnackbar(R.string.measure_engine_error))
                }
                .collect()
        }
    }

    private fun persistScalars(current: Float, min: Float, max: Float, avg: Float, durationMs: Long) {
        savedStateHandle[KEY_CURRENT] = current
        savedStateHandle[KEY_MIN] = min
        savedStateHandle[KEY_MAX] = max
        savedStateHandle[KEY_AVG] = avg
        savedStateHandle[KEY_DURATION] = durationMs
    }

    private fun clearPersistedScalars() {
        savedStateHandle.remove<Float>(KEY_CURRENT)
        savedStateHandle.remove<Float>(KEY_MIN)
        savedStateHandle.remove<Float>(KEY_MAX)
        savedStateHandle.remove<Float>(KEY_AVG)
        savedStateHandle.remove<Long>(KEY_DURATION)
    }

    private fun restoreInitialState(): MeasureUiState =
        // All-or-nothing: only restore when every headline field is present, otherwise treat as
        // a fresh launch. Partial restores would produce confusing "min set, max unset" UIs.
        restoredScalars()?.let { scalars ->
            MeasureUiState(
                current = scalars.current,
                min = scalars.min,
                max = scalars.max,
                avg = scalars.avg,
                durationMs = scalars.durationMs,
                // Restored sessions land in Paused so the engine does not silently re-arm.
                phase = MeasurementPhase.Paused,
            )
        } ?: MeasureUiState()

    // Guard-clause style is the clearest way to express "all five keys must be present, or treat as
    // fresh launch". The alternative — nested `let` chains — buries the intent under indentation,
    // and folding into a list erases the per-field type. Suppressing ReturnCount locally is the
    // least-bad trade-off here.
    @Suppress("ReturnCount")
    private fun restoredScalars(): RestoredScalars? {
        val current = savedStateHandle.get<Float>(KEY_CURRENT) ?: return null
        val min = savedStateHandle.get<Float>(KEY_MIN) ?: return null
        val max = savedStateHandle.get<Float>(KEY_MAX) ?: return null
        val avg = savedStateHandle.get<Float>(KEY_AVG) ?: return null
        val durationMs = savedStateHandle.get<Long>(KEY_DURATION) ?: return null
        return RestoredScalars(current, min, max, avg, durationMs)
    }

    private data class RestoredScalars(val current: Float, val min: Float, val max: Float, val avg: Float, val durationMs: Long)

    companion object {
        internal const val KEY_CURRENT = "measure_current_db"
        internal const val KEY_MIN = "measure_min_db"
        internal const val KEY_MAX = "measure_max_db"
        internal const val KEY_AVG = "measure_avg_db"
        internal const val KEY_DURATION = "measure_duration_ms"

        // 5 Hz buffer period in milliseconds — one persisted SoundSample per 200 ms keeps the
        // History sparkline and the Detail full-graph faithful while bounding RAM to ≈ 12 B × 5 Hz
        // × 8 h = 1.7 MB (per plan "RAM-буфер 5 Гц"). Constants in MeasurementConfig live in
        // :core:domain and are nominal SPL settings, not buffer cadence, so we keep this here.
        private const val BUFFER_PERIOD_MS = 200L

        // Audio capture rate used by `:core:audio/AudioRecordPcmSource` (FR-23 / NFR-9). The Save
        // path persists it per-measurement so Phase 4 settings changes never retroactively reframe
        // historical data.
        private const val AUDIO_SAMPLE_RATE_HZ = 48_000
    }
}
