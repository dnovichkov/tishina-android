package ru.dmdp.tishina.core.data.repository

import android.os.Build
import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.data.db.TishinaDatabase
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * End-to-end contract for [MeasurementRepositoryImpl.deleteAll] — the bulk-delete entry
 * point used by `:feature:history` for FR-12 (multi-select bulk-delete).
 *
 * Runs against an in-memory Room database so the assertion that `observeSummaries` re-emits
 * after the bulk delete exercises the real `InvalidationTracker`, not a stubbed flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementRepositoryImplBulkDeleteTest {

    private lateinit var database: TishinaDatabase
    private lateinit var dao: MeasurementDao
    private lateinit var repository: MeasurementRepositoryImpl

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TishinaDatabase::class.java)
            .addCallback(TishinaDatabase.LENGTH_GUARD_CALLBACK)
            .allowMainThreadQueries()
            .build()
        dao = database.measurementDao()
        repository = MeasurementRepositoryImpl(dao, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleteAll removes the targeted measurements and cascades into samples`() = runTest {
        val keep = repository.save(newMeasurement(title = "keep", createdAtEpochMs = 1_000L))
        val drop1 = repository.save(
            newMeasurement(
                title = "drop1",
                createdAtEpochMs = 2_000L,
                samples = List(4) { SoundSample(db = 40f, timestampMs = it * 200L) },
            ),
        )
        val drop2 = repository.save(
            newMeasurement(
                title = "drop2",
                createdAtEpochMs = 3_000L,
                samples = List(6) { SoundSample(db = 50f, timestampMs = it * 200L) },
            ),
        )

        repository.deleteAll(setOf(drop1, drop2))

        assertNotNull(repository.getById(keep))
        assertNull(repository.getById(drop1))
        assertNull(repository.getById(drop2))
        // Cascade — every sample row that pointed at the deleted measurements is gone.
        assertEquals(0, dao.countSamplesForMeasurement(drop1))
        assertEquals(0, dao.countSamplesForMeasurement(drop2))
    }

    @Test
    fun `deleteAll triggers a new observeSummaries emission`() = runTest {
        val keep = repository.save(newMeasurement(title = "keep", createdAtEpochMs = 1_000L))
        val drop = repository.save(newMeasurement(title = "drop", createdAtEpochMs = 2_000L))

        repository.observeSummaries().test {
            val initial = awaitItem().map { it.id }.toSet()
            assertEquals(setOf(keep, drop), initial)

            repository.deleteAll(setOf(drop))

            val afterDelete = awaitItem().map { it.id }
            assertEquals(listOf(keep), afterDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll with empty set is a no-op and does not emit a new value`() = runTest {
        val id = repository.save(newMeasurement())

        // No exception, no DB hit (the Repository short-circuits before reaching the DAO).
        repository.deleteAll(emptySet())

        val summaries = repository.observeSummaries().first()
        assertEquals(listOf(id), summaries.map { it.id })
    }

    @Test
    fun `deleteAll with only unknown ids is a no-op`() = runTest {
        val id = repository.save(newMeasurement())

        repository.deleteAll(setOf(99_999L, 88_888L))

        assertNotNull(repository.getById(id))
    }

    @Test
    fun `deleteAll then save reflects both operations in the next emission`() = runTest {
        val toDrop = repository.save(newMeasurement(title = "drop", createdAtEpochMs = 1_000L))

        repository.deleteAll(setOf(toDrop))
        val newId = repository.save(newMeasurement(title = "fresh", createdAtEpochMs = 2_000L))

        val summaries = repository.observeSummaries().first()
        assertEquals(listOf(newId), summaries.map { it.id })
        assertEquals("fresh", summaries.single().title)
    }

    private fun newMeasurement(
        createdAtEpochMs: Long = 1_700_000_000_000L,
        title: String? = "Test",
        samples: List<SoundSample> = listOf(SoundSample(db = 35f, timestampMs = 0L)),
    ): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAtEpochMs,
        durationMs = 5_000L,
        avgDb = 35.0f,
        minDb = 30.0f,
        maxDb = 40.0f,
        title = title,
        note = null,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0.0f,
        sampleRateHz = 48_000,
        samples = samples,
    )
}
