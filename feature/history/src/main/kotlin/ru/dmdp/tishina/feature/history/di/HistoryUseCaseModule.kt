package ru.dmdp.tishina.feature.history.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import javax.inject.Singleton

/**
 * Bridges pure-Kotlin use-cases from `:core:domain` into the Hilt graph for `:feature:history`.
 *
 * `:core:domain` deliberately has no `javax.inject` dependency — it stays a plain JVM module
 * so it can be reused outside Android. Wiring lives here at the feature edge.
 *
 * `SingletonComponent` scope matches [MeasurementRepository]'s scope (provided by
 * `:core:data/DataModule`), so the use-cases share its lifetime. Hilt's `@Binds` checker
 * forbids declaring `provideSaveMeasurementUseCase` here in addition to MeasureUseCaseModule —
 * that one lives in `:feature:measure` and remains shared via the SingletonComponent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object HistoryUseCaseModule {

    @Provides
    @Singleton
    fun provideGetMeasurementsUseCase(repository: MeasurementRepository): GetMeasurementsUseCase =
        GetMeasurementsUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteMeasurementUseCase(repository: MeasurementRepository): DeleteMeasurementUseCase =
        DeleteMeasurementUseCase(repository)

    @Provides
    @Singleton
    fun provideDeleteMeasurementsUseCase(repository: MeasurementRepository): DeleteMeasurementsUseCase =
        DeleteMeasurementsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetMeasurementByIdUseCase(repository: MeasurementRepository): GetMeasurementByIdUseCase =
        GetMeasurementByIdUseCase(repository)

    @Provides
    @Singleton
    fun provideUpdateMeasurementNoteUseCase(repository: MeasurementRepository): UpdateMeasurementNoteUseCase =
        UpdateMeasurementNoteUseCase(repository)
}
