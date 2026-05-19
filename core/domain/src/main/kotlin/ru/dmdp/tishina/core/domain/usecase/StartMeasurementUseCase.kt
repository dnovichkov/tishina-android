package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.repository.AudioRepository
import kotlin.math.max
import kotlin.math.min

/**
 * Starts a measurement session and exposes [MeasurementSnapshot] state.
 *
 * Composes [AudioRepository.samples] into an accumulator that runs min / avg /
 * max over the whole session and keeps a 60-second tail of samples for the
 * line chart. The session-wide arithmetic mean requires an internal running
 * sum + count pair, so we fold through a private [Accumulator] and project to
 * the public [MeasurementSnapshot] on each emission.
 */
class StartMeasurementUseCase(private val audioRepository: AudioRepository) {

    operator fun invoke(config: MeasurementConfig): Flow<MeasurementSnapshot> =
        audioRepository.samples(config)
            .runningFold(Accumulator.Empty) { acc, sample -> acc.update(sample) }
            // runningFold emits the initial seed first; drop it so consumers only
            // see snapshots derived from real samples.
            .drop(1)
            .map { it.snapshot }

    /**
     * Internal fold state. Carries sum+count so we can derive the arithmetic
     * mean without storing the full session in memory.
     */
    private data class Accumulator(val snapshot: MeasurementSnapshot, val sumDb: Double, val count: Long) {
        fun update(sample: SoundSample): Accumulator {
            val newCount = count + 1L
            val newSum = sumDb + sample.db.toDouble()
            val newMin = if (count == 0L) sample.db else min(snapshot.minDb, sample.db)
            val newMax = if (count == 0L) sample.db else max(snapshot.maxDb, sample.db)
            val cutoff = sample.timestampMs - RECENT_WINDOW_MS
            val newRecent = (snapshot.recent + sample).filter { it.timestampMs >= cutoff }
            return copy(
                snapshot = MeasurementSnapshot(
                    currentDb = sample.db,
                    minDb = newMin,
                    maxDb = newMax,
                    avgDb = (newSum / newCount).toFloat(),
                    durationMs = sample.timestampMs,
                    recent = newRecent,
                ),
                sumDb = newSum,
                count = newCount,
            )
        }

        companion object {
            val Empty = Accumulator(
                snapshot = MeasurementSnapshot.empty,
                sumDb = 0.0,
                count = 0L,
            )
        }
    }

    private companion object {
        const val RECENT_WINDOW_MS = 60_000L
    }
}
