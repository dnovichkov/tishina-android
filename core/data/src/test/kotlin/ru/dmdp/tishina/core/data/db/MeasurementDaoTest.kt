package ru.dmdp.tishina.core.data.db

import android.database.sqlite.SQLiteConstraintException
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
import org.junit.Assert.fail
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
 * Contract for [MeasurementDao] running against an in-memory Room database.
 *
 * Why Robolectric + Room.inMemoryDatabaseBuilder: real SQLite + real FK/CHECK enforcement,
 * but no on-disk file and no emulator boot, so it stays in the unit-test ring (sub-second
 * per case). This is the canonical Phase 3 testing strategy from the plan § "Testing
 * Strategy" — Data layer is allowed to be Robolectric because the value-add of the real
 * SQLite engine far outweighs the per-test cost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementDaoTest {

    private lateinit var database: TishinaDatabase
    private lateinit var dao: MeasurementDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TishinaDatabase::class.java)
            // The CHECK-like length triggers are installed by this callback. The production
            // DataModule binding will register the same callback (Task 3) so the contract
            // tested here is the contract production code observes.
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
    fun `insertWithSamples stores aggregate and all samples atomically`() = runTest {
        val measurement = sampleMeasurement(createdAt = 1_000L)
        val samples = listOf(
            SampleEntity(measurementId = 0L, tOffsetMs = 0L, db = 30.0f),
            SampleEntity(measurementId = 0L, tOffsetMs = 200L, db = 31.5f),
            SampleEntity(measurementId = 0L, tOffsetMs = 400L, db = 32.5f),
            SampleEntity(measurementId = 0L, tOffsetMs = 600L, db = 33.0f),
            SampleEntity(measurementId = 0L, tOffsetMs = 800L, db = 30.5f),
        )

        val id = dao.insertWithSamples(measurement, samples)

        assertTrue("Inserted id must be positive", id > 0)
        val loaded = dao.getDetailsById(id)
        assertNotNull(loaded)
        assertEquals(5, loaded!!.samples.size)
        // Samples auto-renumber their FK on insert — verify they point at our row.
        assertTrue(loaded.samples.all { it.measurementId == id })
    }

    @Test
    fun `observeSummaries emits rows in descending createdAt order`() = runTest {
        dao.insertWithSamples(sampleMeasurement(createdAt = 1_000L, title = "old"), oneSample())
        dao.insertWithSamples(sampleMeasurement(createdAt = 3_000L, title = "new"), oneSample())
        dao.insertWithSamples(sampleMeasurement(createdAt = 2_000L, title = "mid"), oneSample())

        val rows = dao.observeSummaries().first()

        assertEquals(listOf("new", "mid", "old"), rows.map { it.title })
    }

    @Test
    fun `observeSummaries emits new value after delete`() = runTest {
        val id1 = dao.insertWithSamples(sampleMeasurement(createdAt = 1_000L, title = "keep"), oneSample())
        val id2 = dao.insertWithSamples(sampleMeasurement(createdAt = 2_000L, title = "drop"), oneSample())

        dao.observeSummaries().test {
            val initial = awaitItem()
            assertEquals(setOf(id1, id2), initial.map { it.id }.toSet())

            dao.delete(id2)

            val afterDelete = awaitItem()
            assertEquals(listOf(id1), afterDelete.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(dao.getDetailsById(9_999L))
    }

    @Test
    fun `getById returns details bundled with all samples`() = runTest {
        val samples = listOf(
            SampleEntity(measurementId = 0L, tOffsetMs = 0L, db = 40.0f),
            SampleEntity(measurementId = 0L, tOffsetMs = 200L, db = 45.0f),
        )
        val id = dao.insertWithSamples(sampleMeasurement(), samples)

        val details = dao.getDetailsById(id)

        assertNotNull(details)
        assertEquals(2, details!!.samples.size)
        assertEquals(40.0f, details.samples.first().db, 0.0001f)
        assertEquals(200L, details.samples[1].tOffsetMs)
    }

    @Test
    fun `delete cascades into samples table`() = runTest {
        val id = dao.insertWithSamples(
            sampleMeasurement(),
            samples = List(4) { SampleEntity(measurementId = 0L, tOffsetMs = it * 200L, db = 50.0f) },
        )
        assertEquals(4, dao.countSamplesForMeasurement(id))

        dao.delete(id)

        assertEquals("Samples must cascade-delete with their measurement", 0, dao.countSamplesForMeasurement(id))
        assertNull(dao.getDetailsById(id))
    }

    @Test
    fun `updateNote changes only the note column`() = runTest {
        val id = dao.insertWithSamples(
            sampleMeasurement(title = "keep me", note = "old note"),
            oneSample(),
        )

        dao.updateNote(id, "new note")

        val details = dao.getDetailsById(id)!!
        assertEquals("new note", details.measurement.note)
        assertEquals("keep me", details.measurement.title)
    }

    @Test
    fun `updateNote accepts null to clear the field`() = runTest {
        val id = dao.insertWithSamples(sampleMeasurement(note = "draft"), oneSample())

        dao.updateNote(id, null)

        assertNull(dao.getDetailsById(id)!!.measurement.note)
    }

    @Test
    fun `insertMeasurement rejects title longer than 80 characters via CHECK constraint`() = runTest {
        val tooLong = "x".repeat(81)
        val measurement = sampleMeasurement(title = tooLong)

        try {
            dao.insertWithSamples(measurement, oneSample())
            fail("Expected SQLiteConstraintException for title > 80 chars")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(expected.message?.contains("CHECK", ignoreCase = true) == true)
        }
    }

    @Test
    fun `insertMeasurement rejects note longer than 200 characters via CHECK constraint`() = runTest {
        val measurement = sampleMeasurement(note = "y".repeat(201))

        try {
            dao.insertWithSamples(measurement, oneSample())
            fail("Expected SQLiteConstraintException for note > 200 chars")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(expected.message?.contains("CHECK", ignoreCase = true) == true)
        }
    }

    @Test
    fun `insertMeasurement accepts null title and null note`() = runTest {
        val id = dao.insertWithSamples(sampleMeasurement(title = null, note = null), oneSample())

        val details = dao.getDetailsById(id)!!.measurement
        assertNull(details.title)
        assertNull(details.note)
    }

    @Test
    fun `loadSparklinePreview returns up to 20 db values for the measurement`() = runTest {
        val samples = List(50) { index ->
            SampleEntity(measurementId = 0L, tOffsetMs = index * 200L, db = 40.0f + index)
        }
        val id = dao.insertWithSamples(sampleMeasurement(), samples)

        val sparkline = dao.loadSparklinePreview(id)

        assertEquals(20, sparkline.size)
        // Stored values are read in tOffsetMs order — first should be the earliest db.
        assertEquals(40.0f, sparkline.first(), 0.0001f)
    }

    @Test
    fun `loadSparklinePreview returns fewer than 20 when measurement has fewer samples`() = runTest {
        val samples = List(7) { SampleEntity(measurementId = 0L, tOffsetMs = it * 200L, db = 60.0f) }
        val id = dao.insertWithSamples(sampleMeasurement(), samples)

        val sparkline = dao.loadSparklinePreview(id)

        assertEquals(7, sparkline.size)
    }

    private fun sampleMeasurement(
        createdAt: Long = 1_000L,
        title: String? = "Test",
        note: String? = null,
    ): MeasurementEntity = MeasurementEntity(
        id = 0L,
        createdAtEpochMs = createdAt,
        durationMs = 5_000L,
        avgDb = 35.0f,
        minDb = 30.0f,
        maxDb = 40.0f,
        title = title,
        note = note,
        weighting = "A",
        timeWeighting = "FAST",
        calibrationOffsetDb = 0.0f,
        sampleRateHz = 48_000,
    )

    private fun oneSample(): List<SampleEntity> = listOf(
        SampleEntity(measurementId = 0L, tOffsetMs = 0L, db = 35.0f),
    )
}
