package ru.dmdp.tishina.core.data.di

import javax.inject.Qualifier

/**
 * Marks the IO-bound [kotlinx.coroutines.CoroutineDispatcher] on which the data layer runs
 * Room I/O.
 *
 * Mirrors the qualifier defined in `:core:audio` instead of depending on that module: the
 * data layer has no other reason to pull in `:core:audio`, so duplicating a single
 * annotation keeps the module graph slimmer. Both qualifiers ultimately bind the same
 * `Dispatchers.IO` instance in production but use distinct Hilt keys, which is precisely
 * what `@Qualifier` is for.
 *
 * Tests pass a `TestDispatcher` (typically `UnconfinedTestDispatcher`) directly via the
 * constructor, so the qualifier never matters in unit tests.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
