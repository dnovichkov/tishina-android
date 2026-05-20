package ru.dmdp.tishina.core.data.mapper

import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Pure-Kotlin Entity↔Domain conversions used by [ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl].
 *
 * **Why these are top-level extension functions, not a class:**
 * The conversions are stateless one-liners; wrapping them in a `MeasurementMapper` class
 * would only add ceremony with no testability benefit (they're directly testable as is).
 *
 * **Enum serialization strategy (matches [MeasurementEntity] kdoc):**
 * - [FrequencyWeighting] / [TimeWeighting] are stored as `Enum.name` strings so adding
 *   the future `C` weighting (Phase 4) requires no schema migration — only mapper updates.
 * - Unknown strings (e.g. from a manual sqlite edit) fall back to the safest default:
 *   `FrequencyWeighting.A` (used by FR-15 by default) and `TimeWeighting.FAST` (FR-16
 *   default). This keeps historical records readable instead of crashing the Detail screen.
 */
internal fun MeasurementEntity.toSummary(sparkline: List<Float>): MeasurementSummary =
    MeasurementSummary(
        id = id,
        createdAtEpochMs = createdAtEpochMs,
        durationMs = durationMs,
        avgDb = avgDb,
        minDb = minDb,
        maxDb = maxDb,
        title = title,
        note = note,
        sparklinePreview = sparkline,
    )

internal fun MeasurementEntity.toDetails(
    samples: List<SoundSample>,
    sparkline: List<Float>,
): MeasurementDetails = MeasurementDetails(
    summary = toSummary(sparkline),
    samples = samples,
    weighting = weighting.toFrequencyWeighting(),
    timeWeighting = timeWeighting.toTimeWeighting(),
    calibrationOffsetDb = calibrationOffsetDb,
    sampleRateHz = sampleRateHz,
)

internal fun NewMeasurement.toEntity(): MeasurementEntity = MeasurementEntity(
    id = 0L, // Room autoGenerate assigns the real id on insert.
    createdAtEpochMs = createdAtEpochMs,
    durationMs = durationMs,
    avgDb = avgDb,
    minDb = minDb,
    maxDb = maxDb,
    title = title,
    note = note,
    weighting = weighting.name,
    timeWeighting = timeWeighting.name,
    calibrationOffsetDb = calibrationOffsetDb,
    sampleRateHz = sampleRateHz,
)

internal fun SoundSample.toEntity(measurementId: Long): SampleEntity = SampleEntity(
    id = 0L,
    measurementId = measurementId,
    tOffsetMs = timestampMs,
    db = db,
)

internal fun SampleEntity.toDomain(): SoundSample = SoundSample(db = db, timestampMs = tOffsetMs)

private fun String.toFrequencyWeighting(): FrequencyWeighting = when (this) {
    "A" -> FrequencyWeighting.A
    "Z" -> FrequencyWeighting.Z
    else -> FrequencyWeighting.A
}

private fun String.toTimeWeighting(): TimeWeighting = when (this) {
    "FAST" -> TimeWeighting.FAST
    "SLOW" -> TimeWeighting.SLOW
    else -> TimeWeighting.FAST
}
