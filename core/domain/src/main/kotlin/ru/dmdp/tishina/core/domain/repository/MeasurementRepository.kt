package ru.dmdp.tishina.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.NewMeasurement

/**
 * Persists user-saved measurements (FR-6) and exposes them to History (FR-8…11).
 *
 * Implemented by `MeasurementRepositoryImpl` in `:core:data` (Task 3); replaced
 * in unit tests by `FakeMeasurementRepository` from `:core:testing`.
 *
 * Implementation contract:
 * - [observeSummaries] is a hot, reactive list — emits a new value whenever the
 *   underlying database changes. UI subscribes through `StateFlow.stateIn(...)`.
 * - [save] is atomic: either the measurement and all its samples are written in
 *   a single transaction, or nothing is.
 * - [delete] cascades into the `samples` table via a Room foreign key.
 * - All suspend functions are safe to call from any dispatcher; implementations
 *   internally use `Dispatchers.IO`.
 */
interface MeasurementRepository {

    /** Reactive list of summaries, newest-first (descending `createdAtEpochMs`). */
    fun observeSummaries(): Flow<List<MeasurementSummary>>

    /** Loads the full Detail row, or `null` if [id] is not in the database. */
    suspend fun getById(id: Long): MeasurementDetails?

    /** Inserts [measurement] in a transaction and returns the new row id. */
    suspend fun save(measurement: NewMeasurement): Long

    /** Removes the measurement and (via FK CASCADE) its samples. Idempotent. */
    suspend fun delete(id: Long)

    /** Replaces the [note] column on the row with [id]. `null` clears the field. */
    suspend fun updateNote(id: Long, note: String?)
}
