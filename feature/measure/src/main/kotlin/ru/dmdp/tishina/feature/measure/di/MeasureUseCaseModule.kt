package ru.dmdp.tishina.feature.measure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.dmdp.tishina.core.domain.repository.AudioRepository
import ru.dmdp.tishina.core.domain.usecase.ResetMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.StartMeasurementUseCase
import javax.inject.Singleton

/**
 * Bridges the pure-Kotlin use-cases from `:core:domain` into the Hilt graph.
 *
 * `:core:domain` deliberately has no `javax.inject` dependency — it stays a
 * plain JVM module so it can be reused outside Android (CLI tooling, future
 * platforms). Wiring lives here at the feature edge instead.
 *
 * `SingletonComponent` scope mirrors [AudioRepository]'s scope so the
 * use-cases share its lifetime. Each consumer still gets a fresh
 * `Flow<MeasurementSnapshot>` per invocation — the use-case object is
 * stateless beyond the injected repository.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MeasureUseCaseModule {

    @Provides
    @Singleton
    fun provideStartMeasurementUseCase(audioRepository: AudioRepository): StartMeasurementUseCase =
        StartMeasurementUseCase(audioRepository)

    @Provides
    @Singleton
    fun provideResetMeasurementUseCase(): ResetMeasurementUseCase = ResetMeasurementUseCase()
}
