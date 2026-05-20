package ru.dmdp.tishina.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One downsampled (5 Hz) dB(A) reading owned by a [MeasurementEntity].
 *
 * Foreign-keyed with `ON DELETE CASCADE` so removing the parent measurement automatically
 * sweeps its samples in the same transaction — no orphaned rows even if a caller talks to
 * the DAO directly. The index on `measurementId` is required by Room (it warns otherwise)
 * and accelerates the join in `getDetailsById`.
 *
 * `tOffsetMs` is the offset from the start of the measurement, NOT epoch — same convention
 * as [ru.dmdp.tishina.core.domain.model.SoundSample.timestampMs]; makes synthetic tests
 * trivially reproducible.
 */
@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("measurementId")],
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "measurementId")
    val measurementId: Long,
    @ColumnInfo(name = "tOffsetMs")
    val tOffsetMs: Long,
    @ColumnInfo(name = "db")
    val db: Float,
)
