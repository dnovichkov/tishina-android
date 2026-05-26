package ru.dmdp.tishina.core.data.db

import android.os.Build
import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity

/**
 * Contract for [MeasurementDao.deleteByIds] — the batch-delete primitive that powers
 * History bulk-delete (FR-12) via [ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl.deleteAll].
 *
 * Robolectric + Room.inMemoryDatabaseBuilder exercises the real SQLite engine including
 * the `FOREIGN KEY ... ON DELETE CASCADE` declaration installed by the schema v1 callback,
 * so the cascade-into-samples assertion below is end-to-end, not a mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementDaoBulkDeleteTest {

    private lateinit var database: TishinaDatabase
    private lateinit var dao: MeasurementDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TishinaDatabase::class.java)
            .addCallback(TishinaDatabase.LENGTH_GUARD_CALLBACK)
            .allowMainThreadQueries()
            .build()
        dao = database.measurementDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleteByIds removes only the targeted rows`() = runTest {
        val ids = seedFive()

        dao.deleteByIds(setOf(ids[0], ids[2], ids[4]))

        val remaining = dao.observeSummaries().first().map { it.id }
        assertEquals(setOf(ids[1], ids[3]), remaining.toSet())
    }

    @Test
    fun `deleteByIds cascades into samples for every deleted measurement`() = runTest {
        val ids = seedFive(samplesPerMeasurement = 10)

        dao.deleteByIds(setOf(ids[0], ids[2], ids[4]))

        // Cascade must drop every sample row whose FK pointed at a deleted measurement.
        assertEquals(0, dao.countSamplesForMeasurement(ids[0]))
        assertEquals(0, dao.countSamplesForMeasurement(ids[2]))
        assertEquals(0, dao.countSamplesForMeasurement(ids[4]))
        // Survivors keep their full sample set.
        assertEquals(10, dao.countSamplesForMeasurement(ids[1]))
        assertEquals(10, dao.countSamplesForMeasurement(ids[3]))
    }

    @Test
    fun `deleteByIds with empty set leaves all rows untouched`() = runTest {
        val ids = seedFive()

        // Must not throw — empty `IN ()` is a well-known SQLite footgun. Room ≥ 2.5
        // generates `IN (NULL)` for empty collections so the query is syntactically
        // valid but matches no rows; the DAO contract preserves that guarantee.
        dao.deleteByIds(emptySet())

        val remaining = dao.observeSummaries().first().map { it.id }
        assertEquals(ids.toSet(), remaining.toSet())
    }

    @Test
    fun `deleteByIds with only unknown ids is a no-op`() = runTest {
        val ids = seedFive()

        dao.deleteByIds(setOf(99_999L, 88_888L, 77_777L))

        val remaining = dao.observeSummaries().first().map { it.id }
        assertEquals(ids.toSet(), remaining.toSet())
    }

    @Test
    fun `deleteByIds removes only the existing ids in a mixed set`() = runTest {
        val ids = seedFive()

        dao.deleteByIds(setOf(ids[1], 99_999L, ids[3], 88_888L))

        val remaining = dao.observeSummaries().first().map { it.id }
        assertEquals(setOf(ids[0], ids[2], ids[4]), remaining.toSet())
    }

    @Test
    fun `deleteByIds handles large batches without SQLITE_MAX_VARIABLE_NUMBER errors`() = runTest {
        // SQLite caps host parameters per query at 999 (legacy) or 32766 (3.32+). Room binds
        // every collection element as a separate `?`, so a 200-id batch is well within limits
        // for SQLite 3.18+ shipped with `minSdk = 26`. This smoke test guards against future
        // regressions where someone replaces the @Query with a manual rawQuery that forgets
        // the binding limit.
        val ids = List(200) { index ->
            dao.insertWithSamples(sampleMeasurement(createdAt = 1_000L + index), oneSample())
        }

        dao.deleteByIds(ids.toSet())

        assertEquals(emptyList<Any>(), dao.observeSummaries().first())
    }

    @Test
    fun `deleteByIds triggers a new observeSummaries emission`() = runTest {
        val ids = seedFive()

        dao.observeSummaries().test {
            val initial = awaitItem().map { it.id }.toSet()
            assertEquals(ids.toSet(), initial)

            dao.deleteByIds(setOf(ids[0], ids[1]))

            val afterDelete = awaitItem().map { it.id }.toSet()
            assertEquals(setOf(ids[2], ids[3], ids[4]), afterDelete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteByIds with single id matches single-row delete contract`() = runTest {
        val ids = seedFive()

        dao.deleteByIds(setOf(ids[2]))

        assertNull(dao.getDetailsById(ids[2]))
        // Untouched neighbours still load.
        assertNotNull(dao.getDetailsById(ids[1]))
        assertNotNull(dao.getDetailsById(ids[3]))
    }

    /**
     * Inserts [count] measurements each with [samplesPerMeasurement] samples and returns
     * their freshly minted ids in insertion order. `createdAtEpochMs` is monotonically
     * increasing so the ascending order in the returned list matches the descending order
     * the DAO will yield from `observeSummaries`.
     */
    private suspend fun seedFive(count: Int = 5, samplesPerMeasurement: Int = 3): List<Long> {
        assertTrue("Test helper expects positive count", count > 0)
        return (0 until count).map { index ->
            dao.insertWithSamples(
                sampleMeasurement(createdAt = 1_000L + index, title = "row $index"),
                samples = List(samplesPerMeasurement) { sIdx ->
                    SampleEntity(measurementId = 0L, tOffsetMs = sIdx * 200L, db = 40.0f + sIdx)
                },
            )
        }
    }

    private fun sampleMeasurement(
        createdAt: Long = 1_000L,
        title: String? = "Test",
    ): MeasurementEntity = MeasurementEntity(
        id = 0L,
        createdAtEpochMs = createdAt,
        durationMs = 5_000L,
        avgDb = 35.0f,
        minDb = 30.0f,
        maxDb = 40.0f,
        title = title,
        note = null,
        weighting = "A",
        timeWeighting = "FAST",
        calibrationOffsetDb = 0.0f,
        sampleRateHz = 48_000,
    )

    private fun oneSample(): List<SampleEntity> = listOf(
        SampleEntity(measurementId = 0L, tOffsetMs = 0L, db = 35.0f),
    )
}
