package ru.dmdp.tishina.core.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Pure-Kotlin tests for the Entity↔Domain mapper. No Robolectric is needed — these only
 * verify field-by-field copying, enum serialization and null-handling.
 *
 * Coverage targets (plan § "Testing Strategy"):
 * - round-trip `MeasurementEntity` → `MeasurementSummary` (with sparkline) → assert all
 *   fields are preserved
 * - `MeasurementEntity` + `samples` → `MeasurementDetails` → assert the relation is hydrated
 * - `NewMeasurement` → `MeasurementEntity` does NOT carry samples (those go to the sample
 *   table) and serializes enums as `Enum.name` ("A"/"Z", "FAST"/"SLOW")
 * - `SoundSample` → `SampleEntity` sets the FK
 * - `SampleEntity` → `SoundSample` round-trips db and offset
 * - null `title` / `note` round-trip without coercion to empty strings
 */
class MeasurementMapperTest {

    @Test
    fun `entity toSummary copies every field and attaches sparkline`() {
        val entity = MeasurementEntity(
            id = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            durationMs = 60_000L,
            avgDb = 35.5f,
            minDb = 22.1f,
            maxDb = 48.3f,
            title = "Bedroom",
            note = "late evening",
            weighting = "A",
            timeWeighting = "FAST",
            calibrationOffsetDb = -1.5f,
            sampleRateHz = 48_000,
        )
        val sparkline = listOf(30.0f, 31.5f, 32.0f)

        val summary = entity.toSummary(sparkline)

        assertEquals(7L, summary.id)
        assertEquals(1_700_000_000_000L, summary.createdAtEpochMs)
        assertEquals(60_000L, summary.durationMs)
        assertEquals(35.5f, summary.avgDb, 0.0001f)
        assertEquals(22.1f, summary.minDb, 0.0001f)
        assertEquals(48.3f, summary.maxDb, 0.0001f)
        assertEquals("Bedroom", summary.title)
        assertEquals("late evening", summary.note)
        assertEquals(sparkline, summary.sparklinePreview)
    }

    @Test
    fun `entity toSummary preserves null title and null note`() {
        val entity = sampleEntity(title = null, note = null)

        val summary = entity.toSummary(sparkline = emptyList())

        assertNull(summary.title)
        assertNull(summary.note)
        assertTrue(summary.sparklinePreview.isEmpty())
    }

    @Test
    fun `entity toDetails wraps summary and decodes weighting enums`() {
        val entity = sampleEntity(weighting = "Z", timeWeighting = "SLOW")
        val samples = listOf(
            SampleEntity(id = 1L, measurementId = entity.id, tOffsetMs = 0L, db = 40.0f),
            SampleEntity(id = 2L, measurementId = entity.id, tOffsetMs = 200L, db = 42.5f),
        )

        val details = entity.toDetails(
            samples = samples.map(SampleEntity::toDomain),
            sparkline = listOf(40.0f, 42.5f),
        )

        assertEquals(FrequencyWeighting.Z, details.weighting)
        assertEquals(TimeWeighting.SLOW, details.timeWeighting)
        assertEquals(2, details.samples.size)
        assertEquals(40.0f, details.samples.first().db, 0.0001f)
        assertEquals(200L, details.samples[1].timestampMs)
        // The embedded summary should match what `toSummary` would have produced.
        assertEquals(entity.id, details.summary.id)
        assertEquals(listOf(40.0f, 42.5f), details.summary.sparklinePreview)
    }

    @Test
    fun `newMeasurement toEntity strips samples and serializes weighting as Enum name`() {
        val sample = SoundSample(db = 33.3f, timestampMs = 200L)
        val dto = NewMeasurement(
            createdAtEpochMs = 1_700L,
            durationMs = 1_200L,
            avgDb = 33.0f,
            minDb = 30.0f,
            maxDb = 36.0f,
            title = "Living room",
            note = null,
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.FAST,
            calibrationOffsetDb = 0.0f,
            sampleRateHz = 48_000,
            samples = listOf(sample),
        )

        val entity = dto.toEntity()

        assertEquals(0L, entity.id) // Room autoGenerate replaces 0 with the new row id.
        assertEquals(1_700L, entity.createdAtEpochMs)
        assertEquals(1_200L, entity.durationMs)
        assertEquals(33.0f, entity.avgDb, 0.0001f)
        assertEquals("Living room", entity.title)
        assertNull(entity.note)
        assertEquals("A", entity.weighting)
        assertEquals("FAST", entity.timeWeighting)
        assertEquals(48_000, entity.sampleRateHz)
    }

