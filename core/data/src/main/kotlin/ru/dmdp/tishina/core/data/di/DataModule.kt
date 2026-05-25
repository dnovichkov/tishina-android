package ru.dmdp.tishina.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.dmdp.tishina.core.data.db.TishinaDatabase
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.licenses.OssLicensesProviderImpl
import ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl
import ru.dmdp.tishina.core.data.settings.SettingsRepositoryImpl
import ru.dmdp.tishina.core.data.version.AppVersionProviderImpl
import ru.dmdp.tishina.core.domain.repository.AppVersionProvider
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository
import ru.dmdp.tishina.core.domain.repository.OssLicensesProvider
import ru.dmdp.tishina.core.domain.repository.SettingsRepository
import javax.inject.Singleton

/**
 * Hilt graph for the `:core:data` module.
 *
 * **Single-database singleton:** [TishinaDatabase] is `@Singleton` because Room's
 * `InvalidationTracker` and write queue rely on a single process-wide instance — two
 * databases pointed at the same file would corrupt each other.
 *
 * **Single Preferences DataStore singleton:** the same rule applies to
 * [DataStore]<[Preferences]> — DataStore enforces "one instance per file" at runtime
 * (it throws `IllegalStateException` if a second [PreferenceDataStoreFactory.create]
 * targets the same path while the first is still open). Phase 4's
 * [SettingsRepositoryImpl] consumes this binding to persist calibration, theme,
 * dynamic colors and locale into `tishina_settings.preferences_pb` under app-private
 * storage.
 *
 * **No `.allowMainThreadQueries()` and no `.fallbackToDestructiveMigration()` in
 * production:** queries are dispatched onto [Dispatchers.IO] via the repository, and we
 * want Phase 4 migrations to be loud failures rather than silent data loss.
 *
 * **CHECK trigger callback:** [TishinaDatabase.LENGTH_GUARD_CALLBACK] installs the length
 * triggers for `title` / `note` here too — Room only fires `onCreate` once per database
 * file, so without this binding production installs would never get the guard.
 *
 * **Interface module shape:** every binding is abstract or annotation-driven; detekt's
 * `UnnecessaryAbstractClass` prefers an interface in that case (same pattern as
 * `:core:audio` `AudioModule`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

    @Binds
    @Singleton
    fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository

    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    // Phase 5 — FR-21 AboutScreen reads the live VERSION_NAME/CODE off `PackageManager`
    // (not `BuildConfig` directly) so the value matches what Play Store / RuStore show.
    @Binds
    @Singleton
    fun bindAppVersionProvider(impl: AppVersionProviderImpl): AppVersionProvider

    // Phase 5 — FR-21 OSS-license inventory bundled as `assets/oss_licenses.json` in
    // the `:app` module. Curated manually to avoid Google's oss-licenses-plugin which
    // would drag in Play Services (conflicts with NFR-10 "no third-party analytics").
    @Binds
    @Singleton
    fun bindOssLicensesProvider(impl: OssLicensesProviderImpl): OssLicensesProvider

    companion object {

        private const val SETTINGS_DATASTORE_NAME = "tishina_settings"

        @Provides
        @Singleton
        fun provideTishinaDatabase(@ApplicationContext context: Context): TishinaDatabase =
            Room.databaseBuilder(context, TishinaDatabase::class.java, TishinaDatabase.DATABASE_NAME)
                .addCallback(TishinaDatabase.LENGTH_GUARD_CALLBACK)
                .build()

        @Provides
        fun provideMeasurementDao(database: TishinaDatabase): MeasurementDao =
            database.measurementDao()

        /**
         * Settings DataStore — Preferences flavor, one file `tishina_settings.preferences_pb`
         * inside `context.filesDir/datastore/`. The [ReplaceFileCorruptionHandler] aligns with
         * NFR-7 (crash-free ≥ 99.5%): if the on-disk protobuf is truncated by a power-loss
         * write, DataStore swaps in [emptyPreferences] instead of throwing, and the user
         * silently falls back to defaults — far better UX than a launch loop.
         */
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) },
        )

        // Same rationale as :core:audio's IoDispatcher provider — the @Provides binding is
        // the canonical seam where Dispatchers.IO becomes injectable, so detekt's
        // InjectDispatcher rule is silenced here rather than at each consumer.
        @Suppress("InjectDispatcher")
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
