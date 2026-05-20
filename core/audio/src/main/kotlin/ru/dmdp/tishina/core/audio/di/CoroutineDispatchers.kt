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

/**
 * Marks the CPU-bound [kotlinx.coroutines.CoroutineDispatcher] used to run the DSP pipeline (DC
 * block + A/Z weighting biquad cascade + per-sample TimeWeightedRms + SPL).
 *
 * Production binding is `Dispatchers.Default`. Without an explicit `.flowOn(defaultDispatcher)` on
 * [ru.dmdp.tishina.core.audio.AudioRepositoryImpl.samples], the outer `flow { source.samples()
 * .collect { processor.process(...) } }` runs `processor.process` in the consumer's context — and
 * the consumer is `viewModelScope` (Main.immediate). Pushing the per-sample inner loop onto Main
 * is the difference between meeting NFR-2 (≤ 5% CPU, 10 Hz UI refresh) on low-end devices and
 * jank during measurement.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
