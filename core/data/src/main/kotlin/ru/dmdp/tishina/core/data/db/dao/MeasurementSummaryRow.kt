package ru.dmdp.tishina.core.data.db.dao

import androidx.room.ColumnInfo

/**
 * Slim projection used by [MeasurementDao.observeSummaries] — pulls only the fields the
 * History list renders, skipping the per-row sample stream (loaded separately as a
 * sparkline preview). Keeps the hot list query cheap even with thousands of rows.
 */
data class MeasurementSummaryRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "createdAtEpochMs") val createdAtEpochMs: Long,
    @ColumnInfo(name = "durationMs") val durationMs: Long,
    @ColumnInfo(name = "avgDb") val avgDb: Float,
    @ColumnInfo(name = "minDb") val minDb: Float,
    @ColumnInfo(name = "maxDb") val maxDb: Float,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "note") val note: String?,
)
