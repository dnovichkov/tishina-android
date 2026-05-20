package ru.dmdp.tishina.core.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.di.IoDispatcher
import ru.dmdp.tishina.core.data.mapper.toDetails
import ru.dmdp.tishina.core.data.mapper.toDomain
import ru.dmdp.tishina.core.data.mapper.toEntity
import ru.dmdp.tishina.core.data.mapper.toSummary
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [MeasurementRepository] backed by Room.
 *
 * **Reactivity contract:** [observeSummaries] subscribes to the DAO's `Flow`, which
 * automatically re-emits whenever any row in `measurements` or `samples` changes (Room's
 * invalidation tracker). Each emission then enriches every row with its sparkline preview
 * — that secondary query is fan-outed via `coroutineScope { async { } }` to keep cold-start
 * latency low even when the History list has hundreds of rows. For MVP-scale data (≤ 1000
 * measurements) this stays well under the 16 ms frame budget.
 *
 * **Thread discipline:** every method that touches the DAO `withContext(ioDispatcher)` so
 * callers (ViewModels on Main) never block the UI. The `Flow` chain uses `flowOn` for the
 * same reason. The injected dispatcher is `Dispatchers.IO` in production (wired via
 * [IoDispatcher]) and a `TestDispatcher` in unit tests.
 *
 * **No try/catch:** Room exceptions propagate as is. The use-case layer wraps them in
 * `Result` where the UI cares (e.g. `SaveMeasurementUseCase`); plain `delete` /
 * `updateNote` are infallible from the caller's perspective — Room only throws on
 * programmer error (FK violation, CHECK constraint), which would indicate a bug elsewhere.
 */
@Singleton
class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MeasurementRepository {

    override fun observeSummaries(): Flow<List<MeasurementSummary>> = dao.observeSummaries()
        .map { rows ->
            coroutineScope {
                rows
                    .map { row -> async { row.toSummary(dao.loadSparklinePreview(row.id)) } }
                    .awaitAll()
            }
        }
        .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): MeasurementDetails? = withContext(ioDispatcher) {
        dao.getDetailsById(id)?.let { wrapper ->
            // Room's `@Relation` query has no ORDER BY clause, so SQLite is free to return
            // child rows in any order. Sort by tOffsetMs here so the Detail chart's
            // first/last-sample time-window calculation can rely on a chronological list.
            wrapper.measurement.toDetails(
                samples = wrapper.samples.sortedBy { it.tOffsetMs }.map { it.toDomain() },
                sparkline = dao.loadSparklinePreview(id),
            )
        }
    }

    override suspend fun save(measurement: NewMeasurement): Long = withContext(ioDispatcher) {
        val entity = measurement.toEntity()
        // measurementId on samples is overwritten by the DAO with the freshly minted id —
        // we pass 0 here for clarity (and so unit tests with mock DAOs don't get confused).
        val sampleEntities = measurement.samples.map { it.toEntity(measurementId = 0L) }
        dao.insertWithSamples(entity, sampleEntities)
    }

    override suspend fun delete(id: Long) {
        withContext(ioDispatcher) { dao.delete(id) }
    }

    override suspend fun updateNote(id: Long, note: String?) {
        withContext(ioDispatcher) { dao.updateNote(id, note) }
    }
}

/**
 * Internal extension to keep the `flowOn(ioDispatcher)` chain readable. Wraps the slim
 * [ru.dmdp.tishina.core.data.db.dao.MeasurementSummaryRow] projection into a domain
 * [MeasurementSummary] with the supplied sparkline.
 */
private fun ru.dmdp.tishina.core.data.db.dao.MeasurementSummaryRow.toSummary(
    sparkline: List<Float>,
): MeasurementSummary = MeasurementSummary(
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
