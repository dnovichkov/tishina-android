package ru.dmdp.tishina.core.domain.model

/**
 * Single instantaneous dB reading produced by the audio engine.
 *
 * @property db sound pressure level expressed in dB (typically dB(A) with default config).
 * @property timestampMs offset in milliseconds since the start of the current measurement
 * session — NOT epoch time. This makes synthetic tests trivially reproducible.
 */
data class SoundSample(val db: Float, val timestampMs: Long)
