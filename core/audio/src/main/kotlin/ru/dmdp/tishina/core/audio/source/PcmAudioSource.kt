package ru.dmdp.tishina.core.audio.source

import kotlinx.coroutines.flow.Flow

/**
 * Source of raw 16-bit mono PCM audio chunks driving the DSP pipeline.
 *
 * Two implementations live behind this interface:
 *
 * - [AudioRecordPcmSource] — production, wraps Android `AudioRecord` with sensible
 *   `AudioSource` and sample-rate fallbacks (UNPROCESSED → VOICE_RECOGNITION → MIC,
 *   48 kHz → 44.1 kHz). Acquires the microphone when [samples] is collected and
 *   releases it when collection ends or the consumer cancels.
 *
 * - `FakePcmAudioSource` (in `:core:testing`, added in Task 8) — emits scripted
 *   tones for `AudioRepositoryImpl` and ViewModel tests so the full chain can be
 *   exercised on the JVM without a microphone.
 *
 * Concrete classes guarantee:
 *
 * - [sampleRateHz] reports the rate actually being captured (selected lazily on
 *   first [samples] collection; defaults to 48 000 Hz where supported).
 * - [samples] is a cold flow — collecting it starts capture, cancelling stops and
 *   releases the underlying recorder.
 */
interface PcmAudioSource {

    /** Captured PCM sample rate (Hz). Stable for the lifetime of an instance. */
    val sampleRateHz: Int

    /**
     * Cold flow of PCM 16-bit mono chunks. Each emission is a fresh `ShortArray`
     * sized roughly to the AudioRecord min buffer (≥ 100 ms of audio), so
     * consumers see ~10 Hz updates at 48 kHz.
     */
    fun samples(): Flow<ShortArray>

    /**
     * Whether the device advertises `AudioSource.UNPROCESSED` support via
     * `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`. Used by the
     * source-selection logic to skip the UNPROCESSED probe on devices that have
     * already declared it unavailable, saving an `AudioRecord` allocation.
     */
    suspend fun isUnprocessedSupported(): Boolean
}
