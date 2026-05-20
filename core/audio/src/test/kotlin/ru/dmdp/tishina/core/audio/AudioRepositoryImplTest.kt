package ru.dmdp.tishina.core.audio

import android.content.Context
import android.content.pm.PackageManager
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.audio.dsp.AudioProcessorFactory
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.testing.fakes.FakePcmAudioSource
import kotlin.math.abs

/**
 * Integration tests for [AudioRepositoryImpl]. Drives the full DSP pipeline from a fake PCM source
 * to dB readings without touching `AudioRecord` or Robolectric. Asserts on output dB values
 * against IEC 61672-1 A-weighting reference points to verify wiring is correct end-to-end.
 *
 * Tolerances are intentionally generous (±2 dB) because:
 * - Time-weighted RMS needs a few hundred ms to converge on a steady-state value.
 * - The chunk arrives as a single buffer, so the IIR filter only sees that finite slice; results
 *   are within tolerance after one chunk only when the chunk is long enough (≥ 500 ms typical).
 */
class AudioRepositoryImplTest {

    @Test
    fun `1 kHz at 90 dB with A-weighting emits ~90 dB`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        repo.samples(MeasurementConfig(FrequencyWeighting.A, TimeWeighting.FAST)).test {
            source.emitTone(frequencyHz = 1_000f, amplitudeDb = 90f, durationMs = 800)
            val sample = awaitItem()
            assertWithin(90f, sample.db, tolerance = 2f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `1 kHz at 90 dB with Z-weighting also emits ~90 dB`() = runTest {
        // 1 kHz is the A-weighting reference, so A and Z must agree there.
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        repo.samples(MeasurementConfig(FrequencyWeighting.Z, TimeWeighting.FAST)).test {
            source.emitTone(frequencyHz = 1_000f, amplitudeDb = 90f, durationMs = 800)
            val sample = awaitItem()
            assertWithin(90f, sample.db, tolerance = 2f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `100 Hz A-weighting is ~19 dB below Z-weighting`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        val aDb = repo.samples(MeasurementConfig(FrequencyWeighting.A, TimeWeighting.FAST))
            .also { source.emitTone(100f, 90f, 800) }
            .firstAfter(source, frequencyHz = 100f, amplitudeDb = 90f, durationMs = 800)
        val zDb = repo.samples(MeasurementConfig(FrequencyWeighting.Z, TimeWeighting.FAST))
            .firstAfter(source, frequencyHz = 100f, amplitudeDb = 90f, durationMs = 800)

        // A-weighting at 100 Hz: −19.1 dB per IEC 61672-1. Z is flat.
        val delta = zDb - aDb
        assertWithin(19.1f, delta, tolerance = 2f)
    }

    @Test
    fun `calibration offset shifts the dB value`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        repo.samples(MeasurementConfig(FrequencyWeighting.A, TimeWeighting.FAST, calibrationOffsetDb = 5f)).test {
            source.emitTone(frequencyHz = 1_000f, amplitudeDb = 90f, durationMs = 800)
            val sample = awaitItem()
            // 1 kHz @ 90 dB + 5 dB calibration = ~95 dB.
            assertWithin(95f, sample.db, tolerance = 2f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timestamp is monotonic non-decreasing`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        repo.samples(MeasurementConfig()).test {
            source.emitTone(1_000f, 90f, 500)
            source.emitTone(1_000f, 90f, 500)
            source.emitTone(1_000f, 90f, 500)
            val s1 = awaitItem()
            val s2 = awaitItem()
            val s3 = awaitItem()
            assertTrue(s1.timestampMs <= s2.timestampMs, "expected ${s1.timestampMs} <= ${s2.timestampMs}")
            assertTrue(s2.timestampMs <= s3.timestampMs, "expected ${s2.timestampMs} <= ${s3.timestampMs}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first sample timestamp starts at zero`() = runTest {
        val source = FakePcmAudioSource()
        val repo = AudioRepositoryImpl(source, AudioProcessorFactory(), microphoneContext(present = true), Dispatchers.Unconfined)

        repo.samples(MeasurementConfig()).test {
            source.emitTone(1_000f, 90f, 200)
            val sample = awaitItem()
            assertEquals(0L, sample.timestampMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isAvailable returns true when device has microphone feature`() = runTest {
        val repo = AudioRepositoryImpl(
            FakePcmAudioSource(),
            AudioProcessorFactory(),
            microphoneContext(present = true),
            Dispatchers.Unconfined,
        )
        assertTrue(repo.isAvailable())
    }

    @Test
    fun `isAvailable returns false when device lacks microphone feature`() = runTest {
        val repo = AudioRepositoryImpl(
            FakePcmAudioSource(),
            AudioProcessorFactory(),
            microphoneContext(present = false),
            Dispatchers.Unconfined,
        )
        assertFalse(repo.isAvailable())
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun microphoneContext(present: Boolean): Context {
        val packageManager = mockk<PackageManager>()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE) } returns present
        val context = mockk<Context>()
        every { context.packageManager } returns packageManager
        return context
    }

    private suspend fun kotlinx.coroutines.flow.Flow<ru.dmdp.tishina.core.domain.model.SoundSample>.firstAfter(
        source: FakePcmAudioSource,
        frequencyHz: Float,
        amplitudeDb: Float,
        durationMs: Int,
    ): Float {
        source.emitTone(frequencyHz, amplitudeDb, durationMs)
        return take(1).toList().first().db
    }

    private fun assertWithin(expected: Float, actual: Float, tolerance: Float) {
        val delta = abs(expected - actual)
        assertTrue(
            delta <= tolerance,
            "expected $expected ± $tolerance dB, got $actual (delta=$delta)",
        )
    }
}
