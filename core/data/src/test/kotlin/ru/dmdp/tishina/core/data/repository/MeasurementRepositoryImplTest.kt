package ru.dmdp.tishina.core.data.repository

import android.os.Build
import androidx.room.Room
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import ru.dmdp.tishina.core.data.db.TishinaDatabase
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * End-to-end tests for [MeasurementRepositoryImpl] running against in-memory Room.
 *
 * These are the canonical "data layer correctness" tests from the plan § "Testing Strategy":
 * they exercise the full mapper graph (Entity ↔ Domain), the DAO transaction, and the
 * sparkline composition on top of the reactive `observeSummaries` flow — all in one place.
 *
 * Robolectric is required because Room needs an Android `Context` for the database builder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementRepositoryImplTest {

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
        // UnconfinedTestDispatcher keeps test code single-threaded and deterministic. The
        // production wiring will pass Dispatchers.IO through the @IoDispatcher qualifier.
        repository = MeasurementRepositoryImpl(dao, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save then getById round-trips fields and decodes weighting enums`() = runTest {
        val dto = newMeasurement(title = "Bedroom", note = "evening")

        val id = repository.save(dto)
        val details = repository.getById(id)

        assertNotNull(details)
        assertEquals(id, details!!.summary.id)
        assertEquals(dto.createdAtEpochMs, details.summary.createdAtEpochMs)
        assertEquals(dto.avgDb, details.summary.avgDb, 0.0001f)
        assertEquals(dto.title, details.summary.title)
        assertEquals(dto.note, details.summary.note)
        assertEquals(FrequencyWeighting.A, details.weighting)
        assertEquals(TimeWeighting.FAST, details.timeWeighting)
        assertEquals(dto.samples.size, details.samples.size)
        assertEquals(dto.samples.first().db, details.samples.first().db, 0.0001f)
        assertEquals(dto.samples.first().timestampMs, details.samples.first().timestampMs)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(repository.getById(99_999L))
    }

    @Test
    fun `observeSummaries emits new value after save`() = runTest {
        repository.observeSummaries().test {
            assertEquals(emptyList<Any>(), awaitItem())

            repository.save(newMeasurement(title = "first", createdAtEpochMs = 1_000L))

            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.size)
            assertEquals("first", afterFirst.first().title)

            repository.save(newMeasurement(title = "second", createdAtEpochMs = 2_000L))

            val afterSecond = awaitItem()
            assertEquals(2, afterSecond.size)
            // Descending by createdAt — "second" comes first.
            assertEquals(listOf("second", "first"), afterSecond.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes the measurement and cascades to samples`() = runTest {
        val id = repository.save(
            newMeasurement(samples = List(4) { SoundSample(db = 40f, timestampMs = it * 200L) }),
        )
        assertEquals(4, dao.countSamplesForMeasurement(id))

        repository.delete(id)

        assertNull(repository.getById(id))
        assertEquals(
            "Sample rows must vanish along with their parent measurement",
            0,
            dao.countSamplesForMeasurement(id),
        )
    }

    @Test
    fun `delete on unknown id is a no-op`() = runTest {
        // Should not throw — repository contract says delete is idempotent.
        repository.delete(123_456L)
    }

    @Test
    fun `updateNote with new value replaces only the note column`() = runTest {
        val id = repository.save(newMeasurement(title = "keep", note = "old"))

        repository.updateNote(id, "new")

        val details = repository.getById(id)!!
        assertEquals("new", details.summary.note)
        assertEquals("keep", details.summary.title)
    }

    @Test
    fun `updateNote with null clears the field`() = runTest {
        val id = repository.save(newMeasurement(note = "draft"))

        repository.updateNote(id, null)

        assertNull(repository.getById(id)!!.summary.note)
    }

    @Test
    fun `parallel saves all succeed with unique ids`() = runTest {
        val ids = coroutineScope {
            (0 until 5)
                .map { i -> async { repository.save(newMeasurement(createdAtEpochMs = 1_000L + i)) } }
                .awaitAll()
        }

        assertEquals("Each save must yield a distinct id", 5, ids.toSet().size)
        val summaries = repository.observeSummaries().first()
        assertEquals(5, summaries.size)
        assertTrue(summaries.all { it.id in ids })
    }

    private fun newMeasurement(
        createdAtEpochMs: Long = 1_700_000_000_000L,
        title: String? = "Test",
        note: String? = null,
        samples: List<SoundSample> = listOf(SoundSample(db = 35f, timestampMs = 0L)),
    ): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAtEpochMs,
        durationMs = 5_000L,
        avgDb = 35.0f,
        minDb = 30.0f,
        maxDb = 40.0f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0.0f,
        sampleRateHz = 48_000,
        samples = samples,
    )
}
