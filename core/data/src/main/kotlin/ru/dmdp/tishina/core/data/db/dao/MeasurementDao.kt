package ru.dmdp.tishina.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity

/**
 * Single DAO covering both `measurements` and `samples` tables.
 *
 * Kept as one DAO because every persistence operation in Phase 3 either reads or writes
 * both tables together. Splitting them would force the repository to coordinate two DAOs
 * and reason about transaction boundaries — the current shape keeps the contract clear.
 */
@Dao
abstract class MeasurementDao {

    /**
     * Atomically inserts the measurement plus all its samples. Samples are written with
     * the freshly minted `measurementId` regardless of the value the caller passed in,
     * so call-sites do not need to know the new id ahead of time.
     *
     * Returns the inserted row id.
     */
    @Transaction
    open suspend fun insertWithSamples(
        measurement: MeasurementEntity,
        samples: List<SampleEntity>,
    ): Long {
        val newId = insertMeasurement(measurement)
        if (samples.isNotEmpty()) {
            insertSamples(samples.map { it.copy(measurementId = newId) })
        }
        return newId
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMeasurement(entity: MeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSamples(entities: List<SampleEntity>)

    @Query(
        """
            SELECT id, createdAtEpochMs, durationMs, avgDb, minDb, maxDb, title, note
            FROM measurements
            ORDER BY createdAtEpochMs DESC
        """,
    )
    abstract fun observeSummaries(): Flow<List<MeasurementSummaryRow>>

    @Transaction
    @Query("SELECT * FROM measurements WHERE id = :id LIMIT 1")
    abstract suspend fun getDetailsById(id: Long): MeasurementWithSamples?

    /**
     * Preview vector for the History card sparkline. 20 evenly-tOffset-ordered points is
     * a deliberate trade-off: for a one-hour session it gives one tick per three minutes,
     * which is coarse but enough to convey "spiky vs steady" at card scale.
     */
    @Query(
        """
            SELECT db FROM samples
            WHERE measurementId = :id
            ORDER BY tOffsetMs ASC
            LIMIT 20
        """,
    )
    abstract suspend fun loadSparklinePreview(id: Long): List<Float>

    @Query("DELETE FROM measurements WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE measurements SET note = :note WHERE id = :id")
    abstract suspend fun updateNote(id: Long, note: String?)

    /** Test-only helper for asserting cascade behavior. */
    @Query("SELECT COUNT(*) FROM samples WHERE measurementId = :id")
    abstract suspend fun countSamplesForMeasurement(id: Long): Int
}
