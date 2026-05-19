package ru.dmdp.tishina.core.audio

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.dmdp.tishina.core.audio.dsp.AudioProcessorFactory
import ru.dmdp.tishina.core.audio.source.PcmAudioSource
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.repository.AudioRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [AudioRepository] backed by a [PcmAudioSource] and the [AudioProcessorFactory] DSP
 * chain. Each [samples] collection allocates a fresh [ru.dmdp.tishina.core.audio.dsp.AudioProcessor]
 * so per-session state (IIR delay registers, time-weighted RMS accumulator) never leaks across
 * sessions — a Start / Pause / Start cycle gets a clean baseline.
 *
 * Timestamps are derived from [System.nanoTime] rather than wall-clock time so they remain monotonic
 * under NTP correction, DST jumps, and time-zone changes — important for long measurement
 * sessions and for the in-memory `recent` window the UI plots. The first emission is anchored at
 * `timestampMs = 0`; subsequent emissions report elapsed milliseconds since the first chunk arrived
 * from the source.
 *
 * Cooperative cancellation is delegated to the flow builder: when the consumer cancels (via
 * `take`, `withTimeout`, scope cancellation, …), the next `emit` throws `CancellationException`
 * and the `try-finally` inside [PcmAudioSource.samples] releases the underlying recorder.
 */
@Singleton
class AudioRepositoryImpl @Inject internal constructor(
    private val source: PcmAudioSource,
    private val processorFactory: AudioProcessorFactory,
    @ApplicationContext private val context: Context,
) : AudioRepository {

    override fun samples(config: MeasurementConfig): Flow<SoundSample> = flow {
        val processor = processorFactory.create(source.sampleRateHz, config)
        var startNanos: Long = -1L
        source.samples().collect { chunk ->
            val db = processor.process(chunk, config.calibrationOffsetDb)
            val now = System.nanoTime()
            if (startNanos < 0L) startNanos = now
            val timestampMs = (now - startNanos) / NANOS_PER_MILLI
            emit(SoundSample(db = db, timestampMs = timestampMs))
        }
    }

    override suspend fun isAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
