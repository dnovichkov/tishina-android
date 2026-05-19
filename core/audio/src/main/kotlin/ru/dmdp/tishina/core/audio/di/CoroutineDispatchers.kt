package ru.dmdp.tishina.core.audio.di

import javax.inject.Qualifier

/**
 * Marks the IO-bound [kotlinx.coroutines.CoroutineDispatcher] used by the audio engine.
 *
 * Exists so [ru.dmdp.tishina.core.audio.source.AudioRecordPcmSource] can be constructed in tests
 * with a `TestDispatcher` instead of the real `Dispatchers.IO`, which keeps `runTest` virtual-time
 * fully in control and prevents `read()` calls from racing the test runner.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
