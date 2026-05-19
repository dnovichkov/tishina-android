package ru.dmdp.tishina.core.audio.source

import android.content.Context
import android.media.AudioManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pinpoint tests for [AudioRecordCheck]. The fact-check fully fits inside two
 * cases: AudioManager.getProperty returns `"true"` ⇔ supported. Everything else
 * (null, `"false"`, missing AudioManager, missing AudioManager service) must
 * report unsupported, which is the conservative answer for source-selection.
 */
class AudioRecordCheckTest {

    @ParameterizedTest(name = "AudioManager.getProperty == \"{0}\" ⇒ supported={1}")
    @CsvSource(
        "true, true",
        "false, false",
        "TRUE, false", // case-sensitive — Android spec value is literally "true"
        "null, false", // sentinel: see helper
        "'', false",
    )
    fun `result follows AudioManager property`(propertyValue: String, expected: Boolean) {
        val actualValue = propertyValue.takeUnless { it == "null" }
        val audioManager = mockk<AudioManager>()
        every {
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        } returns actualValue

        assertEquals(expected, AudioRecordCheck.isUnprocessedSupported(audioManager))
    }

    @Test
    fun `context overload reads via AudioManager`() {
        val audioManager = mockk<AudioManager>()
        every {
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        } returns "true"
        val context = mockk<Context>()
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager

        assertTrue(AudioRecordCheck.isUnprocessedSupported(context))
    }

    @Test
    fun `context overload returns false when AudioManager service missing`() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns null

        assertFalse(AudioRecordCheck.isUnprocessedSupported(context))
    }
}
