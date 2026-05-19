package ru.dmdp.tishina.core.audio

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.audio.dsp.AudioProcessorFactory
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.testing.fakes.FakePcmAudioSource

/**
 * Verifies the cold-flow contract of [AudioRepositoryImpl]: cancelling the consumer
 * propagates through to [FakePcmAudioSource] so the underlying recorder is released
 * (NFR-5).
 *
 * Kept separate from [AudioRepositoryImplTest] to keep the integration suite
 * lighter — these tests only need a handful of emissions and no DSP assertions.
 */
class AudioRepositoryImplCancellationTest {

    @Test
    fun `take(1) cancels upstream source after one emission`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true))

        source.emitTone(1_000f, 90f, 200)
        repo.samples(MeasurementConfig()).take(1).toList()

        assertEquals(1, source.cancelCount, "expected upstream source to be cancelled exactly once")
    }

    @Test
    fun `take(3) cancels upstream source after three emissions`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true))

        repeat(3) { source.emitTone(1_000f, 90f, 100) }
        val out = repo.samples(MeasurementConfig()).take(3).toList()

        assertEquals(3, out.size)
        assertEquals(1, source.cancelCount)
    }
}

private fun microphoneContext(present: Boolean): Context {
    val packageManager = mockk<PackageManager>()
    every { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns present
    val context = mockk<Context>()
    every { context.packageManager } returns packageManager
    return context
}