    @Test
    fun `newMeasurement toEntity supports Z weighting and SLOW time weighting`() {
        val dto = NewMeasurement(
            createdAtEpochMs = 1L,
            durationMs = 1L,
            avgDb = 0f,
            minDb = 0f,
            maxDb = 0f,
            title = null,
            note = null,
            weighting = FrequencyWeighting.Z,
            timeWeighting = TimeWeighting.SLOW,
            calibrationOffsetDb = 0f,
            sampleRateHz = 48_000,
            samples = emptyList(),
        )

        val entity = dto.toEntity()

        assertEquals("Z", entity.weighting)
        assertEquals("SLOW", entity.timeWeighting)
    }

    @Test
    fun `soundSample toEntity sets the measurementId foreign key`() {
        val sample = SoundSample(db = 55.5f, timestampMs = 1_400L)

        val entity = sample.toEntity(measurementId = 42L)

        assertEquals(0L, entity.id) // autoGenerate
        assertEquals(42L, entity.measurementId)
        assertEquals(1_400L, entity.tOffsetMs)
        assertEquals(55.5f, entity.db, 0.0001f)
    }

    @Test
    fun `sampleEntity toDomain round-trips db and offset`() {
        val entity = SampleEntity(id = 99L, measurementId = 1L, tOffsetMs = 600L, db = 47.2f)

        val sample = entity.toDomain()

        assertEquals(47.2f, sample.db, 0.0001f)
        assertEquals(600L, sample.timestampMs)
    }

    @Test
    fun `entity round-trip preserves all fields except id`() {
        // Verifies the inverse property: toEntity(toSummary(entity).bundleBackInto(dto))
        // doesn't drop any column we care about.
        val original = sampleEntity(
            id = 0L, // simulate "pre-insert" state from a NewMeasurement
            createdAtEpochMs = 123_456L,
            title = "Original",
            note = "preserve me",
            weighting = "A",
            timeWeighting = "SLOW",
            calibrationOffsetDb = 2.5f,
        )

        val dto = NewMeasurement(
            createdAtEpochMs = original.createdAtEpochMs,
            durationMs = original.durationMs,
            avgDb = original.avgDb,
            minDb = original.minDb,
            maxDb = original.maxDb,
            title = original.title,
            note = original.note,
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.SLOW,
            calibrationOffsetDb = original.calibrationOffsetDb,
            sampleRateHz = original.sampleRateHz,
            samples = emptyList(),
        )

        val roundTripped = dto.toEntity()

        // The only thing that differs is id (autoGenerate); everything else round-trips.
        assertNotSame(original, roundTripped)
        assertEquals(original.copy(id = 0L), roundTripped)
    }

    private fun sampleEntity(
        id: Long = 1L,
        createdAtEpochMs: Long = 1_000L,
        durationMs: Long = 5_000L,
        avgDb: Float = 35.0f,
        minDb: Float = 30.0f,
        maxDb: Float = 40.0f,
        title: String? = "Test",
        note: String? = null,
        weighting: String = "A",
        timeWeighting: String = "FAST",
        calibrationOffsetDb: Float = 0.0f,
        sampleRateHz: Int = 48_000,
    ): MeasurementEntity = MeasurementEntity(
        id = id,
        createdAtEpochMs = createdAtEpochMs,
        durationMs = durationMs,
        avgDb = avgDb,
        minDb = minDb,
        maxDb = maxDb,
        title = title,
        note = note,
        weighting = weighting,
        timeWeighting = timeWeighting,
        calibrationOffsetDb = calibrationOffsetDb,
        sampleRateHz = sampleRateHz,
    )
}
