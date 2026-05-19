package ru.dmdp.tishina.core.audio.source

/**
 * Minimal wrapper around `android.media.AudioRecord` exposing only the surface
 * area used by [AudioRecordPcmSource]. Existing solely to keep the production
 * source unit-testable without Robolectric — the JVM tests substitute a
 * `FakeAudioRecordSession` for these methods.
 *
 * Each method maps 1:1 to its `AudioRecord` counterpart; see Android docs for
 * thread-safety and lifecycle constraints (no `read()` after `stop()`, no
 * operations after `release()`, etc.).
 */
internal interface AudioRecordSession {

    /** Either `AudioRecord.STATE_INITIALIZED` or `STATE_UNINITIALIZED`. */
    val state: Int

    fun startRecording()

    fun read(buffer: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int

    fun stop()

    fun release()
}

/**
 * Builds [AudioRecordSession]s and exposes `getMinBufferSize` so the source can
 * probe support for a sample rate before committing to it. Separated from the
 * source so tests can supply scripted failures (constructor `IllegalArgumentException`,
 * `getMinBufferSize == ERROR_BAD_VALUE`) without touching real Android APIs.
 */
internal interface AudioRecordSessionFactory {

    /**
     * Returns the minimum buffer size in bytes that `AudioRecord` requires for the
     * given sample rate (16-bit mono), or `AudioRecord.ERROR_BAD_VALUE` /
     * `AudioRecord.ERROR` when the device cannot honour the request.
     */
    fun getMinBufferSize(sampleRate: Int): Int

    /**
     * Attempts to allocate an `AudioRecord` with the supplied parameters. Returns
     * `null` if the constructor throws `IllegalArgumentException` or the resulting
     * recorder is in `STATE_UNINITIALIZED` (in the latter case the partial recorder
     * is released by the factory before returning).
     */
    fun create(audioSource: Int, sampleRate: Int, bufferSizeBytes: Int): AudioRecordSession?
}
