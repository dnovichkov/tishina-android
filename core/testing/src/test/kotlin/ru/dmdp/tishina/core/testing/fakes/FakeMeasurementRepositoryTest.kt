package ru.dmdp.tishina.core.testing.fakes

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

@DisplayName("FakeMeasurementRepository — in-memory MeasurementRepository for unit tests")
class FakeMeasurementRepositoryTest {

    private fun newMeasurement(
        createdAt: Long = 1_700_000_000_000L,
        title: String? = "Спальня",
        note: String? = null,
        samples: List<SoundSample> = listOf(SoundSample(40f, 0L)),
    ) = NewMeasurement(
        createdAtEpochMs = createdAt,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 44_100,
        samples = samples,
    )

    @Test
    fun `starts empty`() = runTest {
        val repo = FakeMeasurementRepository()
        assertEquals(0, repo.size())
        assertEquals(emptyList<Any>(), repo.observeSummaries().first())
    }

    @Test
    fun `save assigns monotonic ids starting at 1`() = runTest {
        val repo = FakeMeasurementRepository()
        val id1 = repo.save(newMeasurement(createdAt = 100L))
        val id2 = repo.save(newMeasurement(createdAt = 200L))
        val id3 = repo.save(newMeasurement(createdAt = 300L))

        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
    }

    @Test
    fun `observeSummaries returns rows in descending createdAt order`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.save(newMeasurement(createdAt = 100L))
        repo.save(newMeasurement(createdAt = 300L))
        repo.save(newMeasurement(createdAt = 200L))

