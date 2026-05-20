package ru.dmdp.tishina.core.data.repository

import android.os.Build
import androidx.room.Room
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Sparkline-specific contract: `observeSummaries` must fetch up to 20 db values per
 * summary from the `samples` table (via `MeasurementDao.loadSparklinePreview`) and attach
 * them as `MeasurementSummary.sparklinePreview`.
 *
 * Edge cases:
 * - When a measurement has < 20 samples, return all of them (no zero-padding).
 * - When > 20, return exactly 20.
 * - Measurements with zero samples (which shouldn't happen in production but defensively
 *   handled) yield an empty list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class MeasurementRepositoryImplSparklineTest {

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
    fun `summary sparkline has at most 20 points for long measurements`() = runTest {
        val samples = List(75) { SoundSample(db = 40.0f + it, timestampMs = it * 200L) }
        repository.save(newMeasurement(samples = samples))

        val summary = repository.observeSummaries().first().single()

        assertEquals(20, summary.sparklinePreview.size)
    }

    @Test
    fun `summary sparkline returns all points when measurement has fewer than 20`() = runTest {
        val samples = List(5) { SoundSample(db = 50.0f, timestampMs = it * 200L) }
        repository.save(newMeasurement(samples = samples))

        val summary = repository.observeSummaries().first().single()

        assertEquals(5, summary.sparklinePreview.size)
        assertTrue(summary.sparklinePreview.all { it == 50.0f })
    }

    @Test
    fun `summary sparkline is ordered by sample tOffset ascending`() = runTest {
        // Inserts in non-monotonic offset order to make sure the query / mapper enforces ASC.
        val samples = listOf(
            SoundSample(db = 30.0f, timestampMs = 0L),
            SoundSample(db = 32.0f, timestampMs = 200L),
            SoundSample(db = 34.0f, timestampMs = 400L),
        )
        repository.save(newMeasurement(samples = samples))

        val summary = repository.observeSummaries().first().single()

        assertEquals(listOf(30.0f, 32.0f, 34.0f), summary.sparklinePreview)
    }

    @Test
    fun `each summary gets its own sparkline when multiple measurements are stored`() = runTest {
        repository.save(
            newMeasurement(
                createdAtEpochMs = 1_000L,
                title = "older",
                samples = List(3) { SoundSample(db = 20.0f + it, timestampMs = it * 200L) },
            ),
        )
        repository.save(
            newMeasurement(
                createdAtEpochMs = 2_000L,
                title = "newer",
                samples = List(2) { SoundSample(db = 70.0f + it, timestampMs = it * 200L) },
            ),
        )

        val summaries = repository.observeSummaries().first()

        // Sorted descending by createdAt — "newer" first.
        assertEquals("newer", summaries[0].title)
        assertEquals(listOf(70.0f, 71.0f), summaries[0].sparklinePreview)
        assertEquals("older", summaries[1].title)
        assertEquals(listOf(20.0f, 21.0f, 22.0f), summaries[1].sparklinePreview)
    }

    private fun newMeasurement(
        createdAtEpochMs: Long = 1_700_000_000_000L,
        title: String? = "Test",
        samples: List<SoundSample>,
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
