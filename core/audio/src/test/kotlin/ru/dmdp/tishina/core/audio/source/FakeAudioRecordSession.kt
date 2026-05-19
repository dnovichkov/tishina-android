package ru.dmdp.tishina.core.audio.source

import android.media.AudioRecord

/**
 * In-memory [AudioRecordSession] for fast JVM tests. Records lifecycle events
 * (`startRecording / stop / release / read`) so tests can assert on them without
 * resorting to Robolectric.
 *
 * Use [pendingReads] to script what `read()` returns: each invocation consumes
 * one buffer from the head of the queue. When the queue is exhausted, `read()`
 * returns [defaultRead] (default `STOP_READING = -1`) so cooperative cancellation
 * in [AudioRecordPcmSource.samples] terminates the flow naturally.
 */
internal class FakeAudioRecordSession(initialState: Int = AudioRecord.STATE_INITIALIZED, private val defaultRead: Int = STOP_READING) :
    AudioRecordSession {

    override val state: Int = initialState

    var startedCount: Int = 0
        private set
    var stoppedCount: Int = 0
        private set
    var releasedCount: Int = 0
        private set
    var readCount: Int = 0
        private set

    val pendingReads: ArrayDeque<ShortArray> = ArrayDeque()

    /**
     * If non-zero, the next N reads return 0 (legal transient AudioRecord state, e.g.
     * warm-up after [startRecording] or a hardware route change). Decrements per read.
     * Use this to verify the consumer cooperates with cancellation while data is unavailable.
     */
    var pendingZeroReads: Int = 0

    override fun startRecording() {
        startedCount++
    }

    override fun read(buffer: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        readCount++
        if (pendingZeroReads > 0) {
            pendingZeroReads--
            return 0
        }
        val next = pendingReads.removeFirstOrNull() ?: return defaultRead
        val n = minOf(next.size, sizeInShorts)
        System.arraycopy(next, 0, buffer, offsetInShorts, n)
        return n
    }

    override fun stop() {
        stoppedCount++
    }

    override fun release() {
        releasedCount++
    }

    companion object {
        /** Mirrors AudioRecord.ERROR — signals "stop reading" to [AudioRecordPcmSource]. */
        const val STOP_READING: Int = -1
    }
}

/**
 * Scriptable [AudioRecordSessionFactory] for unit tests. Configure either
 * [minBufferSizes] (per sample rate) or [createBehavior] (per AudioSource) to
 * exercise fallback logic without an Android runtime.
 */
internal class FakeAudioRecordSessionFactory(
    private val minBufferSizes: Map<Int, Int> = DEFAULT_BUFFER_SIZES,
    private val createBehavior: (audioSource: Int, sampleRate: Int) -> FakeAudioRecordSession? =
        { _, _ -> FakeAudioRecordSession() },
) : AudioRecordSessionFactory {

    val createCalls: MutableList<CreateCall> = mutableListOf()
    val createdSessions: MutableList<FakeAudioRecordSession> = mutableListOf()

    data class CreateCall(val audioSource: Int, val sampleRate: Int, val bufferSizeBytes: Int)

    override fun getMinBufferSize(sampleRate: Int): Int =
        minBufferSizes[sampleRate] ?: AudioRecord.ERROR_BAD_VALUE

    override fun create(audioSource: Int, sampleRate: Int, bufferSizeBytes: Int): AudioRecordSession? {
        createCalls.add(CreateCall(audioSource, sampleRate, bufferSizeBytes))
        val session = createBehavior(audioSource, sampleRate) ?: return null
        createdSessions.add(session)
        return session
    }

    companion object {
        /** Reasonable defaults — both 48 kHz and 44.1 kHz are supported with 4096-byte min buffers. */
        val DEFAULT_BUFFER_SIZES: Map<Int, Int> = mapOf(48_000 to 4096, 44_100 to 4096)
    }
}
