package ru.dmdp.tishina.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregate row stored in the `measurements` table.
 *
 * Mirrors [ru.dmdp.tishina.core.domain.model.MeasurementSummary] / [ru.dmdp.tishina.core.domain.model.MeasurementDetails]
 * minus the sample stream (which lives in [SampleEntity] for streaming-friendly inserts).
 *
 * Frequency and time weighting are persisted as their `Enum.name` strings ("A" / "Z",
 * "FAST" / "SLOW") so adding a future weighting (C) does not require a schema migration —
 * only a defensive parser update in the mapper.
 *
 * Length CHECK constraints (`LENGTH(title) <= 80`, `LENGTH(note) <= 200`) are enforced via
 * triggers installed by [ru.dmdp.tishina.core.data.db.TishinaDatabase]; declaring them on
 * the entity alone is not supported by Room 2.8, but trigger-based defense in depth gives
 * us the same guarantee.
 */
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo(name = "createdAtEpochMs")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "durationMs")
    val durationMs: Long,
    @ColumnInfo(name = "avgDb")
    val avgDb: Float,
    @ColumnInfo(name = "minDb")
    val minDb: Float,
    @ColumnInfo(name = "maxDb")
    val maxDb: Float,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "weighting")
    val weighting: String,
    @ColumnInfo(name = "timeWeighting")
    val timeWeighting: String,
    @ColumnInfo(name = "calibrationOffsetDb")
    val calibrationOffsetDb: Float,
    @ColumnInfo(name = "sampleRateHz")
    val sampleRateHz: Int,
)
