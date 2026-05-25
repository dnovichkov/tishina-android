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
     * Preview vector for the History card sparkline. Returns up to 20 evenly time-spaced
     * dB values spanning the full session: samples are grouped into 20 time-buckets across
     * `[0, MAX(tOffsetMs)]` and each bucket's mean is emitted. For a one-hour session that
     * gives one tick per three minutes, which is coarse but enough to convey "spiky vs
     * steady" at card scale. Sessions with ≤ 20 samples are returned verbatim (one bucket
     * per sample).
     *
     * Implementation note: avoids window functions (ROW_NUMBER OVER, COUNT OVER) because
     * `minSdk = 26` ships SQLite 3.18, which predates them. The arithmetic GROUP BY on a
     * scalar subquery is portable to SQLite 3.18+.
     */
    @Query(
        """
            SELECT AVG(db) AS db FROM samples
            WHERE measurementId = :id
            GROUP BY (tOffsetMs * 20 / COALESCE(
                (SELECT MAX(tOffsetMs) + 1 FROM samples WHERE measurementId = :id), 1
            ))
            ORDER BY MIN(tOffsetMs) ASC
        """,
    )
    abstract suspend fun loadSparklinePreview(id: Long): List<Float>

    @Query("DELETE FROM measurements WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /**
     * Bulk-delete every measurement whose id is in [ids]. Samples are removed by the
     * `FOREIGN KEY ... ON DELETE CASCADE` declaration on `SampleEntity`, so a single
     * round-trip removes both tables. Unknown ids are silently skipped; an empty
     * collection is a no-op (Room generates `IN (NULL)` which matches nothing).
     *
     * Powers History's bulk-delete flow (FR-12) via
     * [ru.dmdp.tishina.core.data.repository.MeasurementRepositoryImpl.deleteAll].
     */
    @Query("DELETE FROM measurements WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: Collection<Long>)

    @Query("UPDATE measurements SET note = :note WHERE id = :id")
    abstract suspend fun updateNote(id: Long, note: String?)

    /** Test-only helper for asserting cascade behavior. */
    @Query("SELECT COUNT(*) FROM samples WHERE measurementId = :id")
    abstract suspend fun countSamplesForMeasurement(id: Long): Int
}
