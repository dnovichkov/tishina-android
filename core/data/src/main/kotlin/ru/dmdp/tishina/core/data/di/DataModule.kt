package ru.dmdp.tishina.core.data.di

import android.content.Context
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
import ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository
import javax.inject.Singleton

/**
 * Hilt graph for the `:core:data` module.
 *
 * **Single-database singleton:** [TishinaDatabase] is `@Singleton` because Room's
 * `InvalidationTracker` and write queue rely on a single process-wide instance — two
 * databases pointed at the same file would corrupt each other.
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

    companion object {

        @Provides
        @Singleton
        fun provideTishinaDatabase(@ApplicationContext context: Context): TishinaDatabase =
            Room.databaseBuilder(context, TishinaDatabase::class.java, TishinaDatabase.DATABASE_NAME)
                .addCallback(TishinaDatabase.LENGTH_GUARD_CALLBACK)
                .build()

        @Provides
        fun provideMeasurementDao(database: TishinaDatabase): MeasurementDao =
            database.measurementDao()

        // Same rationale as :core:audio's IoDispatcher provider — the @Provides binding is
        // the canonical seam where Dispatchers.IO becomes injectable, so detekt's
        // InjectDispatcher rule is silenced here rather than at each consumer.
        @Suppress("InjectDispatcher")
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
