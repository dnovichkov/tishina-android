package ru.dmdp.tishina.core.audio.source

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fast JVM tests covering the source-selection, sample-rate-fallback, and lifecycle
 * logic of [AudioRecordPcmSource] using a fake `AudioRecordSessionFactory`. No
 * Robolectric needed — these tests pin the *policy* of the source. A small
 * companion test ([AudioRecordPcmSourceShadowTest]) verifies the policy hooks up
 * to real Android primitives.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AudioRecordPcmSourceTest {

    @Test
    fun `prefers UNPROCESSED when AudioManager reports support`() = runTest {
        val factory = FakeAudioRecordSessionFactory()
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(1).toList()

        assertEquals(MediaRecorder.AudioSource.UNPROCESSED, factory.createCalls.first().audioSource)
        assertEquals(1, factory.createCalls.size)
    }

    @Test
    fun `skips UNPROCESSED when AudioManager reports no support`() = runTest {
        val factory = FakeAudioRecordSessionFactory()
        val source = buildSource(unprocessedSupported = false, factory = factory)

        source.samples().take(1).toList()

        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, factory.createCalls.first().audioSource)
    }

    @Test
    fun `falls back to MIC when VOICE_RECOGNITION construction fails`() = runTest {
        val factory = FakeAudioRecordSessionFactory(
            createBehavior = { audioSource, _ ->
                when (audioSource) {
                    MediaRecorder.AudioSource.UNPROCESSED -> null // simulate IllegalArgumentException
                    MediaRecorder.AudioSource.VOICE_RECOGNITION -> null
                    MediaRecorder.AudioSource.MIC -> FakeAudioRecordSession()
                    else -> null
                }
            },
        )
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(1).toList()

        val sourcesTried = factory.createCalls.map { it.audioSource }
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            ),
            sourcesTried,
        )
    }

    @Test
    fun `falls back to 44_100 Hz when 48 kHz rejected`() = runTest {
        val factory = FakeAudioRecordSessionFactory(
            minBufferSizes = mapOf(
                48_000 to AudioRecord.ERROR_BAD_VALUE,
                44_100 to 4096,
            ),
        )
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(1).toList()

        assertEquals(44_100, source.sampleRateHz)
        assertEquals(44_100, factory.createCalls.first().sampleRate)
    }

    @Test
    fun `throws when no supported sample rate`() {
        val factory = FakeAudioRecordSessionFactory(
            minBufferSizes = mapOf(
                48_000 to AudioRecord.ERROR_BAD_VALUE,
                44_100 to AudioRecord.ERROR_BAD_VALUE,
            ),
        )
        val source = buildSource(
            unprocessedSupported = true,
            factory = factory,
            dispatcher = UnconfinedTestDispatcher(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            source.sampleRateHz
        }
        assertTrue(error.message!!.contains("No supported PCM sample rate"))
    }

    @Test
    fun `buffer size at least 100 ms of audio`() = runTest {
        val factory = FakeAudioRecordSessionFactory(
            minBufferSizes = mapOf(48_000 to 1024), // tiny min — must be overridden
        )
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(1).toList()

        val bufferBytes = factory.createCalls.first().bufferSizeBytes
        // 48 000 / 10 × 2 = 9600 bytes minimum for the 100 ms target.
        assertEquals(9_600, bufferBytes)
    }

    @Test
    fun `emits PCM chunks from the session`() = runTest {
        val payload = ShortArray(8) { (it * 100).toShort() }
        val session = FakeAudioRecordSession().apply {
            pendingReads.addLast(payload)
            pendingReads.addLast(payload)
        }
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> session })
        val source = buildSource(unprocessedSupported = true, factory = factory)

        val emitted = source.samples().take(2).toList()

        assertEquals(2, emitted.size)
        // copyOf trims to read size (8 shorts), regardless of chunk allocation size.
        assertEquals(8, emitted[0].size)
        assertEquals(0.toShort(), emitted[0][0])
        assertEquals(100.toShort(), emitted[0][1])
    }

    @Test
    fun `stops and releases session on flow completion`() = runTest {
        val session = FakeAudioRecordSession().apply {
            pendingReads.addLast(ShortArray(4))
        }
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> session })
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(1).toList()

        assertEquals(1, session.startedCount)
        assertEquals(1, session.stoppedCount)
        assertEquals(1, session.releasedCount)
    }

    @Test
    fun `stops and releases session on take operator early termination`() = runTest {
        // take(N) implicitly cancels the upstream once N items have been emitted — the same
        // CancellationException path that explicit user cancellation would trigger. Exercises
        // the try-finally lifecycle without needing an infinite read loop, which on
        // UnconfinedTestDispatcher would never yield back to the test scheduler.
        val session = FakeAudioRecordSession().apply {
            pendingReads.addLast(ShortArray(4))
            pendingReads.addLast(ShortArray(4))
            pendingReads.addLast(ShortArray(4))
        }
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> session })
        val source = buildSource(unprocessedSupported = true, factory = factory)

        source.samples().take(2).toList()

        assertEquals(1, session.startedCount)
        assertEquals(1, session.stoppedCount)
        assertEquals(1, session.releasedCount)
    }

    @Test
    fun `treats read==0 as transient and continues until data arrives`() = runTest {
        // 3 zero-returns (warm-up / route change), then a real chunk, then EOF.
        val session = FakeAudioRecordSession().apply {
            pendingZeroReads = 3
            pendingReads.addLast(ShortArray(4))
        }
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> session })
        // Use Dispatchers.Unconfined (not UnconfinedTestDispatcher) for the flowOn dispatcher.
        // The production code calls `yield()` on read==0, which dispatches on the flow's
        // dispatcher. A TestDispatcher there would create a TestCoroutineScheduler distinct
        // from runTest's scheduler and trigger "Detected use of different schedulers".
        val source = buildSource(
            unprocessedSupported = true,
            factory = factory,
            dispatcher = Dispatchers.Unconfined,
        )

        val emitted = source.samples().take(1).toList()

        assertEquals(1, emitted.size, "the positive read must still produce one emission")
        // 3 zero reads + 1 successful read; the source must not have broken out on the zeros.
        assertTrue(session.readCount >= 4, "expected at least 4 reads (3 zero + 1 data), got ${session.readCount}")
    }

    @Test
    fun `stops emission when read returns negative error`() = runTest {
        val session = FakeAudioRecordSession(defaultRead = AudioRecord.ERROR_DEAD_OBJECT).apply {
            pendingReads.addLast(ShortArray(4)) // first read succeeds
        }
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> session })
        val source = buildSource(unprocessedSupported = true, factory = factory)

        val emitted = source.samples().toList()

        assertEquals(1, emitted.size)
        assertEquals(1, session.releasedCount)
    }

    @Test
    fun `throws when every audio source rejected`() {
        val factory = FakeAudioRecordSessionFactory(createBehavior = { _, _ -> null })
        val source = buildSource(
            unprocessedSupported = true,
            factory = factory,
            dispatcher = UnconfinedTestDispatcher(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                source.samples().take(1).toList()
            }
        }
        assertTrue(error.message!!.contains("AudioRecord could not be acquired"))
    }

    @Test
    fun `isUnprocessedSupported delegates to AudioManager property`() = runTest {
        val supportedSource = buildSource(unprocessedSupported = true, factory = FakeAudioRecordSessionFactory())
        val unsupportedSource = buildSource(unprocessedSupported = false, factory = FakeAudioRecordSessionFactory())

        assertTrue(supportedSource.isUnprocessedSupported())
        assertEquals(false, unsupportedSource.isUnprocessedSupported())
    }

    @Test
    fun `falls through to VOICE_RECOGNITION when AudioManager service missing`() = runTest {
        val context = mockk<Context>()
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns null

        val factory = FakeAudioRecordSessionFactory()
        val source = AudioRecordPcmSource(context, factory, UnconfinedTestDispatcher())

        source.samples().take(1).toList()

        // No UNPROCESSED tried — AudioManager unavailable means we treat unprocessed as not supported.
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, factory.createCalls.first().audioSource)
    }

    @Test
    fun `companion object exposes AudioRecord ERROR_BAD_VALUE`() {
        assertEquals(AudioRecord.ERROR_BAD_VALUE, AudioRecordPcmSource.ERROR_BAD_VALUE)
    }

    @Test
    fun `companion source priority is UNPROCESSED then VOICE_RECOGNITION then MIC`() {
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            ),
            AudioRecordPcmSource.SOURCE_PRIORITY,
        )
    }

    @Test
    fun `companion sample-rate priority is 48k then 44_1k`() {
        assertEquals(listOf(48_000, 44_100), AudioRecordPcmSource.SUPPORTED_SAMPLE_RATES)
    }

    @Test
    fun `lazy sampleRate prefers 48 kHz when available`() = runTest {
        val factory = FakeAudioRecordSessionFactory()
        val source = buildSource(unprocessedSupported = true, factory = factory)

        assertEquals(48_000, source.sampleRateHz)
        // Second access must hit the lazy cache, not the factory again.
        val initialQueryCount = factory.createCalls.size
        assertNull(factory.createCalls.firstOrNull()) // no samples() yet → no createCalls
        assertEquals(initialQueryCount, factory.createCalls.size)
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun buildSource(
        unprocessedSupported: Boolean,
        factory: AudioRecordSessionFactory,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): AudioRecordPcmSource = AudioRecordPcmSource(
        context = contextWithUnprocessed(unprocessedSupported),
        sessionFactory = factory,
        ioDispatcher = dispatcher,
    )

    private fun contextWithUnprocessed(supported: Boolean): Context {
        val audioManager = mockk<AudioManager>()
        every {
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        } returns if (supported) "true" else "false"
        val context = mockk<Context>()
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        return context
    }
}
