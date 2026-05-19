package ru.dmdp.tishina.core.audio.source

import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric smoke test wiring [AndroidAudioRecordSessionFactory] against the real
 * `AudioRecord` API. This proves the production factory talks to Android primitives
 * correctly; the *policy* layer (source/sample-rate fallback, lifecycle) is covered
 * by the fast JVM tests in [AudioRecordPcmSourceTest].
 *
 * Robolectric pins to API 33 via `src/test/resources/robolectric.properties`. On that
 * level `AudioRecord.getMinBufferSize(48 000, MONO, 16-bit)` returns a real positive
 * value, and the `AudioRecord` constructor succeeds without throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AudioRecordPcmSourceShadowTest {

    @Test
    fun `factory reports positive min buffer size for 48 kHz on Robolectric`() {
        val factory = AndroidAudioRecordSessionFactory()
        val size = factory.getMinBufferSize(48_000)
        assertTrue("Expected positive min buffer for 48 kHz, got $size", size > 0)
    }

    @Test
    fun `factory creates AudioRecord session for MIC source`() {
        val factory = AndroidAudioRecordSessionFactory()
        val minSize = factory.getMinBufferSize(48_000)
        val session = factory.create(MediaRecorder.AudioSource.MIC, 48_000, minSize * 2)

        assertNotNull("Robolectric should allocate AudioRecord successfully", session)
        assertEquals(AudioRecord.STATE_INITIALIZED, session!!.state)

        // Lifecycle hygiene: the session must survive stop() + release() without throwing.
        session.stop()
        session.release()
    }

    @Test
    fun `source picks a valid sample rate when constructed against real Android APIs`() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
        val source = AudioRecordPcmSource(
            context = context,
            sessionFactory = AndroidAudioRecordSessionFactory(),
            ioDispatcher = Dispatchers.Unconfined,
        )

        // Robolectric supports either 48 kHz or 44.1 kHz; both are acceptable.
        val rate = source.sampleRateHz
        assertTrue(
            "sampleRateHz must be 48 000 or 44 100, got $rate",
            rate == 48_000 || rate == 44_100,
        )
    }
}
