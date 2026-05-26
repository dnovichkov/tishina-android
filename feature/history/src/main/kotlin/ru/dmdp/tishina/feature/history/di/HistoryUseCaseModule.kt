package ru.dmdp.tishina.feature.history.di

import android.content.Context
import androidx.core.content.FileProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.dmdp.tishina.core.data.export.MeasurementsExporterImpl
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.ExportHistoryUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import ru.dmdp.tishina.core.ui.snapshot.LineChartSnapshotter
import ru.dmdp.tishina.feature.history.HistoryViewModel
import ru.dmdp.tishina.feature.history.detail.share.ShareIntentBuilder
import javax.inject.Named
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
 *
 * Phase 6 Task 3 adds the FR-20 CSV-export dependencies:
 *  - [MeasurementsExporter] is bound to the `:core:data` implementation here rather than in
 *    `:core:data/DataModule` because the contract lives in `:core:domain` (the lower module
 *    only declares the interface; the actual binding has to know both sides).
 *  - [ExportHistoryUseCase] follows the same factory pattern as the other use-cases.
 *  - The `nowMillisProvider` clock is injected as a lambda via `@Named` so tests can inject
 *    a fixed instant. The production provider returns `System.currentTimeMillis()`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HistoryUseCaseModule {

    /**
     * Phase 6 Task 3 — FR-20 CSV exporter. Bound here rather than in `:core:data/DataModule`
     * so the `:core:domain` interface stays the only public type the use-case sees; the impl
     * remains an internal `:core:data` class.
     */
    @Binds
    @Singleton
    abstract fun bindMeasurementsExporter(impl: MeasurementsExporterImpl): MeasurementsExporter

    companion object {

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

        @Provides
        @Singleton
        fun provideExportHistoryUseCase(exporter: MeasurementsExporter): ExportHistoryUseCase =
            ExportHistoryUseCase(exporter)

        /**
         * Wall-clock as an injectable `() -> Long`. Disambiguated by [HistoryViewModel.NOW_MILLIS_PROVIDER]
         * — Hilt cannot pick between two `() -> Long` bindings by signature alone.
         */
        @Provides
        @Singleton
        @Named(HistoryViewModel.NOW_MILLIS_PROVIDER)
        fun provideNowMillisProvider(): () -> Long = { System.currentTimeMillis() }

        /**
         * Phase 6 Task 4 — share-Intent infrastructure for FR-10 P1. The snapshotter is a
         * plain class with no dependencies; provided here so the binding lives next to the
         * builder it pairs with rather than spreading the share wiring across modules.
         */
        @Provides
        @Singleton
        fun provideLineChartSnapshotter(): LineChartSnapshotter = LineChartSnapshotter()

        /**
         * Production wiring for [ShareIntentBuilder] — uses
         * `androidx.core.content.FileProvider.getUriForFile` with the
         * `${applicationId}.fileprovider` authority declared in `:app/AndroidManifest.xml`
         * and the `share/` cache subpath declared in
         * `:app/src/main/res/xml/file_provider_paths.xml`.
         */
        @Provides
        @Singleton
        fun provideShareIntentBuilder(@ApplicationContext context: Context): ShareIntentBuilder =
            ShareIntentBuilder(
                context = context,
                cacheSubdir = "share",
                fileToUri = { file ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                },
            )
    }
}
