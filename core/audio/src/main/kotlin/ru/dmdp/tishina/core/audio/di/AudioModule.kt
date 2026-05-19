package ru.dmdp.tishina.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.dmdp.tishina.core.audio.dsp.AudioProcessorFactory
import ru.dmdp.tishina.core.audio.source.AndroidAudioRecordSessionFactory
import ru.dmdp.tishina.core.audio.source.AudioRecordPcmSource
import ru.dmdp.tishina.core.audio.source.AudioRecordSessionFactory
import ru.dmdp.tishina.core.audio.source.PcmAudioSource
import javax.inject.Singleton

/**
 * Hilt graph for the `:core:audio` module.
 *
 * Bindings live in `SingletonComponent` because the microphone is a process-wide singleton
 * resource — multiple `AudioRecord` consumers would just block each other on hardware. The
 * abstract bindings rely on the concrete classes' `@Inject` constructors; [AudioProcessorFactory]
 * needs an explicit `@Provides` because it intentionally exposes a no-arg constructor (it's a
 * lightweight builder, easier to instantiate from non-Hilt contexts than to add `@Inject`).
 *
 * Modelled as a Kotlin `interface` rather than an `abstract class` because every binding is
 * abstract — detekt's `UnnecessaryAbstractClass` rule prefers interfaces in that case. Hilt
 * supports interface modules transparently, with `@Provides` methods placed in the companion
 * object.
 *
 * [ru.dmdp.tishina.core.domain.repository.AudioRepository] is **not** yet wired here — its
 * implementation lands in Task 8; the `@Binds` for `AudioRepositoryImpl` will be added then.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface AudioModule {

    @Binds
    @Singleton
    fun bindPcmAudioSource(impl: AudioRecordPcmSource): PcmAudioSource

    @Binds
    @Singleton
    fun bindAudioRecordSessionFactory(
        impl: AndroidAudioRecordSessionFactory,
    ): AudioRecordSessionFactory

    companion object {

        @Provides
        @Singleton
        fun provideAudioProcessorFactory(): AudioProcessorFactory = AudioProcessorFactory()

        // The @Provides method is itself the canonical seam where Dispatchers.IO becomes
        // injectable; detekt's InjectDispatcher rule cannot see that the @IoDispatcher qualifier
        // mediates this, so we suppress the warning at the binding site rather than smearing it
        // across every call site that consumes the dispatcher.
        @Suppress("InjectDispatcher")
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
