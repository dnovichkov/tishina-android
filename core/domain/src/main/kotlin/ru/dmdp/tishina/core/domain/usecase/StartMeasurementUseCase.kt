package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot
import ru.dmdp.tishina.core.domain.model.SessionSeed
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
 *
 * The optional [SessionSeed] parameter lets the caller resume a session that
 * was previously paused: Pause cancels the upstream `samples()` collection,
 * Resume launches a fresh one — without a seed the new fold would start from
 * `Empty` and the first post-resume sample would overwrite the preserved
 * min / max / avg in the UI. The ViewModel hands the seed back via
 * [SessionSeed], including a `durationOffsetMs` so the clock keeps ticking.
 */
class StartMeasurementUseCase(private val audioRepository: AudioRepository) {

    operator fun invoke(
        config: MeasurementConfig,
        seed: SessionSeed = SessionSeed.empty,
    ): Flow<MeasurementSnapshot> =
        audioRepository.samples(config)
            .runningFold(Accumulator.fromSeed(seed)) { acc, sample -> acc.update(sample) }
            // runningFold emits the initial seed first; drop it so consumers only
            // see snapshots derived from real samples.
            .drop(1)
            .map { it.snapshot }

    /**
     * Internal fold state. Carries sum+count so we can derive the arithmetic
     * mean without storing the full session in memory.
     *
     * [durationOffsetMs] is added to each incoming `sample.timestampMs` so the
     * emitted `durationMs` is monotonic across Pause→Resume cycles (the
     * underlying `AudioRecord` restarts its own zero each time).
     */
    private data class Accumulator(val snapshot: MeasurementSnapshot, val sumDb: Double, val count: Long, val durationOffsetMs: Long) {
        fun update(sample: SoundSample): Accumulator {
            val newCount = count + 1L
            val newSum = sumDb + sample.db.toDouble()
            val newMin = min(snapshot.minDb, sample.db)
            val newMax = max(snapshot.maxDb, sample.db)
            val effectiveTimestamp = sample.timestampMs + durationOffsetMs
            val cutoff = effectiveTimestamp - RECENT_WINDOW_MS
            // Shift the new sample's timestamp into the session-wide clock so the seeded `recent`
            // entries stay alignment-comparable. Pre-seed recent timestamps are already absolute.
            val shiftedSample = if (durationOffsetMs == 0L) sample else sample.copy(timestampMs = effectiveTimestamp)
            val newRecent = (snapshot.recent + shiftedSample).filter { it.timestampMs >= cutoff }
            return copy(
                snapshot = MeasurementSnapshot(
                    currentDb = sample.db,
                    minDb = newMin,
                    maxDb = newMax,
                    avgDb = (newSum / newCount).toFloat(),
                    durationMs = effectiveTimestamp,
                    recent = newRecent,
                ),
                sumDb = newSum,
                count = newCount,
            )
        }

        companion object {
            fun fromSeed(seed: SessionSeed): Accumulator = Accumulator(
                snapshot = MeasurementSnapshot(
                    currentDb = 0f,
                    minDb = seed.minDb,
                    maxDb = seed.maxDb,
                    avgDb = if (seed.count > 0L) (seed.sumDb / seed.count).toFloat() else 0f,
                    durationMs = seed.durationOffsetMs,
                    recent = seed.recent,
                ),
                sumDb = seed.sumDb,
                count = seed.count,
                durationOffsetMs = seed.durationOffsetMs,
            )
        }
    }

    private companion object {
        const val RECENT_WINDOW_MS = 60_000L
    }
}
