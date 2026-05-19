package ru.dmdp.tishina.core.testing.fakes

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.repository.AudioRepository

/**
 * In-memory [AudioRepository] for unit-testing use-cases and ViewModels without
 * spinning up `AudioRecord` or an emulator.
 *
 * Usage:
 * ```
 * val repo = FakeAudioRepository()
 * repo.emit(SoundSample(db = 60f, timestampMs = 0L))
 * ```
 *
 * The flow returned by [samples] is hot-ish: it's backed by a
 * [MutableSharedFlow] with a small replay buffer so emits made before the
 * collector subscribes are still observable. This makes assertions deterministic
 * under `runTest` without forcing every test to interleave `launch { collect }`
 * with `emit` calls.
 *
 * [cancelCount] lets tests verify that the cold flow surface is honoured —
 * specifically that cancelling collection stops capture (NFR-5).
 */
class FakeAudioRepository : AudioRepository {

    private val flow = MutableSharedFlow<SoundSample>(
        replay = REPLAY_BUFFER,
        extraBufferCapacity = EXTRA_BUFFER,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /** Latest [MeasurementConfig] handed to [samples]. Useful for asserting wiring. */
    @Volatile
    var lastConfig: MeasurementConfig? = null
        private set

    /** Number of times a collector cancelled / completed the [samples] flow. */
    @Volatile
    var cancelCount: Int = 0
        private set

    private var available: Boolean = true

    override fun samples(config: MeasurementConfig): Flow<SoundSample> =
        flow.asSharedFlow()
            .onStart { lastConfig = config }
            .onCompletion { cancelCount += 1 }

    override suspend fun isAvailable(): Boolean = available

    /** Push a [SoundSample] to subscribed collectors (or buffer it for later). */
    suspend fun emit(sample: SoundSample) {
        flow.emit(sample)
    }

    /** Convenience: emit several samples sequentially. */
    suspend fun emitAll(samples: Iterable<SoundSample>) {
        samples.forEach { flow.emit(it) }
    }

    /** Toggle the value returned by [isAvailable]. */
    fun setAvailable(value: Boolean) {
        available = value
    }

    private companion object {
        const val REPLAY_BUFFER = 64
        const val EXTRA_BUFFER = 64
    }
}
