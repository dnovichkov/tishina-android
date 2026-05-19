package ru.dmdp.tishina.core.audio.source

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import javax.inject.Inject

/**
 * Production [AudioRecordSessionFactory] that talks to real `AudioRecord`. Always
 * captures 16-bit mono PCM; the caller chooses the AudioSource and sample rate.
 *
 * The constructor `AudioRecord(int, int, int, int, int)` is deprecated in API 34+
 * in favour of `AudioRecord.Builder`, but the legacy constructor remains the only
 * way to specify legacy `AudioSource.UNPROCESSED` semantics on older devices and
 * is still the supported path; we suppress the deprecation warning intentionally.
 */
internal class AndroidAudioRecordSessionFactory @Inject constructor() : AudioRecordSessionFactory {

    override fun getMinBufferSize(sampleRate: Int): Int =
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

    // IllegalArgumentException ⇒ the (source, rate, buffer) triple is not supported on this device;
    // IllegalStateException ⇒ AudioRecord internals refused construction (rare, mid-init race).
    // In both cases we *intentionally* swallow because the surrounding fallback chain in
    // AudioRecordPcmSource is the failure-handling mechanism — returning null is how we signal
    // "try the next AudioSource". Logging would just spam at production startup.
    @SuppressLint("MissingPermission") // RECORD_AUDIO is gated by the caller before collecting samples.
    @Suppress("DEPRECATION", "SwallowedException")
    override fun create(audioSource: Int, sampleRate: Int, bufferSizeBytes: Int): AudioRecordSession? = try {
        val record = AudioRecord(
            audioSource,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            null
        } else {
            AndroidAudioRecordSession(record)
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IllegalStateException) {
        null
    }
}

private class AndroidAudioRecordSession(private val record: AudioRecord) : AudioRecordSession {

    override val state: Int get() = record.state

    override fun startRecording() {
        record.startRecording()
    }

    override fun read(buffer: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int =
        record.read(buffer, offsetInShorts, sizeInShorts)

    override fun stop() {
        // AudioRecord.stop() throws IllegalStateException if called in an unexpected state
        // (e.g. constructor failed midway). The factory already filters those, but we still
        // wrap defensively because Flow cancellation can race with start().
        runCatching { record.stop() }
    }

    override fun release() {
        record.release()
    }
}
