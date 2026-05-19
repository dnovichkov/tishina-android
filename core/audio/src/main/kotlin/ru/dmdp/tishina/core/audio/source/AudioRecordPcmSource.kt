package ru.dmdp.tishina.core.audio.source

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import ru.dmdp.tishina.core.audio.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [PcmAudioSource] backed by `android.media.AudioRecord`.
 *
 * **Source-selection priority** (per spec § 10):
 *   1. `AudioSource.UNPROCESSED` — skips Android's built-in AGC / noise suppression, the only
 *      way to get raw mic samples suitable for SPL measurement. Available API 24+ and only
 *      when `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED == "true"`.
 *   2. `VOICE_RECOGNITION` — second-best; most OEMs disable AGC for this source.
 *   3. `MIC` — last resort; AGC/NS may distort low- and high-amplitude regions.
 *
 * **Sample-rate priority**: 48 000 Hz (mandatory per AOSP CDD § 7.8.3 since API 21) →
 * 44 100 Hz fallback for devices that reject 48 kHz.
 *
 * **Buffer size**: max of `AudioRecord.getMinBufferSize` and ~100 ms of audio
 * (`sampleRate / 10 × 2` bytes). The 100 ms target gives the consumer roughly 10 emissions/s,
 * matching the spec's NFR-2 (10 Hz UI refresh).
 *
 * **Lifecycle**: the flow returned by [samples] is cold; collection allocates and starts
 * `AudioRecord`, cancellation `stop()`s and `release()`s it via the `try-finally` inside
 * the flow builder. Multiple concurrent collections allocate separate recorders (one mic per
 * mic, so this will block on hardware — practical clients use one collector at a time).
 *
 * Heavy work (`read()`) runs on [Dispatchers.IO] via `flowOn`.
 */
@Singleton
class AudioRecordPcmSource @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val sessionFactory: AudioRecordSessionFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PcmAudioSource {

    private val chosenSampleRate: Int by lazy { chooseSampleRate() }

    override val sampleRateHz: Int get() = chosenSampleRate

    override suspend fun isUnprocessedSupported(): Boolean =
        AudioRecordCheck.isUnprocessedSupported(context)

    override fun samples(): Flow<ShortArray> = flow {
        val sampleRate = chosenSampleRate
        val bufferBytes = computeBufferSizeBytes(sampleRate)
        val session = openSession(sampleRate, bufferBytes)
            ?: error(
                "AudioRecord could not be acquired on any of " +
                    "UNPROCESSED/VOICE_RECOGNITION/MIC sources",
            )
        try {
            session.startRecording()
            val chunk = ShortArray(bufferBytes / BYTES_PER_SAMPLE)
            while (currentCoroutineContext().isActive) {
                val read = session.read(chunk, 0, chunk.size)
                if (read > 0) {
                    // copyOf so consumers can hold the array safely while we refill chunk in-place.
                    emit(chunk.copyOf(read))
                } else if (read < 0) {
                    // ERROR_INVALID_OPERATION / ERROR_BAD_VALUE / ERROR_DEAD_OBJECT — stop the
                    // flow rather than busy-loop on a broken recorder.
                    break
                }
            }
        } finally {
            session.stop()
            session.release()
        }
    }.flowOn(ioDispatcher)

    private fun chooseSampleRate(): Int =
        SUPPORTED_SAMPLE_RATES.firstOrNull { rate ->
            val size = sessionFactory.getMinBufferSize(rate)
            size > 0
        } ?: error(
            "No supported PCM sample rate on this device " +
                "(tried ${SUPPORTED_SAMPLE_RATES.joinToString()})",
        )

    private fun computeBufferSizeBytes(sampleRate: Int): Int {
        val minBytes = sessionFactory.getMinBufferSize(sampleRate)
        val tenHzBytes = (sampleRate / TARGET_UPDATES_PER_SECOND) * BYTES_PER_SAMPLE
        return maxOf(minBytes, tenHzBytes)
    }

    private fun openSession(sampleRate: Int, bufferBytes: Int): AudioRecordSession? {
        val unprocessed = AudioRecordCheck.isUnprocessedSupported(context)
        val candidates = if (unprocessed) SOURCE_PRIORITY else SOURCE_PRIORITY.drop(1)
        return candidates.firstNotNullOfOrNull { source ->
            sessionFactory.create(source, sampleRate, bufferBytes)
        }
    }

    internal companion object {
        /** Preferred sample rates in fallback order. 48 kHz is CDD-mandatory; 44.1 kHz is the safety net. */
        val SUPPORTED_SAMPLE_RATES = listOf(48_000, 44_100)

        /** Fallback chain: try UNPROCESSED first, then VOICE_RECOGNITION, then MIC. */
        val SOURCE_PRIORITY = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )

        /** Each AudioRecord chunk targets ~100 ms of audio so the UI can refresh at 10 Hz. */
        const val TARGET_UPDATES_PER_SECOND = 10

        /** PCM 16-bit mono → 2 bytes per sample. */
        const val BYTES_PER_SAMPLE = 2

        /**
         * Re-exported for tests so a single source of truth governs both production and tests.
         * Mirrors the AudioRecord constants without forcing test code to depend on Android.
         */
        val ERROR_BAD_VALUE: Int = AudioRecord.ERROR_BAD_VALUE
    }
}
