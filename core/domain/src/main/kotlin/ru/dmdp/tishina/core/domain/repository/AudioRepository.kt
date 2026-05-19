package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SoundSample

/**
 * Abstracts the audio engine away from the rest of the app.
 *
 * The real implementation in `:core:audio` wraps `AudioRecord`, runs the DSP
 * pipeline (DC block → A/Z weighting → RMS → SPL) and emits a stream of
 * `SoundSample`. Tests use `FakeAudioRepository` from `:core:testing` instead,
 * which lets us cover use-cases and ViewModels without an emulator.
 *
 * Collecting [samples] starts capture; cancelling the collector stops it and
 * releases the underlying `AudioRecord`.
 */
interface AudioRepository {

    /**
     * Cold flow that begins capturing audio when collected and emits a
     * `SoundSample` per processed PCM chunk (typically ~10 Hz).
     *
     * @param config frequency/time weighting and calibration offset to apply.
     */
    fun samples(config: MeasurementConfig): Flow<SoundSample>

    /**
     * Whether the device exposes a usable microphone. Checked at cold start so
     * we can degrade gracefully on tablets / Android Auto heads / emulators
     * without `FEATURE_MICROPHONE`.
     */
    suspend fun isAvailable(): Boolean
}
