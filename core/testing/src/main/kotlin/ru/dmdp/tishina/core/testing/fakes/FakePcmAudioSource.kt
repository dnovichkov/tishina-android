package ru.dmdp.tishina.core.testing.fakes

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import ru.dmdp.tishina.core.audio.source.PcmAudioSource
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * In-memory [PcmAudioSource] for testing the full audio pipeline without an
 * `AudioRecord` or emulator.
 *
 * Backed by a hot [MutableSharedFlow] with a small replay buffer so chunks
 * pushed before a collector subscribes are still observable. Two driver APIs:
 *
 * - [emitBuffer] — push a raw `ShortArray` chunk verbatim. Useful when a test
 *   already owns scripted PCM (e.g. silence, full-scale, deterministic fixtures).
 *
 * - [emitTone] — generate a sine wave at a target frequency / amplitude
 *   (expressed in dB SPL against the same anchor the production `SplCalculator`
 *   uses) and emit it as one chunk. Useful for integration-style assertions
 *   such as "1 kHz at 90 dB SPL should round-trip through the DSP chain back to
 *   ≈ 90 dB".
 *
 * [cancelCount] lets tests verify the cold-flow surface — specifically that
 * cancelling collection (e.g. via `Flow.take`) propagates through to the source
 * (NFR-5).
 */
class FakePcmAudioSource(initialSampleRateHz: Int = DEFAULT_SAMPLE_RATE, private val unprocessedSupported: Boolean = true) :
    PcmAudioSource {

    private val flow = MutableSharedFlow<ShortArray>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    private var rate: Int = initialSampleRateHz

    override val sampleRateHz: Int get() = rate

    /** Number of times a collector cancelled / completed the [samples] flow. */
    @Volatile
    var cancelCount: Int = 0
        private set

    override fun samples(): Flow<ShortArray> =
        flow.asSharedFlow()
            .onCompletion { cancelCount += 1 }

    override suspend fun isUnprocessedSupported(): Boolean = unprocessedSupported

    /** Push a single PCM chunk to subscribed collectors (or buffer it for later). */
    suspend fun emitBuffer(buffer: ShortArray) {
        flow.emit(buffer)
    }

    /**
     * Generate and emit a sine tone matching the [SplCalculator] reference anchor.
     *
     * Production `SplCalculator` is anchored at "RMS 0.0763 (full-scale × 0.0763) ≈
     * 90 dB SPL". For a sine of amplitude `A`, RMS is `A / √2`, so to hit a target
     * SPL of [amplitudeDb] the peak amplitude becomes:
     *
     * ```
     * A = REFERENCE_RMS · 10^((amplitudeDb − ANCHOR_DB) / 20) · √2
     * ```
     *
     * Multiplied by 32 768 to land in PCM 16-bit short range. Clamped to ±32767
     * so callers asking for unrealistic levels (≥ 110 dB at a single sample)
     * don't overflow into negative numbers.
     */
    suspend fun emitTone(frequencyHz: Float, amplitudeDb: Float, durationMs: Int) {
        val sampleCount = (rate.toLong() * durationMs / MS_PER_SECOND).toInt()
        if (sampleCount <= 0) return
        val rmsTarget = REFERENCE_RMS * DB_BASE.pow((amplitudeDb - ANCHOR_DB) / DB_AMPLITUDE_DIVISOR)
        val peakNormalised = rmsTarget * SQRT_TWO
        val peakShort = (peakNormalised * SHORT_FULL_SCALE).coerceIn(-PCM_MAX, PCM_MAX)
        val buffer = ShortArray(sampleCount)
        val angularStep = TWO_PI * frequencyHz / rate
        for (i in 0 until sampleCount) {
            val s = sin(angularStep * i) * peakShort
            buffer[i] = s.toInt().toShort()
        }
        flow.emit(buffer)
    }

    /** Override the captured sample rate; tests use this to assert 44.1 kHz fallbacks. */
    fun setSampleRate(rateHz: Int) {
        rate = rateHz
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val REPLAY_BUFFER = 8
        const val EXTRA_BUFFER = 32
        const val MS_PER_SECOND = 1_000
        const val SHORT_FULL_SCALE = 32_768.0
        const val PCM_MAX = 32_767.0
        val TWO_PI = 2 * PI
        val SQRT_TWO = kotlin.math.sqrt(2.0)

        /** AOSP close-talk anchor: RMS=2500/32768 ↔ 90 dB SPL — must match `SplCalculator`. */
        const val ANCHOR_DB = 90.0
        const val REFERENCE_RMS = 2_500.0 / 32_768.0

        /** Base of the decibel exponential: `10^(dB / 20)`. */
        const val DB_BASE: Double = 10.0

        /** Amplitude-form decibel divisor: dB = 20·log10(ratio), so inverse uses ÷ 20. */
        const val DB_AMPLITUDE_DIVISOR: Double = 20.0
    }
}