        val summaries = repo.observeSummaries().first()
        assertEquals(listOf(300L, 200L, 100L), summaries.map { it.createdAtEpochMs })
    }

    @Test
    fun `observeSummaries emits a fresh list whenever the store changes`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.observeSummaries().test {
            assertTrue(awaitItem().isEmpty())

            repo.save(newMeasurement(createdAt = 100L))
            assertEquals(1, awaitItem().size)

            repo.save(newMeasurement(createdAt = 200L))
            assertEquals(2, awaitItem().size)

            repo.delete(1L)
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun `getById returns saved details for known id`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement(title = "Кухня"))

        val details = repo.getById(id)

        assertNotNull(details)
        assertEquals("Кухня", details?.summary?.title)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        val repo = FakeMeasurementRepository()
        assertNull(repo.getById(42L))
    }

    @Test
    fun `delete removes the measurement`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement())

        repo.delete(id)

        assertNull(repo.getById(id))
        assertEquals(0, repo.size())
    }

    @Test
    fun `delete is idempotent for unknown id`() = runTest {
        val repo = FakeMeasurementRepository()
        // No exception, no state change.
        repo.delete(99_999L)
        assertEquals(0, repo.size())
    }

    @Test
    fun `deleteAll removes only the listed ids, leaving the rest intact`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                newMeasurement(createdAt = 100L),
                newMeasurement(createdAt = 200L),
                newMeasurement(createdAt = 300L),
            ),
        )

        repo.deleteAll(setOf(ids[0], ids[2]))

        val remaining = repo.observeSummaries().first()
        assertEquals(listOf(ids[1]), remaining.map { it.id })
        assertEquals(1, repo.size())
    }

    @Test
    fun `deleteAll with empty set is a silent no-op (does not throw)`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.seed(listOf(newMeasurement(), newMeasurement()))

        repo.deleteAll(emptySet())

        assertEquals(2, repo.size())
    }

    @Test
    fun `deleteAll with unknown ids is idempotent (skips missing rows)`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(listOf(newMeasurement(createdAt = 100L)))

        repo.deleteAll(setOf(99_999L, 88_888L))

        assertEquals(1, repo.size())
        assertNotNull(repo.getById(ids[0]))
    }

    @Test
    fun `deleteAll with mixed (existing + unknown) ids removes only what exists`() = runTest {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                newMeasurement(createdAt = 100L),
                newMeasurement(createdAt = 200L),
            ),
        )

        repo.deleteAll(setOf(ids[0], 99_999L))

        assertEquals(1, repo.size())
        assertNull(repo.getById(ids[0]))
        assertNotNull(repo.getById(ids[1]))
    }

    @Test
    fun `deleteAll emits a fresh list through observeSummaries`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.observeSummaries().test {
            assertTrue(awaitItem().isEmpty())

            repo.save(newMeasurement(createdAt = 100L))
            assertEquals(1, awaitItem().size)

            repo.save(newMeasurement(createdAt = 200L))
            assertEquals(2, awaitItem().size)

            repo.deleteAll(setOf(1L, 2L))
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `updateNote replaces only the note field`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement(title = "T", note = "initial"))

        repo.updateNote(id, "updated")

        val after = repo.getById(id)
        assertEquals("updated", after?.summary?.note)
        assertEquals("T", after?.summary?.title)
    }

    @Test
    fun `updateNote with null clears the note`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement(note = "to clear"))

        repo.updateNote(id, null)

        assertNull(repo.getById(id)?.summary?.note)
    }

    @Test
    fun `updateNote for unknown id is a silent no-op`() = runTest {
        val repo = FakeMeasurementRepository()
        repo.updateNote(99_999L, "ignored")
        assertEquals(0, repo.size())
    }

    @Test
    fun `seed pre-populates without going through suspend save`() {
        val repo = FakeMeasurementRepository()
        val ids = repo.seed(
            listOf(
                newMeasurement(createdAt = 100L),
                newMeasurement(createdAt = 200L),
                newMeasurement(createdAt = 300L),
            ),
        )

        assertEquals(listOf(1L, 2L, 3L), ids)
        assertEquals(3, repo.size())
    }

    @Test
    fun `saveError causes the next save to throw, then resets`() = runTest {
        val repo = FakeMeasurementRepository()
        val boom = IllegalStateException("disk full")
        repo.saveError = boom

        val thrown = try {
            repo.save(newMeasurement())
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertEquals("disk full", thrown?.message)

        // saveError should have reset; the next save succeeds normally.
        val id = repo.save(newMeasurement())
        assertEquals(1L, id)
    }

    @Test
    fun `sparkline preview has one point for a single-sample input`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement(samples = listOf(SoundSample(40f, 0L))))

        val sparkline = repo.getById(id)?.summary?.sparklinePreview
        assertNotNull(sparkline)
        // 1 sample → at most 1 sparkline point.
        assertEquals(1, sparkline?.size)
    }

    @Test
    fun `sparkline preview is empty when input samples are empty`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(newMeasurement(samples = emptyList()))

        val sparkline = repo.getById(id)?.summary?.sparklinePreview
        assertNotNull(sparkline)
        assertEquals(0, sparkline?.size)
    }

    @Test
    fun `sparkline preview is downsampled to at most 20 points`() = runTest {
        val repo = FakeMeasurementRepository()
        val samples = (0 until 100).map { i -> SoundSample(40f + i, i.toLong() * 10L) }

        val id = repo.save(newMeasurement(samples = samples))

        val sparkline = repo.getById(id)?.summary?.sparklinePreview
        assertEquals(20, sparkline?.size)
        // 100 samples → step 5 → first picked sample is at index 0 (db = 40).
        assertEquals(40f, sparkline?.first())
    }

    @Test
    fun `saved details preserve the configuration snapshot from the DTO`() = runTest {
        val repo = FakeMeasurementRepository()
        val id = repo.save(
            newMeasurement().copy(
                weighting = FrequencyWeighting.Z,
                timeWeighting = TimeWeighting.SLOW,
                calibrationOffsetDb = -3.5f,
                sampleRateHz = 48_000,
            ),
        )

        val details = repo.getById(id)
        assertEquals(FrequencyWeighting.Z, details?.weighting)
        assertEquals(TimeWeighting.SLOW, details?.timeWeighting)
        assertEquals(-3.5f, details?.calibrationOffsetDb)
        assertEquals(48_000, details?.sampleRateHz)
    }

    @Test
    fun `each saved record gets a fresh summary id even when other fields are identical`() = runTest {
        val repo = FakeMeasurementRepository()
        val dto = newMeasurement()
        val id1 = repo.save(dto)
        val id2 = repo.save(dto)

        assertNotEquals(id1, id2)
        assertEquals(id1, repo.getById(id1)?.summary?.id)
        assertEquals(id2, repo.getById(id2)?.summary?.id)
    }
}
