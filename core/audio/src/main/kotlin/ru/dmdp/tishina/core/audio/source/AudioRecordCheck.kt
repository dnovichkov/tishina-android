package ru.dmdp.tishina.core.audio.source

import android.content.Context
import android.media.AudioManager

/**
 * Static helper that asks the system whether `AudioSource.UNPROCESSED` capture
 * is honoured on this device. Reads `AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`
 * — devices opt in via this property (Android CDD § 7.8.2.2), and many shipped
 * phones report `null` or `"false"` meaning the request would silently downgrade
 * to MIC. We use the answer to skip an `AudioRecord` allocation in
 * [AudioRecordPcmSource.openSession] when UNPROCESSED is not available.
 *
 * The overload taking [AudioManager] exists so tests can pass a mock without
 * having to bring up a Robolectric `Context`.
 */
object AudioRecordCheck {

    /** Resolves [AudioManager] from [context] and delegates. Returns `false` if the service is missing. */
    fun isUnprocessedSupported(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return isUnprocessedSupported(audioManager)
    }

    /** Pure check against a supplied [AudioManager]; the unit-test entry point. */
    fun isUnprocessedSupported(audioManager: AudioManager): Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
}
